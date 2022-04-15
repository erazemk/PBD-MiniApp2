package si.uni_lj.fri.pbd.miniapp2

import android.app.*
import android.content.*
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class MediaPlayerService : Service() {

    companion object {
        const val TAG = "MediaPlayerService"

        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_STOP = "action_stop"
        const val ACTION_EXIT = "action_exit"

        const val DURATION_MSG_ID = 2
        const val DURATION_MSG_RATE = 1000L

        const val NOTIFICATION_ID = 3
        const val NOTIFICATION_CHANNEL_ID = "background_player"
    }

    private var serviceBound : Boolean = false
    private var accelerationService : AccelerationService? = null

    // Media info
    private var duration = 0
    private var songResId = 0
    private var currentPosition = 0

    var songProgress = 0
    var songTitleText = "Song title"
    var songDurationText = "00:00"

    private var foreground = false
    private var mediaPlayer : MediaPlayer? = null
    private var serviceBinder = MediaServiceBinder()

    private lateinit var notificationManager : NotificationManager
    private lateinit var notificationBuilder : NotificationCompat.Builder

    private lateinit var stopPendingIntent : PendingIntent
    private lateinit var exitPendingIntent : PendingIntent
    private lateinit var playPausePendingIntent : PendingIntent

    private lateinit var playPauseNotificationButtonText : String

    var isMediaPlaying = false
        private set

    inner class MediaServiceBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    // Handler to update duration info every second
    private val updateDurationHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (DURATION_MSG_ID == msg.what) {
                if (isMediaPlaying) updateMediaInfo()
                if (foreground) updateNotification()
                sendEmptyMessageDelayed(DURATION_MSG_ID, DURATION_MSG_RATE)
            }
        }
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) { serviceBound = false }
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            accelerationService = (service as AccelerationService.AccelerationServiceBinder).service
            serviceBound = true
            registerGestureReceiver()
        }
    }

    // Create a broadcast receiver for handling gestures
    // Source: https://stackoverflow.com/a/45399437
    private val gestureBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.getStringExtra("gesture")) {
                "horizontal" -> pausePlayer()
                "vertical" -> startPlayer()
            }
        }
    }

    private fun registerGestureReceiver() {
        LocalBroadcastManager.getInstance(this).registerReceiver(
            gestureBroadcastReceiver,
            IntentFilter(AccelerationService.ACTION_GESTURE)
        )
    }

    override fun onBind(intent: Intent): IBinder { return serviceBinder }

    override fun onCreate() {
        notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        isMediaPlaying = false
        playPauseNotificationButtonText = if (isMediaPlaying) "Pause" else "Play"

        createMediaPlayer()
        createNotificationChannel()
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(mConnection)
            serviceBound = false
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(gestureBroadcastReceiver)
        stopService(Intent(this, AccelerationService::class.java))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> if (isMediaPlaying) pausePlayer() else startPlayer()
            ACTION_STOP -> stopPlayer()
            ACTION_EXIT -> exitPlayer()
        }

        return START_STICKY
    }

    private fun createMediaPlayer() {
        // Pick a random song
        // Source: https://stackoverflow.com/a/63584710
        val fields : Array<Field> = R.raw::class.java.declaredFields
        val randomSongIndex = (Math.random() * fields.size).toInt()
        songResId = fields[randomSongIndex].getInt(randomSongIndex)
        mediaPlayer = MediaPlayer.create(this, songResId)

        // Get song title from the resource file
        // Source: https://stackoverflow.com/a/24452339
        val metadataRetriever = MediaMetadataRetriever()
        val uri = Uri.parse("android.resource://$packageName/$songResId")
        metadataRetriever.setDataSource(this, uri)

        val artist = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        songTitleText = String.format("%s - %s", artist, title)
    }

    // Properly release (stop/kill) MediaPlayer instance
    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Update current position and duration with data from MediaPlayerService
    private fun updateMediaInfo() {
        currentPosition = mediaPlayer?.currentPosition ?: 0
        duration = mediaPlayer?.duration ?: 0
        songProgress = ((currentPosition.toFloat() / duration.toFloat()) * 100).toInt()

        // Convert current position and duration to seconds and minutes
        // Source: https://stackoverflow.com/a/64740615
        val currentPositionMinutes : Long = TimeUnit.MILLISECONDS.toMinutes(currentPosition.toLong())
        val currentPositionSeconds : Long = TimeUnit.MILLISECONDS.toSeconds(currentPosition.toLong()) -
                TimeUnit.MINUTES.toSeconds(currentPositionMinutes)
        val durationMinutes : Long = TimeUnit.MILLISECONDS.toMinutes(duration.toLong())
        val durationSeconds : Long = TimeUnit.MILLISECONDS.toSeconds(duration.toLong()) -
                TimeUnit.MINUTES.toSeconds(durationMinutes)

        // Format them in MM:SS format
        val currentPositionFormat = String.format("%02d:%02d", currentPositionMinutes,
            currentPositionSeconds)
        val durationFormat = String.format("%02d:%02d", durationMinutes, durationSeconds)
        songDurationText = String.format("%s/%s", currentPositionFormat, durationFormat)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.notification_channel_description)
        channel.enableLights(true)
        channel.lightColor = Color.RED
        channel.enableVibration(true)
        val managerCompat = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        managerCompat.createNotificationChannel(channel)
    }

    // Create all the necessary intents (start is not necessary since the notification
    // won't exist if music isn't playing)
    private fun createNotificationActions() {
        val playPauseIntent = Intent(this, MediaPlayerService::class.java)
        playPauseIntent.action = ACTION_PLAY_PAUSE
        playPausePendingIntent = PendingIntent.getService(this, 0, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val stopIntent = Intent(this, MediaPlayerService::class.java)
        stopIntent.action = ACTION_STOP
        stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val exitIntent = Intent(this, MediaPlayerService::class.java)
        exitIntent.action = ACTION_EXIT
        exitPendingIntent = PendingIntent.getService(this, 0, exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createNotification() : Notification {
        createNotificationActions()
        playPauseNotificationButtonText = if (isMediaPlaying) "Pause" else "Play"

        // Source: https://stackoverflow.com/a/16435330
        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(songTitleText)
            .setContentText(songDurationText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, playPauseNotificationButtonText,
                playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Exit", exitPendingIntent)

        val resultIntent = Intent(this, MainActivity::class.java)
        val resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notificationBuilder.setContentIntent(resultPendingIntent)
        return notificationBuilder.build()
    }

    // Update the duration in the notification
    private fun updateNotification() {
        playPauseNotificationButtonText = if (isMediaPlaying) "Pause" else "Play"
        notificationBuilder.setContentText(songDurationText)
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // Start the player if not currently playing media
    fun startPlayer() {
        // Recreate the media player if needed
        if (mediaPlayer == null) CoroutineScope(Dispatchers.Default).launch { createMediaPlayer() }

        isMediaPlaying = true
        mediaPlayer?.start()
        if (foreground) updateNotification()
        updateDurationHandler.sendEmptyMessage(DURATION_MSG_ID)
    }

    // Pause the player if currently playing media
    fun pausePlayer() {
        if (!isMediaPlaying) return

        isMediaPlaying = false
        mediaPlayer?.pause()
        if (foreground) updateNotification()
        updateDurationHandler.sendEmptyMessage(DURATION_MSG_ID)
    }

    // Stop the player and remove notification if currently playing media
    fun stopPlayer() {
        if (isMediaPlaying) {
            isMediaPlaying = false
            mediaPlayer?.stop()
            stopForeground(true)
        }

        releaseMediaPlayer()
        updateDurationHandler.sendEmptyMessage(DURATION_MSG_ID)
    }

    // Stop the player, remove notification and properly exit app
    fun exitPlayer() {
        if (isMediaPlaying) {
            stopPlayer()
            isMediaPlaying = false
        }

        disableGestures()
        stopForeground(true)
        exitProcess(0)
    }

    // Start and bind AccelerationService
    fun enableGestures() {
        Toast.makeText(this, getString(R.string.gestures_toast_text, "enabled"),
            Toast.LENGTH_SHORT).show()

        val accelerationServiceIntent = Intent(this, AccelerationService::class.java)

        if (startService(accelerationServiceIntent) == null)
            Log.e(TAG, "Could not start AccelerationService")

        if (!bindService(accelerationServiceIntent, mConnection, 0))
            Log.e(TAG, "Could not bind to AccelerationService")
    }

    // Unbind and stop AccelerationService
    fun disableGestures() {
        Toast.makeText(this, getString(R.string.gestures_toast_text, "disabled"),
            Toast.LENGTH_SHORT).show()

        if (serviceBound) {
            unbindService(mConnection)
            serviceBound = false
        }

        // Unregister the gesture broadcast receiver and stop AccelerationService
        LocalBroadcastManager.getInstance(this).unregisterReceiver(gestureBroadcastReceiver)
        stopService(Intent(this, AccelerationService::class.java))
    }

    fun foreground() {
        foreground = true
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun background() {
        foreground = false
        stopForeground(true)
    }
}
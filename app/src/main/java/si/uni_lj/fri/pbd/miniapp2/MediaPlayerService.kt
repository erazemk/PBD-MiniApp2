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
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class MediaPlayerService : Service() {

    companion object {
        const val TAG = "MediaPlayerService"

        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
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
    var songTitleText = "Press play"
    var songDurationText = "00:00/00:00"

    private var mediaPlayer : MediaPlayer? = null
    private var serviceBinder = MediaServiceBinder()

    private lateinit var notificationManager : NotificationManager
    private lateinit var notificationBuilder : NotificationCompat.Builder

    private lateinit var playPendingIntent : PendingIntent
    private lateinit var pausePendingIntent : PendingIntent
    private lateinit var stopPendingIntent : PendingIntent
    private lateinit var exitPendingIntent : PendingIntent

    var isMediaPlaying = false
        private set

    inner class MediaServiceBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    // Handler to update duration info every second
    private val updateMediaInfoHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (DURATION_MSG_ID == msg.what && isMediaPlaying) {
                Log.d(TAG, "Updating media info")
                updateMediaInfo()
                updateNotification()
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
        Log.d(TAG, "Called onCreate")

        notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        isMediaPlaying = false

        createNotificationChannel()
        createNotificationIntents()
    }

    override fun onDestroy() {
        Log.d(TAG, "Called onDestroy")

        if (serviceBound) {
            unbindService(mConnection)
            serviceBound = false
        }

        // Release media player if needed
        if (mediaPlayer != null) releaseMediaPlayer()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(gestureBroadcastReceiver)
        stopService(Intent(this, AccelerationService::class.java))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Received intent from notification: ${intent.action}")
        when (intent.action) {
            ACTION_PLAY -> startPlayer()
            ACTION_PAUSE -> pausePlayer()
            ACTION_STOP -> stopPlayer()
            ACTION_EXIT -> exitPlayer()
        }

        return START_STICKY
    }

    private fun createMediaPlayer() {
        Log.i(TAG, "Creating media player")

        // Pick a random song
        // Source: https://stackoverflow.com/a/63584710
        val fields : Array<Field> = R.raw::class.java.declaredFields
        var randomSongIndex = (Math.random() * fields.size).toInt()

        // Don't choose same song twice
        while (fields[randomSongIndex].getInt(randomSongIndex) == songResId) {
            randomSongIndex = (Math.random() * fields.size).toInt()
        }

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

    private fun createNotificationIntents() {
        val notificationIntent = Intent(this, MediaPlayerService::class.java)

        notificationIntent.action = ACTION_PLAY
        playPendingIntent = PendingIntent.getService(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        notificationIntent.action = ACTION_PAUSE
        pausePendingIntent = PendingIntent.getService(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        notificationIntent.action = ACTION_STOP
        stopPendingIntent = PendingIntent.getService(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        notificationIntent.action = ACTION_EXIT
        exitPendingIntent = PendingIntent.getService(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createNotification() : Notification {
        Log.i(TAG, "Creating notification")

        // Source: https://stackoverflow.com/a/16435330
        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(songTitleText)
            .setContentText(songDurationText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Exit", exitPendingIntent)

        val resultIntent = Intent(this, MainActivity::class.java)
        val resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notificationBuilder.setContentIntent(resultPendingIntent)
        return notificationBuilder.build()
    }

    // Update media info in the notifications
    private fun updateNotification() {
        notificationBuilder.clearActions()

        // Rebuild actions based on media player state
        if (isMediaPlaying) {
            notificationBuilder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        } else {
            notificationBuilder.addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent)
        }

        notificationBuilder
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Exit", exitPendingIntent)
            .setContentTitle(songTitleText)
            .setContentText(songDurationText)

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // Start the player if not currently playing media
    fun startPlayer() {
        if (isMediaPlaying) return

        Log.i(TAG, "Starting player")

        if (mediaPlayer == null) createMediaPlayer()
        isMediaPlaying = true
        mediaPlayer?.start()

        // Create a notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Start updating media info
        updateMediaInfoHandler.sendEmptyMessage(DURATION_MSG_ID)

        // Autoplay next song on completion
        mediaPlayer?.setOnCompletionListener {
            releaseMediaPlayer()
            startPlayer()
        }
    }

    // Pause the player if currently playing media
    fun pausePlayer() {
        if (!isMediaPlaying) return

        Log.i(TAG, "Pausing player")

        isMediaPlaying = false
        mediaPlayer?.pause()
        updateNotification()
    }

    // Stop the player
    fun stopPlayer() {
        Log.i(TAG, "Stopping player")

        releaseMediaPlayer()

        // Set default media info values
        songProgress = 0
        songTitleText = "Press play"
        songDurationText = "00:00/00:00"

        updateNotification()
    }

    // Properly exit app
    fun exitPlayer() {
        Log.i(TAG, "Exiting player")

        stopPlayer()
        disableGestures()
        exitProcess(0)
    }

    // Properly stop and release MediaPlayer
    private fun releaseMediaPlayer() {
        Log.i(TAG, "Releasing player")

        mediaPlayer?.stop()
        mediaPlayer?.reset()
        mediaPlayer?.release()
        mediaPlayer = null

        isMediaPlaying = false

        Log.i(TAG, "Released player")
    }

    // Start and bind AccelerationService
    fun enableGestures() {
        Log.i(TAG, "Enabling gestures")

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
        Log.i(TAG, "Disabling gestures")

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
}
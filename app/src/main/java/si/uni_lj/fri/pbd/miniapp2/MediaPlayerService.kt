package si.uni_lj.fri.pbd.miniapp2

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.time.Duration

class MediaPlayerService : Service() {
    companion object {
        private val TAG = MediaPlayerService::class.simpleName
        const val ACTION_START = "start_player"
        const val ACTION_PAUSE = "pause_player"
        const val ACTION_STOP = "stop_player"
        const val ACTION_EXIT = "exit_player"

        private const val CHANNEL_ID = "background_player"
        const val NOTIFICATION_ID = 42
    }

    // Create a binder for the service
    inner class MediaServiceBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    private var serviceBinder = MediaServiceBinder()
    var mediaPlayer : MediaPlayer? = null

    var isMediaPlaying = false
        private set

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "Binding service")
        return serviceBinder
    }

    override fun onCreate() {
        Log.i(TAG, "Creating MediaPlayerService")

        isMediaPlaying = false
        createMediaPlayer()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting MediaPlayer service")

        when (intent.action) {
            ACTION_START -> startPlayer()
            ACTION_PAUSE -> pausePlayer()
            ACTION_STOP -> stopPlayer()
            ACTION_EXIT -> exitPlayer()
        }

        return START_STICKY
    }

    // Create a new MediaPlayer instance
    // Source: https://developer.android.com/guide/topics/media/mediaplayer
    private fun createMediaPlayer() {
        Log.i(TAG, "Creating a new MediaPlayer instance")
        mediaPlayer = MediaPlayer.create(this, R.raw.scarlet_fire)
    }

    // Properly release (stop/kill) MediaPlayer instance
    private fun releaseMediaPlayer() {
        Log.i(TAG, "Releasing MediaPlayer instance")
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // Create a notification channel
    private fun createNotificationChannel() {
        Log.i(TAG, "Creating a new notification channel")

        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.notification_channel_description)
        channel.enableLights(true)
        channel.lightColor = Color.RED
        channel.enableVibration(true)
        val managerCompat = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        managerCompat.createNotificationChannel(channel)
    }

    // Create a notification for when the app runs closed
    private fun createNotification() : Notification {
        Log.i(TAG, "Creating a new notification")

        // Create all the necessary intents
        val startIntent = Intent(this, MediaPlayerService::class.java)
        startIntent.action = ACTION_START
        val startPendingIntent = PendingIntent.getService(this, 0, startIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val pauseIntent = Intent(this, MediaPlayerService::class.java)
        pauseIntent.action = ACTION_PAUSE
        val pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val stopIntent = Intent(this, MediaPlayerService::class.java)
        stopIntent.action = ACTION_STOP
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val exitIntent = Intent(this, MediaPlayerService::class.java)
        exitIntent.action = ACTION_EXIT
        val exitPendingIntent = PendingIntent.getService(this, 0, exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setChannelId(CHANNEL_ID)
            .addAction(android.R.drawable.ic_media_play, "Play", startPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Exit", exitPendingIntent)

        val resultIntent = Intent(this, MainActivity::class.java)
        val resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(resultPendingIntent)
        return builder.build()
    }

    // Start the player if not currently playing media
    fun startPlayer() {
        if (isMediaPlaying) return

        Log.i(TAG, "Starting media player")

        // Recreate the media player if needed
        if (mediaPlayer == null) {
            createMediaPlayer()
        }

        isMediaPlaying = true
        mediaPlayer?.start()
    }

    // Pause the player if currently playing media
    fun pausePlayer() {
        if (!isMediaPlaying) return

        Log.i(TAG, "Pausing media player")

        isMediaPlaying = false
        mediaPlayer?.pause()
    }

    // Stop the player and remove notification if currently playing media
    fun stopPlayer() {
        if (isMediaPlaying) {
            Log.i(TAG, "Stopping media player and removing notification")

            isMediaPlaying = false
            mediaPlayer?.stop()
            stopForeground(true)
        }

        releaseMediaPlayer()
    }

    // Stop the player and remove notification if currently playing media
    fun exitPlayer() {
        if (isMediaPlaying) {
            Log.i(TAG, "Exiting media player and removing notification")

            stopPlayer()
            isMediaPlaying = false
            stopForeground(true)
        }

        // Kill AccelerationService
        //stopService(Intent(this, AccelerationService::class.java))
    }

    fun getCurrentPosition() : Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getDuration() : Int {
        return mediaPlayer?.duration ?: 0
    }

    fun foreground() {
        Log.i(TAG, "Going to foreground")
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun background() {
        Log.i(TAG, "Going to background")
        stopForeground(true)
    }
}
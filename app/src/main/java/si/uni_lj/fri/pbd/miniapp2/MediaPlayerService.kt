package si.uni_lj.fri.pbd.miniapp2

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MediaPlayerService : Service() {
    companion object {
        private val TAG = MediaPlayerService::class.simpleName
        const val ACTION_START = "start_service"
        const val ACTION_PAUSE = "pause_service"
        const val ACTION_STOP = "stop_service"
        const val ACTION_EXIT = "exit_app"

        private const val CHANNEL_ID = "background_player"
        const val NOTIFICATION_ID = 42
    }

    // Create a binder for the service
    inner class MediaServiceBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    private var serviceBinder = MediaServiceBinder()

    override fun onBind(intent: Intent?): IBinder {
        return serviceBinder
    }

    override fun onCreate() {
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
        }

        return START_STICKY
    }

    // Create a notification channel (source: Lab4)
    private fun createNotificationChannel() {
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

    fun startPlayer() {}
    fun pausePlayer() {}
    fun stopPlayer() {}

    fun foreground() {
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun background() {
        stopForeground(true)
    }
}
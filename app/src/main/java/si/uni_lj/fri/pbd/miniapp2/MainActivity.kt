package si.uni_lj.fri.pbd.miniapp2

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import si.uni_lj.fri.pbd.miniapp2.databinding.ActivityMainBinding

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG = MainActivity::class.simpleName
        private const val UPDATE_DURATION_MSG_ID = 16
        private const val UPDATE_RATE_MS = 1000L
    }

    var mediaPlayerService : MediaPlayerService? = null
    private var serviceBound : Boolean = false
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "Creating MainActivity")

        // Bind the view
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Set default values for UI elements
        binding.textTitle.text = getString(R.string.song_title_text, "Press play")
        binding.textDuration.text = getString(R.string.song_duration_text, "00:00/00:00")
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.max = 100
    }

    override fun onStart() {
        super.onStart()

        Log.i(TAG, "Starting and binding service")

        val mediaPlayerIntent = Intent(this, MediaPlayerService::class.java)

        if (startService(mediaPlayerIntent) == null) {
            Log.e(TAG, "Could not start MediaPlayerService")
        }

        if (!bindService(mediaPlayerIntent, mConnection, 0)) {
            Log.e(TAG, "Could not bind to MediaPlayerService")
        }
    }

    override fun onStop() {
        super.onStop()
        updateDurationHandler.sendEmptyMessage(UPDATE_DURATION_MSG_ID)

        // Unbind the service if bound
        if (serviceBound) {
            unbindService(mConnection)
            serviceBound = false

            // If media is playing, create a notification, otherwise stop the service
            if (mediaPlayerService?.isMediaPlaying == true) {
                mediaPlayerService?.foreground()
            } else {
                mediaPlayerService?.stopPlayer()
                stopService(Intent(this, MediaPlayerService::class.java))
            }
        }
    }

    // Handler to update the duration info every second, when the media is playing
    private val updateDurationHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (UPDATE_DURATION_MSG_ID == msg.what) {
                // Only update duration if needed
                if (mediaPlayerService?.isMediaPlaying == true) {
                    CoroutineScope(Main).launch {
                        updateDuration()
                    }
                }

                sendEmptyMessageDelayed(UPDATE_DURATION_MSG_ID, UPDATE_RATE_MS)
            }
        }
    }

    // Bind to MediaPlayerService
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(TAG, "Creating a new service connection")

            val binder = service as MediaPlayerService.MediaServiceBinder

            mediaPlayerService = binder.service
            if (mediaPlayerService == null) {
                Log.e(TAG, "Could not bind to service")
            }

            serviceBound = true
            mediaPlayerService?.background()

            // Update the duration if media was already playing
            if (mediaPlayerService?.isMediaPlaying == true) {
                Log.i(TAG, "Updating duration info after connection to service")

                CoroutineScope(Main).launch {
                    updateDuration()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            Log.i(TAG, "Disconnected from service")
        }
    }

    // Updates the duration in the media player screen
    private fun updateDuration() {
        Log.i(TAG, "ServiceBound = $serviceBound")
        if (serviceBound) {
            // Update text view and progress bar with the proper current position and duration
            binding.textDuration.text = mediaPlayerService?.songDurationText
            binding.progressBar.progress = mediaPlayerService?.progress ?: 0
        } else {
            Log.i(TAG, "Could not update duration, service not bound")
        }
    }

    // Start playing a media file if the service is bound and there is no media playing already
    fun playButtonOnClickListener(v: View) {
        Log.i(TAG, "Play button was pressed")

        if (serviceBound) {
            mediaPlayerService?.startPlayer()
            binding.textTitle.text = mediaPlayerService?.songTitleText
            updateDurationHandler.sendEmptyMessage(UPDATE_DURATION_MSG_ID)
            Log.i(TAG, "Started player")
        }
    }

    // Pause playing a media file if the service is bound and there is a media file playing
    fun pauseButtonOnClickListener(v: View) {
        Log.i(TAG, "Pause button was pressed")

        if (serviceBound && mediaPlayerService?.isMediaPlaying == true) {
            mediaPlayerService?.pausePlayer()
            updateDurationHandler.sendEmptyMessage(UPDATE_DURATION_MSG_ID)
            Log.i(TAG, "Paused player")
        } else {
            Log.i(TAG, "Not bound or not playing media")
        }
    }

    // Stop playing a media file if the service is bound and there is a media file playing
    fun stopButtonOnClickListener(v: View) {
        Log.i(TAG, "Stop button was pressed")

        if (serviceBound && mediaPlayerService?.isMediaPlaying == true) {
            mediaPlayerService?.stopPlayer()
            updateDurationHandler.sendEmptyMessage(UPDATE_DURATION_MSG_ID)
            Log.i(TAG, "Stopped player")
        } else {
            Log.i(TAG, "Not bound or not playing media")
        }
    }

    fun gestureOnButtonOnClickListener(v: View) {}
    fun gestureOffButtonOnClickListener(v: View) {}

    // Stop playing a media file and exit the app
    fun exitButtonOnClickListener(v: View) {
        Log.i(TAG, "Exit button was pressed")

        if (serviceBound) {
            if (mediaPlayerService?.isMediaPlaying == true) {
                mediaPlayerService?.exitPlayer()
            }

            // Only stop MediaPlayerService, it will stop AccelerationService if needed
            stopService(Intent(this, MediaPlayerService::class.java))
            Log.i(TAG, "Stopped MediaPlayerService")
        }

        // Exit the app
        Log.i(TAG, "Exiting the app")
        finishAndRemoveTask()
    }
}
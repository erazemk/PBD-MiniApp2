package si.uni_lj.fri.pbd.miniapp2

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import si.uni_lj.fri.pbd.miniapp2.databinding.ActivityMainBinding
import kotlin.system.exitProcess

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"

        const val DURATION_MSG_ID = 1
        const val DURATION_MSG_RATE = 1000L
    }

    private var serviceBound : Boolean = false
    private var gesturesEnabled : Boolean = false
    private var mediaPlayerService : MediaPlayerService? = null

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
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

        val mediaPlayerIntent = Intent(this, MediaPlayerService::class.java)

        if (startService(mediaPlayerIntent) == null)
            Log.e(TAG, "Could not start MediaPlayerService")

        if (!bindService(mediaPlayerIntent, mConnection, 0))
            Log.e(TAG, "Could not bind to MediaPlayerService")
    }

    override fun onStop() {
        super.onStop()

        updateDurationHandler.sendEmptyMessage(DURATION_MSG_ID)

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
            if (DURATION_MSG_ID == msg.what) {
                if (mediaPlayerService?.isMediaPlaying == true)
                    // Update duration info (including progress bar) in a coroutine
                    CoroutineScope(Dispatchers.Main).launch { updateDuration() }

                sendEmptyMessageDelayed(DURATION_MSG_ID, DURATION_MSG_RATE)
            }
        }
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) { serviceBound = false }
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mediaPlayerService = (service as MediaPlayerService.MediaServiceBinder).service
            serviceBound = true
            mediaPlayerService?.background()
        }
    }

    // Updates the duration in the media player screen
    private fun updateDuration() {
        if (serviceBound) {
            // Update text view and progress bar with the proper current position and duration
            binding.textDuration.text = mediaPlayerService?.songDurationText
            binding.progressBar.progress = mediaPlayerService?.songProgress ?: 0
        } else {
            Log.w(TAG, "Could not update duration, service not bound")
        }
    }

    // Start playing a media file if the service is bound and there is no media playing already
    fun playButtonOnClickListener(v: View) {
        if (serviceBound && mediaPlayerService?.isMediaPlaying == false) {
            mediaPlayerService?.startPlayer()
            binding.textTitle.text = mediaPlayerService?.songTitleText
            updateDurationHandler.sendEmptyMessage(DURATION_MSG_ID)
        } else {
            Log.w(TAG, "Not bound to MediaPlayerService or already playing media")
        }
    }

    // Pause playing a media file if the service is bound and there is a media file playing
    fun pauseButtonOnClickListener(v: View) {
        if (serviceBound && mediaPlayerService?.isMediaPlaying == true) {
            mediaPlayerService?.pausePlayer()
            updateDurationHandler.sendEmptyMessage(DURATION_MSG_ID)
        } else {
            Log.w(TAG, "Not bound to MediaPlayerService or not playing media")
        }
    }

    // Stop playing a media file if the service is bound and there is a media file playing
    fun stopButtonOnClickListener(v: View) {
        if (serviceBound) {
            mediaPlayerService?.stopPlayer()
            updateDurationHandler.sendEmptyMessage(DURATION_MSG_ID)

            // Reset media info
            binding.textTitle.text = getString(R.string.song_title_text, "Press play")
            binding.textDuration.text = getString(R.string.song_duration_text, "00:00/00:00")
            binding.progressBar.progress = 0
        } else {
            Log.w(TAG, "Not bound to MediaPlayerService or not playing media")
        }
    }

    // Enable gesture control
    fun gestureOnButtonOnClickListener(v: View) {
        if (!gesturesEnabled && serviceBound) {
            gesturesEnabled = true
            mediaPlayerService?.enableGestures()
        }
    }

    // Disable gesture control
    fun gestureOffButtonOnClickListener(v: View) {
        if (gesturesEnabled && serviceBound) {
            gesturesEnabled = false
            mediaPlayerService?.disableGestures()
        }
    }

    // Stop playing a media file and exit the app
    fun exitButtonOnClickListener(v: View) {
        if (serviceBound && mediaPlayerService?.isMediaPlaying == true) {
            mediaPlayerService?.exitPlayer()
            updateDurationHandler.sendEmptyMessage(DURATION_MSG_ID)
        }

        // Only stop MediaPlayerService, it will have stopped AccelerationService if needed
        stopService(Intent(this, MediaPlayerService::class.java))

        // Exit the app
        exitProcess(0)
    }
}
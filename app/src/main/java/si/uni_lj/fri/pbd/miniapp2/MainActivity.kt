package si.uni_lj.fri.pbd.miniapp2

import android.content.*
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import si.uni_lj.fri.pbd.miniapp2.databinding.ActivityMainBinding

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"

        const val MEDIAINFO_MSG_ID = 1
        const val MEDIAINFO_MSG_RATE = 1000L
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
        Log.d(TAG, "Called onStart")

        val mediaPlayerIntent = Intent(this, MediaPlayerService::class.java)

        if (startService(mediaPlayerIntent) == null)
            Log.e(TAG, "Could not start MediaPlayerService")

        if (!bindService(mediaPlayerIntent, mConnection, 0))
            Log.e(TAG, "Could not bind to MediaPlayerService")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "Called onStop")

        // Unbind the service if bound
        if (serviceBound) {
            unbindService(mConnection)
            serviceBound = false
        }
    }

    // Handler to update media info every second, when the media is playing
    private val updateMediaInfoHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (serviceBound && MEDIAINFO_MSG_ID == msg.what) {
                Log.d(TAG, "Called updateMediaInfoHandler")

                // Update duration info (including progress bar) in a coroutine
                CoroutineScope(Dispatchers.Main).launch { updateMediaInfo() }
                sendEmptyMessageDelayed(MEDIAINFO_MSG_ID, MEDIAINFO_MSG_RATE)
            }
        }
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "Disconnecting from MediaPlayerService")
            serviceBound = false
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(TAG, "Connecting to MediaPlayerService")

            mediaPlayerService = (service as MediaPlayerService.MediaServiceBinder).service
            serviceBound = true
            registerExitReceiver()
            updateMediaInfoHandler.sendEmptyMessage(MEDIAINFO_MSG_ID)
        }
    }

    // Create a broadcast receiver for properly exiting the app
    // Source: https://stackoverflow.com/a/45399437
    private val exitBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediaPlayerService.ACTION_EXIT) {
                Log.i(TAG, "Received exit broadcast message")

                // Only stop MediaPlayerService, it will have stopped AccelerationService if needed
                stopService(Intent(context, MediaPlayerService::class.java))

                // Exit the app
                finishAndRemoveTask()
            }
        }
    }

    private fun registerExitReceiver() {
        LocalBroadcastManager.getInstance(this).registerReceiver(
            exitBroadcastReceiver,
            IntentFilter(MediaPlayerService.ACTION_EXIT)
        )
    }

    private fun updateMediaInfo() {
        if (serviceBound) {
            Log.d(TAG, "Updating media info")

            // Update text view and progress bar with the proper current position and duration
            binding.textDuration.text = mediaPlayerService?.songDurationText
            binding.progressBar.progress = mediaPlayerService?.songProgress ?: 0

            // We have to also update the title for autoplay after completion to work,
            // even though this is really wasteful :(
            binding.textTitle.text = mediaPlayerService?.songTitleText
        } else {
            Log.w(TAG, "Could not update duration, service not bound")
        }
    }

    // Start playing media if the service is bound and there is no media playing already
    fun playButtonOnClickListener(v: View) {
        if (serviceBound) {
            mediaPlayerService?.startPlayer()
        } else {
            Log.w(TAG, "Not bound to MediaPlayerService")
        }
    }

    // Pause playing media if the service is bound and there is a media file playing
    fun pauseButtonOnClickListener(v: View) {
        if (serviceBound && mediaPlayerService?.isMediaPlaying == true) {
            mediaPlayerService?.pausePlayer()
        } else {
            Log.w(TAG, "Not bound to MediaPlayerService or not playing media")
        }
    }

    // Stop playing media if the service is bound
    fun stopButtonOnClickListener(v: View) {
        if (serviceBound) {
            mediaPlayerService?.stopPlayer()

            // Reset media info
            binding.textTitle.text = getString(R.string.song_title_text, "Press play")
            binding.textDuration.text = getString(R.string.song_duration_text, "00:00/00:00")
            binding.progressBar.progress = 0
        } else {
            Log.w(TAG, "Not bound to MediaPlayerService")
        }
    }

    // Stop playing media if needed and exit the app
    fun exitButtonOnClickListener(v: View) {
        if (serviceBound) {
            mediaPlayerService?.exitPlayer()
        } else {
            // Only stop MediaPlayerService, it will have stopped AccelerationService if needed
            stopService(Intent(this, MediaPlayerService::class.java))

            // Exit the app
            finishAndRemoveTask()
        }
    }

    // Enable gesture control
    fun gestureOnButtonOnClickListener(v: View) {
        if (!gesturesEnabled && serviceBound) {
            gesturesEnabled = true
            mediaPlayerService?.enableGestures()

            Toast.makeText(this, getString(R.string.gestures_toast_text, "enabled"),
                Toast.LENGTH_SHORT).show()
        }
    }

    // Disable gesture control
    fun gestureOffButtonOnClickListener(v: View) {
        if (gesturesEnabled && serviceBound) {
            gesturesEnabled = false
            mediaPlayerService?.disableGestures()

            Toast.makeText(this, getString(R.string.gestures_toast_text, "disabled"),
                Toast.LENGTH_SHORT).show()
        }
    }
}

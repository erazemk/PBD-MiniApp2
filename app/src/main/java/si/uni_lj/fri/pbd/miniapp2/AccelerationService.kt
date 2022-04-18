package si.uni_lj.fri.pbd.miniapp2

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

class AccelerationService : Service(), SensorEventListener {

    companion object {
        const val ACTION_GESTURE = "action_gesture"
    }

    private lateinit var sensor: Sensor
    private lateinit var sensorManager: SensorManager
    private var serviceBinder = AccelerationServiceBinder()

    private var vertical : Boolean = false
    private var horizontal : Boolean = false
    private var ignoredFirstGesture : Boolean = false

    private var lastGesture : String = ""

    private var t : Long = 0
    private var t1 : Long = 0
    private var t2 : Long = 0

    private var x : Float = 0F
    private var y : Float = 0F
    private var z : Float = 0F

    inner class AccelerationServiceBinder : Binder() {
        val service: AccelerationService
            get() = this@AccelerationService
    }

    override fun onCreate() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onDestroy() { sensorManager.unregisterListener(this) }

    override fun onBind(intent: Intent): IBinder { return serviceBinder }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* No need to do anything */ }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        CoroutineScope(Dispatchers.Default).launch { detectGesture(event.values) }
    }

    // Detect if device is being shaken and if needed send a broadcast to MediaPlayerService
    private fun detectGesture(values: FloatArray) {
        t1 = System.currentTimeMillis()

        // Only detect gesture every 500 ms
        if ((t1 - t2) < 500) return

        // Reset both values
        vertical = false
        horizontal = false

        // Calculate deltas
        var dX = abs(x - values[0])
        var dY = abs(y - values[1])
        var dZ = abs(z - values[2])

        // Store current values
        x = values[0]
        y = values[1]
        z = values[2]

        // Only count gesture if past threshold
        if (dX < 5) dX = 0F
        if (dY < 5) dY = 0F
        if (dZ < 5) dZ = 0F

        // If moving in X axis, count as horizontal change
        if (dX > dZ) {
            vertical = false
            horizontal = true
        }

        // If moving in Y axis, count as vertical change
        if (dY > dZ) {
            vertical = true
            horizontal = false
        }

        t2 = System.currentTimeMillis()

        // Send a broadcast to MediaPlayerService only if a gesture was detected
        if (horizontal || vertical) {

            // Ignore first gesture event, since it's triggered when starting the service
            if (!ignoredFirstGesture) {
                ignoredFirstGesture = true
                return
            }

            val gesture = if (horizontal) "horizontal" else "vertical"

            // If detected the same gesture in the last 2s, ignore it
            if (gesture == lastGesture && t2 - t < 2000) return

            lastGesture = gesture
            t = t2

            // Send the gesture to MediaPlayerService
            val gestureEvent = Intent(ACTION_GESTURE)
            gestureEvent.putExtra("gesture", gesture)
            LocalBroadcastManager.getInstance(this).sendBroadcast(gestureEvent)
        }
    }
}

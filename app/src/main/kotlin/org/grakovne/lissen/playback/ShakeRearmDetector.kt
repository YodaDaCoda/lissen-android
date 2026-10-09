package org.grakovne.lissen.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Re-arms the sleep timer on a phone shake, but only while the fade-out window is open: the
 * accelerometer listener is registered on [PlaybackEvent.TimerFadeStarted] and unregistered on
 * [PlaybackEvent.TimerExpired]/[PlaybackEvent.TimerCancelled] - never left running in the
 * background. That is a deliberate difference from a global "shake anytime to reset" gesture:
 * it can't misfire from a phone jostling in a pocket outside the few seconds the timer is about
 * to stop playback, and it costs nothing when no timer is running at all.
 */
@Singleton
class ShakeRearmDetector
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackEventBus: PlaybackEventBus,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val sensorManager by lazy { context.getSystemService(SensorManager::class.java) }
    private val accelerometer by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    private var lastShakeAtMillis = 0L

    private val sensorListener =
      object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
          val now = System.currentTimeMillis()
          val values = event.values
          if (values.size < 3) return

          if (!isShakeMagnitude(values[0], values[1], values[2])) return
          if (!debounceElapsed(now, lastShakeAtMillis)) return

          lastShakeAtMillis = now
          mediaRepository.rearmTimer(RearmTrigger.SHAKE)
        }

        override fun onAccuracyChanged(
          sensor: Sensor?,
          accuracy: Int,
        ) {
        }
      }

    override fun onCreate() {
      scope.launch {
        playbackEventBus.events.collect { event ->
          when (event) {
            is PlaybackEvent.TimerFadeStarted -> {
              register()
            }

            is PlaybackEvent.TimerExpired -> {
              unregister()
            }

            is PlaybackEvent.TimerCancelled -> {
              unregister()
            }

            else -> {}
          }
        }
      }
    }

    private fun register() {
      val sensor = accelerometer
      if (sensor == null) {
        Timber.w("No accelerometer available, shake re-arm disabled")
        return
      }

      sensorManager?.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregister() {
      sensorManager?.unregisterListener(sensorListener)
    }
  }

/** A standard gForce-over-gravity shake heuristic: true once acceleration exceeds [threshold]g. */
internal fun isShakeMagnitude(
  x: Float,
  y: Float,
  z: Float,
  threshold: Float = SHAKE_G_FORCE_THRESHOLD,
): Boolean {
  val gX = x / SensorManager.GRAVITY_EARTH
  val gY = y / SensorManager.GRAVITY_EARTH
  val gZ = z / SensorManager.GRAVITY_EARTH
  val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
  return gForce > threshold
}

internal fun debounceElapsed(
  now: Long,
  lastTriggerMillis: Long,
  minIntervalMillis: Long = MIN_SHAKE_INTERVAL_MILLIS,
): Boolean = now - lastTriggerMillis >= minIntervalMillis

internal const val SHAKE_G_FORCE_THRESHOLD = 2.7f
internal const val MIN_SHAKE_INTERVAL_MILLIS = 1_000L

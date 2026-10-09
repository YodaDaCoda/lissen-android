package org.grakovne.lissen.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.R
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the two short sleep-timer chimes - a lower one for the fade-out warning, a higher one
 * for a re-arm confirmation - on the media audio stream, so they come out of the same output as
 * the audiobook, including Bluetooth. Each has its own configurable volume.
 */
@Singleton
class SleepTimerChimePlayer
  @Inject
  constructor(
    @ApplicationContext context: Context,
  ) {
    private val soundPool =
      SoundPool
        .Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
          AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
        ).build()

    private val loadedSoundIds = mutableSetOf<Int>()
    private val fadeSoundId: Int
    private val rearmSoundId: Int

    init {
      soundPool.setOnLoadCompleteListener { _, sampleId, status ->
        if (status == 0) {
          loadedSoundIds += sampleId
        } else {
          Timber.w("Sleep timer chime $sampleId failed to load, status=$status")
        }
      }
      fadeSoundId = soundPool.load(context, R.raw.sleep_timer_chime_fade, 1)
      rearmSoundId = soundPool.load(context, R.raw.sleep_timer_chime_rearm, 1)
    }

    fun playFadeStart(volume: Float) = play(fadeSoundId, volume)

    fun playRearm(volume: Float) = play(rearmSoundId, volume)

    private fun play(
      soundId: Int,
      volume: Float,
    ) {
      if (soundId !in loadedSoundIds) {
        Timber.d("Sleep timer chime $soundId not loaded yet, skipping")
        return
      }
      val clamped = volume.coerceIn(0f, 1f)
      soundPool.play(soundId, clamped, clamped, 1, 0, 1f)
    }
  }

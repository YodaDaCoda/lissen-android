package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.domain.RewindOnPauseSettings
import org.grakovne.lissen.domain.RearmExtensionMode
import org.grakovne.lissen.domain.ResumeRewindLongMode
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.SleepTimerSettings
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.EqualizerBandProvider
import org.grakovne.lissen.playback.EqualizerCapabilities
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlaybackSettingsViewModel
  @Inject
  constructor(
    private val playback: PlaybackPreferences,
    private val equalizerBandProvider: EqualizerBandProvider,
  ) : ViewModel() {
    private val _preferredPlaybackVolumeBoost = MutableStateFlow(playback.getPlaybackVolumeBoost())
    val preferredPlaybackVolumeBoost: StateFlow<Int> = _preferredPlaybackVolumeBoost.asStateFlow()

    private val _equalizer = MutableStateFlow(playback.getEqualizer())
    val equalizer: StateFlow<EqualizerSettings> = _equalizer.asStateFlow()

    val equalizerCapabilities: StateFlow<EqualizerCapabilities?> =
      flow { emit(equalizerBandProvider.getCapabilities()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _seekTime = MutableStateFlow(playback.getSeekTime())
    val seekTime: StateFlow<SeekTime> = _seekTime.asStateFlow()

    private val _rewindOnPause = MutableStateFlow(playback.getRewindOnPause().secondsOrNull())
    val rewindOnPause: StateFlow<Int?> = _rewindOnPause.asStateFlow()

    private val _defaultTimerOption = MutableStateFlow<TimerOption?>(playback.getDefaultTimerOption())
    val defaultTimerOption: StateFlow<TimerOption?> = _defaultTimerOption.asStateFlow()

    private val _sleepTimerFade = MutableStateFlow(playback.getSleepTimerSettings().fadeSecondsOrNull())
    val sleepTimerFade: StateFlow<Int?> = _sleepTimerFade.asStateFlow()

    private val _sleepTimerChimeOnFadeStart = MutableStateFlow(playback.getSleepTimerSettings().chimeOnFadeStart)
    val sleepTimerChimeOnFadeStart: StateFlow<Boolean> = _sleepTimerChimeOnFadeStart.asStateFlow()

    private val _sleepTimerRearmEnabled = MutableStateFlow(playback.getSleepTimerSettings().rearmEnabled)
    val sleepTimerRearmEnabled: StateFlow<Boolean> = _sleepTimerRearmEnabled.asStateFlow()

    private val _sleepTimerChimeOnRearm = MutableStateFlow(playback.getSleepTimerSettings().chimeOnRearm)
    val sleepTimerChimeOnRearm: StateFlow<Boolean> = _sleepTimerChimeOnRearm.asStateFlow()

    private val _sleepTimerRearmViaHeadphoneButton =
      MutableStateFlow(playback.getSleepTimerSettings().rearmViaHeadphoneButton)
    val sleepTimerRearmViaHeadphoneButton: StateFlow<Boolean> = _sleepTimerRearmViaHeadphoneButton.asStateFlow()

    private val _sleepTimerRearmViaShake = MutableStateFlow(playback.getSleepTimerSettings().rearmViaShake)
    val sleepTimerRearmViaShake: StateFlow<Boolean> = _sleepTimerRearmViaShake.asStateFlow()

    private val _sleepTimerRearmExtensionMode =
      MutableStateFlow(playback.getSleepTimerSettings().rearmExtensionMode)
    val sleepTimerRearmExtensionMode: StateFlow<RearmExtensionMode> = _sleepTimerRearmExtensionMode.asStateFlow()

    private val _sleepTimerRearmExtensionSeconds =
      MutableStateFlow(playback.getSleepTimerSettings().rearmExtensionSeconds)
    val sleepTimerRearmExtensionSeconds: StateFlow<Int> = _sleepTimerRearmExtensionSeconds.asStateFlow()

    private val _sleepTimerResumeRewindThresholdSeconds =
      MutableStateFlow(playback.getSleepTimerSettings().resumeRewindThresholdSeconds)
    val sleepTimerResumeRewindThresholdSeconds: StateFlow<Int> = _sleepTimerResumeRewindThresholdSeconds.asStateFlow()

    private val _sleepTimerResumeRewindShortSeconds =
      MutableStateFlow(playback.getSleepTimerSettings().resumeRewindShortSeconds)
    val sleepTimerResumeRewindShortSeconds: StateFlow<Int> = _sleepTimerResumeRewindShortSeconds.asStateFlow()

    private val _sleepTimerResumeRewindLongMode =
      MutableStateFlow(playback.getSleepTimerSettings().resumeRewindLongMode)
    val sleepTimerResumeRewindLongMode: StateFlow<ResumeRewindLongMode> = _sleepTimerResumeRewindLongMode.asStateFlow()

    private val _sleepTimerResumeRewindLongSeconds =
      MutableStateFlow(playback.getSleepTimerSettings().resumeRewindLongSeconds)
    val sleepTimerResumeRewindLongSeconds: StateFlow<Int> = _sleepTimerResumeRewindLongSeconds.asStateFlow()

    private val _sleepTimerChimeFadeVolume = MutableStateFlow(playback.getSleepTimerSettings().chimeFadeVolume)
    val sleepTimerChimeFadeVolume: StateFlow<Int> = _sleepTimerChimeFadeVolume.asStateFlow()

    private val _sleepTimerChimeRearmVolume = MutableStateFlow(playback.getSleepTimerSettings().chimeRearmVolume)
    val sleepTimerChimeRearmVolume: StateFlow<Int> = _sleepTimerChimeRearmVolume.asStateFlow()

    private val _sleepTimerDefaultScheduleEnabled =
      MutableStateFlow(playback.getSleepTimerSettings().defaultTimerScheduleEnabled)
    val sleepTimerDefaultScheduleEnabled: StateFlow<Boolean> = _sleepTimerDefaultScheduleEnabled.asStateFlow()

    private val _sleepTimerDefaultScheduleStartMinute =
      MutableStateFlow(playback.getSleepTimerSettings().defaultTimerScheduleStartMinute)
    val sleepTimerDefaultScheduleStartMinute: StateFlow<Int> = _sleepTimerDefaultScheduleStartMinute.asStateFlow()

    private val _sleepTimerDefaultScheduleEndMinute =
      MutableStateFlow(playback.getSleepTimerSettings().defaultTimerScheduleEndMinute)
    val sleepTimerDefaultScheduleEndMinute: StateFlow<Int> = _sleepTimerDefaultScheduleEndMinute.asStateFlow()

    private val _softwareCodecsEnabled = MutableStateFlow(playback.getSoftwareCodecsEnabled())
    val softwareCodecsEnabled: StateFlow<Boolean> = _softwareCodecsEnabled.asStateFlow()
    val softwareCodecsEnabledOnStart: Boolean = playback.getSoftwareCodecsEnabled()

    private val _audioFocusLossPolicy = MutableStateFlow(playback.getAudioFocusLossPolicy())
    val audioFocusLossPolicy: StateFlow<AudioFocusLossPolicy> = _audioFocusLossPolicy.asStateFlow()

    fun preferPlaybackVolumeBoost(db: Int) {
      Timber.d("User action: preferPlaybackVolumeBoost $db dB")
      _preferredPlaybackVolumeBoost.value = db
      playback.savePlaybackVolumeBoost(db)
    }

    fun preferEqualizerGain(
      band: Int,
      db: Int,
    ) {
      Timber.d("User action: preferEqualizerGain band=$band $db dB")
      val current = _equalizer.value
      val size = maxOf(current.gains.size, band + 1)
      val gains = List(size) { index -> if (index == band) db else current.gains.getOrElse(index) { 0 } }

      saveEqualizer(current.copy(gains = gains))
    }

    fun resetEqualizer() {
      Timber.d("User action: resetEqualizer")
      saveEqualizer(_equalizer.value.copy(gains = emptyList()))
    }

    private fun saveEqualizer(settings: EqualizerSettings) {
      _equalizer.value = settings
      playback.saveEqualizer(settings)
    }

    fun preferForward(seconds: Int) {
      Timber.d("User action: preferForward $seconds")
      saveSeekTime(_seekTime.value.copy(forward = seconds))
    }

    fun preferRewind(seconds: Int) {
      Timber.d("User action: preferRewind $seconds")
      saveSeekTime(_seekTime.value.copy(rewind = seconds))
    }

    private fun saveSeekTime(seekTime: SeekTime) {
      playback.saveSeekTime(seekTime)
      _seekTime.value = seekTime
    }

    fun preferRewindOnPause(seconds: Int?) {
      Timber.d("User action: preferRewindOnPause $seconds")
      _rewindOnPause.value = seconds

      val current = playback.getRewindOnPause()
      playback.saveRewindOnPause(current.copy(enabled = seconds != null, seconds = seconds ?: current.seconds))
    }

    fun saveDefaultTimerOption(option: TimerOption?) {
      Timber.d("User action: saveDefaultTimerOption option=$option")
      _defaultTimerOption.value = option
      playback.saveDefaultTimerOption(option)
    }

    fun preferSleepTimerFade(seconds: Int?) {
      Timber.d("User action: preferSleepTimerFade $seconds")
      _sleepTimerFade.value = seconds

      val current = playback.getSleepTimerSettings()
      playback.saveSleepTimerSettings(current.copy(fadeEnabled = seconds != null, fadeSeconds = seconds ?: current.fadeSeconds))
    }

    fun preferSleepTimerChimeOnFadeStart(value: Boolean) {
      Timber.d("User action: preferSleepTimerChimeOnFadeStart $value")
      _sleepTimerChimeOnFadeStart.value = value
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(chimeOnFadeStart = value))
    }

    fun preferSleepTimerRearmEnabled(value: Boolean) {
      Timber.d("User action: preferSleepTimerRearmEnabled $value")
      _sleepTimerRearmEnabled.value = value
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(rearmEnabled = value))
    }

    fun preferSleepTimerChimeOnRearm(value: Boolean) {
      Timber.d("User action: preferSleepTimerChimeOnRearm $value")
      _sleepTimerChimeOnRearm.value = value
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(chimeOnRearm = value))
    }

    fun preferSleepTimerRearmViaHeadphoneButton(value: Boolean) {
      Timber.d("User action: preferSleepTimerRearmViaHeadphoneButton $value")
      _sleepTimerRearmViaHeadphoneButton.value = value
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(rearmViaHeadphoneButton = value))
    }

    fun preferSleepTimerRearmViaShake(value: Boolean) {
      Timber.d("User action: preferSleepTimerRearmViaShake $value")
      _sleepTimerRearmViaShake.value = value
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(rearmViaShake = value))
    }

    fun preferSleepTimerRearmExtensionMode(mode: RearmExtensionMode) {
      Timber.d("User action: preferSleepTimerRearmExtensionMode $mode")
      _sleepTimerRearmExtensionMode.value = mode
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(rearmExtensionMode = mode))
    }

    fun preferSleepTimerRearmExtensionSeconds(seconds: Int) {
      Timber.d("User action: preferSleepTimerRearmExtensionSeconds $seconds")
      _sleepTimerRearmExtensionSeconds.value = seconds
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(rearmExtensionSeconds = seconds))
    }

    fun preferSleepTimerResumeRewindThresholdSeconds(seconds: Int) {
      Timber.d("User action: preferSleepTimerResumeRewindThresholdSeconds $seconds")
      _sleepTimerResumeRewindThresholdSeconds.value = seconds
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(resumeRewindThresholdSeconds = seconds))
    }

    fun preferSleepTimerResumeRewindShortSeconds(seconds: Int) {
      Timber.d("User action: preferSleepTimerResumeRewindShortSeconds $seconds")
      _sleepTimerResumeRewindShortSeconds.value = seconds
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(resumeRewindShortSeconds = seconds))
    }

    fun preferSleepTimerResumeRewindLongMode(mode: ResumeRewindLongMode) {
      Timber.d("User action: preferSleepTimerResumeRewindLongMode $mode")
      _sleepTimerResumeRewindLongMode.value = mode
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(resumeRewindLongMode = mode))
    }

    fun preferSleepTimerResumeRewindLongSeconds(seconds: Int) {
      Timber.d("User action: preferSleepTimerResumeRewindLongSeconds $seconds")
      _sleepTimerResumeRewindLongSeconds.value = seconds
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(resumeRewindLongSeconds = seconds))
    }

    fun preferSleepTimerChimeFadeVolume(volume: Int) {
      Timber.d("User action: preferSleepTimerChimeFadeVolume $volume")
      _sleepTimerChimeFadeVolume.value = volume
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(chimeFadeVolume = volume))
    }

    fun preferSleepTimerChimeRearmVolume(volume: Int) {
      Timber.d("User action: preferSleepTimerChimeRearmVolume $volume")
      _sleepTimerChimeRearmVolume.value = volume
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(chimeRearmVolume = volume))
    }

    fun preferSleepTimerDefaultScheduleEnabled(value: Boolean) {
      Timber.d("User action: preferSleepTimerDefaultScheduleEnabled $value")
      _sleepTimerDefaultScheduleEnabled.value = value
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(defaultTimerScheduleEnabled = value))
    }

    fun preferSleepTimerDefaultScheduleStartMinute(minuteOfDay: Int) {
      Timber.d("User action: preferSleepTimerDefaultScheduleStartMinute $minuteOfDay")
      _sleepTimerDefaultScheduleStartMinute.value = minuteOfDay
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(defaultTimerScheduleStartMinute = minuteOfDay))
    }

    fun preferSleepTimerDefaultScheduleEndMinute(minuteOfDay: Int) {
      Timber.d("User action: preferSleepTimerDefaultScheduleEndMinute $minuteOfDay")
      _sleepTimerDefaultScheduleEndMinute.value = minuteOfDay
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(defaultTimerScheduleEndMinute = minuteOfDay))
    }

    fun preferSoftwareCodecsEnabled(value: Boolean) {
      Timber.d("User action: preferSoftwareCodecsEnabled $value")
      _softwareCodecsEnabled.value = value
      playback.saveSoftwareCodecsEnabled(value)
    }

    fun preferAudioFocusLossPolicy(policy: AudioFocusLossPolicy) {
      Timber.d("User action: preferAudioFocusLossPolicy $policy")
      _audioFocusLossPolicy.value = policy
      playback.saveAudioFocusLossPolicy(policy)
    }
  }

private fun RewindOnPauseSettings.secondsOrNull(): Int? = seconds.takeIf { enabled }

private fun SleepTimerSettings.fadeSecondsOrNull(): Int? = fadeSeconds.takeIf { fadeEnabled }

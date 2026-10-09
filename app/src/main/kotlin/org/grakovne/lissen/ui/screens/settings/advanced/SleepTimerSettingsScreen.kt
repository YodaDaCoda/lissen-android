package org.grakovne.lissen.ui.screens.settings.advanced

import android.content.Context
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RearmExtensionMode
import org.grakovne.lissen.domain.ResumeRewindLongMode
import org.grakovne.lissen.domain.SleepTimerSettings
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.slider.CommonSlider
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.ui.screens.settings.composable.DefaultTimerSettingsComposable
import org.grakovne.lissen.ui.screens.settings.composable.DisableableTimeBottomSheet
import org.grakovne.lissen.ui.screens.settings.composable.SettingsSectionHeaderComposable
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.ui.screens.settings.composable.SettingsTopAppBar
import org.grakovne.lissen.viewmodel.LibrarySettingsViewModel
import org.grakovne.lissen.viewmodel.PlaybackSettingsViewModel
import kotlin.math.roundToInt


@Composable
fun SleepTimerSettingsScreen(onBack: () -> Unit) {
  val viewModel: PlaybackSettingsViewModel = hiltViewModel()
  val librarySettingsViewModel: LibrarySettingsViewModel = hiltViewModel()
  val libraryType by librarySettingsViewModel.preferredLibraryType.collectAsState()

  SleepTimerSettingsScreenContent(
    viewModel = viewModel,
    libraryType = libraryType,
    onBack = onBack,
  )
}

@Composable
internal fun SleepTimerSettingsScreenContent(
  viewModel: PlaybackSettingsViewModel,
  libraryType: LibraryType,
  onBack: () -> Unit,
) {
  val fade by viewModel.sleepTimerFade.collectAsState()
  val fadeEnabled = fade != null
  val context = LocalContext.current
  val chimeOnFadeStart by viewModel.sleepTimerChimeOnFadeStart.collectAsState()
  val rearmEnabled by viewModel.sleepTimerRearmEnabled.collectAsState()
  val chimeOnRearm by viewModel.sleepTimerChimeOnRearm.collectAsState()
  val rearmViaHeadphoneButton by viewModel.sleepTimerRearmViaHeadphoneButton.collectAsState()
  val rearmViaShake by viewModel.sleepTimerRearmViaShake.collectAsState()
  val rearmExtensionMode by viewModel.sleepTimerRearmExtensionMode.collectAsState()
  val rearmExtensionSeconds by viewModel.sleepTimerRearmExtensionSeconds.collectAsState()
  val resumeRewindThresholdSeconds by viewModel.sleepTimerResumeRewindThresholdSeconds.collectAsState()
  val resumeRewindShortSeconds by viewModel.sleepTimerResumeRewindShortSeconds.collectAsState()
  val resumeRewindLongMode by viewModel.sleepTimerResumeRewindLongMode.collectAsState()
  val resumeRewindLongSeconds by viewModel.sleepTimerResumeRewindLongSeconds.collectAsState()
  val chimeFadeVolume by viewModel.sleepTimerChimeFadeVolume.collectAsState()
  val chimeRearmVolume by viewModel.sleepTimerChimeRearmVolume.collectAsState()

  val rearmControlsEnabled = fadeEnabled && rearmEnabled

  var fadeExpanded by remember { mutableStateOf(false) }
  var extensionExpanded by remember { mutableStateOf(false) }
  var thresholdExpanded by remember { mutableStateOf(false) }
  var shortRewindExpanded by remember { mutableStateOf(false) }
  var longRewindExpanded by remember { mutableStateOf(false) }
  var fadeChimeVolumeExpanded by remember { mutableStateOf(false) }
  var rearmChimeVolumeExpanded by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      SettingsTopAppBar(
        title = stringResource(R.string.sleep_timer_settings_title),
        onBack = onBack,
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxHeight(),
    content = { innerPadding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        SettingsSectionHeaderComposable(title = stringResource(R.string.sleep_timer_section_default), topPadding = 4.dp)

        DefaultTimerSettingsComposable(viewModel, libraryType)

        SettingsSectionHeaderComposable(title = stringResource(R.string.sleep_timer_section_fade))

        AdvancedSettingsSimpleItemComposable(
          title = stringResource(R.string.sleep_timer_fade_title),
          description =
            fade
              ?.let { context.resources.getQuantityString(R.plurals.fade_duration_seconds, it, it) }
              ?: stringResource(R.string.sleep_timer_fade_disabled),
          onclick = { fadeExpanded = true },
        )

        SettingsToggleItem(
          title = stringResource(R.string.sleep_timer_chime_title),
          description = stringResource(R.string.sleep_timer_chime_description),
          initialState = chimeOnFadeStart,
          enabled = fadeEnabled,
        ) { viewModel.preferSleepTimerChimeOnFadeStart(it) }

        ChimeVolumeRowComposable(
          title = stringResource(R.string.sleep_timer_chime_fade_volume_title),
          percent = chimeFadeVolume,
          enabled = fadeEnabled && chimeOnFadeStart,
          onClicked = { fadeChimeVolumeExpanded = true },
        )

        SettingsSectionHeaderComposable(title = stringResource(R.string.sleep_timer_section_rearm))

        SettingsToggleItem(
          title = stringResource(R.string.sleep_timer_rearm_title),
          description = stringResource(R.string.sleep_timer_rearm_description),
          initialState = rearmEnabled,
          enabled = fadeEnabled,
        ) { viewModel.preferSleepTimerRearmEnabled(it) }

        SettingsToggleItem(
          title = stringResource(R.string.sleep_timer_rearm_headphone_title),
          description = stringResource(R.string.sleep_timer_rearm_headphone_description),
          initialState = rearmViaHeadphoneButton,
          enabled = rearmControlsEnabled,
        ) { viewModel.preferSleepTimerRearmViaHeadphoneButton(it) }

        SettingsToggleItem(
          title = stringResource(R.string.sleep_timer_rearm_shake_title),
          description = stringResource(R.string.sleep_timer_rearm_shake_description),
          initialState = rearmViaShake,
          enabled = rearmControlsEnabled,
        ) { viewModel.preferSleepTimerRearmViaShake(it) }

        SettingsToggleItem(
          title = stringResource(R.string.sleep_timer_chime_on_rearm_title),
          description = stringResource(R.string.sleep_timer_chime_on_rearm_description),
          initialState = chimeOnRearm,
          enabled = rearmControlsEnabled,
        ) { viewModel.preferSleepTimerChimeOnRearm(it) }

        ChimeVolumeRowComposable(
          title = stringResource(R.string.sleep_timer_chime_rearm_volume_title),
          percent = chimeRearmVolume,
          enabled = rearmControlsEnabled && chimeOnRearm,
          onClicked = { rearmChimeVolumeExpanded = true },
        )

        RearmExtensionModeComposable(viewModel = viewModel, enabled = rearmControlsEnabled)

        if (rearmExtensionMode == RearmExtensionMode.FIXED) {
          RearmExtensionRowComposable(
            seconds = rearmExtensionSeconds,
            enabled = rearmControlsEnabled,
            onClicked = { extensionExpanded = true },
          )
        }

        SettingsSectionHeaderComposable(title = stringResource(R.string.sleep_timer_section_resume_rewind))

        ResumeRewindThresholdRowComposable(
          seconds = resumeRewindThresholdSeconds,
          onClicked = { thresholdExpanded = true },
        )

        ResumeRewindShortRowComposable(
          seconds = resumeRewindShortSeconds,
          onClicked = { shortRewindExpanded = true },
        )

        ResumeRewindLongModeComposable(viewModel = viewModel)

        if (resumeRewindLongMode == ResumeRewindLongMode.FIXED) {
          ResumeRewindLongRowComposable(
            seconds = resumeRewindLongSeconds,
            onClicked = { longRewindExpanded = true },
          )
        }
      }
    },
  )

  if (fadeExpanded) {
    DisableableTimeBottomSheet(
      title = stringResource(R.string.sleep_timer_fade_title),
      seconds = fade,
      maxSeconds = SleepTimerSettings.MAX_FADE_SECONDS,
      presets = fadeTimePresets,
      secondsLabel = R.plurals.fade_duration_seconds,
      offLabel = R.string.sleep_timer_fade_disabled,
      onDismissRequest = { fadeExpanded = false },
      onUpdate = { viewModel.preferSleepTimerFade(it) },
    )
  }

  if (extensionExpanded) {
    RearmExtensionBottomSheet(
      currentSeconds = rearmExtensionSeconds,
      onDismissRequest = { extensionExpanded = false },
      onUpdate = { viewModel.preferSleepTimerRearmExtensionSeconds(it) },
    )
  }

  if (thresholdExpanded) {
    ResumeRewindThresholdBottomSheet(
      currentSeconds = resumeRewindThresholdSeconds,
      onDismissRequest = { thresholdExpanded = false },
      onUpdate = { viewModel.preferSleepTimerResumeRewindThresholdSeconds(it) },
    )
  }

  if (shortRewindExpanded) {
    ResumeRewindShortBottomSheet(
      currentSeconds = resumeRewindShortSeconds,
      onDismissRequest = { shortRewindExpanded = false },
      onUpdate = { viewModel.preferSleepTimerResumeRewindShortSeconds(it) },
    )
  }

  if (longRewindExpanded) {
    ResumeRewindLongBottomSheet(
      currentSeconds = resumeRewindLongSeconds,
      onDismissRequest = { longRewindExpanded = false },
      onUpdate = { viewModel.preferSleepTimerResumeRewindLongSeconds(it) },
    )
  }

  if (fadeChimeVolumeExpanded) {
    ChimeVolumeBottomSheet(
      title = stringResource(R.string.sleep_timer_chime_fade_volume_title),
      currentPercent = chimeFadeVolume,
      onDismissRequest = { fadeChimeVolumeExpanded = false },
      onUpdate = { viewModel.preferSleepTimerChimeFadeVolume(it) },
    )
  }

  if (rearmChimeVolumeExpanded) {
    ChimeVolumeBottomSheet(
      title = stringResource(R.string.sleep_timer_chime_rearm_volume_title),
      currentPercent = chimeRearmVolume,
      onDismissRequest = { rearmChimeVolumeExpanded = false },
      onUpdate = { viewModel.preferSleepTimerChimeRearmVolume(it) },
    )
  }
}

private val fadeTimePresets = listOf(10, 15, 30, 60)

@Composable
private fun RearmExtensionModeComposable(
  viewModel: PlaybackSettingsViewModel,
  enabled: Boolean,
) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }
  val mode by viewModel.sleepTimerRearmExtensionMode.collectAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .let {
          when (enabled) {
            true -> it.clickable { expanded = true }
            false -> it
          }
        }.padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sleep_timer_rearm_extension_mode_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color =
          when (enabled) {
            true -> colorScheme.onBackground
            false -> colorScheme.onBackground.copy(alpha = 0.4f)
          },
      )
      Text(
        text = mode.toItem(context).name,
        style = typography.bodyMedium,
        color =
          when (enabled) {
            true -> colorScheme.onSurfaceVariant
            false -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          },
      )
    }
  }

  if (expanded) {
    CommonSettingsItemComposable(
      items =
        listOf(
          RearmExtensionMode.FIXED.toItem(context),
          RearmExtensionMode.MATCH_TIMER_DURATION.toItem(context),
        ),
      selectedItem = mode.toItem(context),
      onDismissRequest = { expanded = false },
      onItemSelected = { item ->
        RearmExtensionMode
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferSleepTimerRearmExtensionMode(it) }
        expanded = false
      },
    )
  }
}

private fun RearmExtensionMode.toItem(context: Context): CommonSettingsItem {
  val name =
    when (this) {
      RearmExtensionMode.FIXED -> context.getString(R.string.sleep_timer_rearm_extension_mode_fixed)
      RearmExtensionMode.MATCH_TIMER_DURATION -> context.getString(R.string.sleep_timer_rearm_extension_mode_match_timer)
    }
  return CommonSettingsItem(this.name, name, null)
}

@Composable
private fun RearmExtensionRowComposable(
  seconds: Int,
  enabled: Boolean,
  onClicked: () -> Unit,
) {
  val context = LocalContext.current
  val minutes = (seconds / 60).coerceAtLeast(1)

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .let {
          when (enabled) {
            true -> it.clickable { onClicked() }
            false -> it
          }
        }.padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sleep_timer_rearm_extension_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color =
          when (enabled) {
            true -> colorScheme.onBackground
            false -> colorScheme.onBackground.copy(alpha = 0.4f)
          },
      )
      Text(
        text = context.resources.getQuantityString(R.plurals.rearm_extension_minutes, minutes, minutes),
        style = typography.bodyMedium,
        color =
          when (enabled) {
            true -> colorScheme.onSurfaceVariant
            false -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          },
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RearmExtensionBottomSheet(
  currentSeconds: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  val context = LocalContext.current
  var selectedMinutes by remember { mutableIntStateOf((currentSeconds / 60).coerceAtLeast(1)) }

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.sleep_timer_rearm_extension_title),
          style = typography.bodyLarge,
        )

        CommonSlider(
          internalValue = selectedMinutes.coerceIn(REARM_EXTENSION_MIN_MINUTES, REARM_EXTENSION_MAX_MINUTES),
          range = REARM_EXTENSION_MIN_MINUTES..REARM_EXTENSION_MAX_MINUTES,
          formatHeader = { value ->
            val minutes = value.roundToInt().coerceIn(REARM_EXTENSION_MIN_MINUTES, REARM_EXTENSION_MAX_MINUTES)
            context.resources.getQuantityString(R.plurals.rearm_extension_minutes, minutes, minutes)
          },
          formatIndex = { "$it" },
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          labeledIndexes = rearmExtensionPresetMinutes,
          onUpdate = {
            val minutes = it.roundToInt().coerceIn(REARM_EXTENSION_MIN_MINUTES, REARM_EXTENSION_MAX_MINUTES)
            selectedMinutes = minutes
            onUpdate(minutes * 60)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          rearmExtensionPresetMinutes.forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedMinutes = preset
                  onUpdate(preset * 60)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedMinutes == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selectedMinutes == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = "$preset",
                style =
                  if (selectedMinutes == preset) {
                    typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  } else {
                    typography.labelMedium
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
  )
}

private const val REARM_EXTENSION_MIN_MINUTES = 1
private const val REARM_EXTENSION_MAX_MINUTES = SleepTimerSettings.MAX_REARM_EXTENSION_SECONDS / 60

private val rearmExtensionPresetMinutes = listOf(1, 2, 5, 10, 15)

/** Formats a duration as "N seconds" below a minute, "N minutes" on an exact minute, else "NmNs". */
private fun formatDuration(
  context: Context,
  totalSeconds: Int,
): String {
  if (totalSeconds < 60) {
    return context.resources.getQuantityString(R.plurals.duration_seconds, totalSeconds, totalSeconds)
  }

  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return if (seconds == 0) {
    context.resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
  } else {
    context.getString(R.string.duration_minutes_seconds, minutes, seconds)
  }
}

private fun formatDurationOrOff(
  context: Context,
  totalSeconds: Int,
): String =
  when (totalSeconds) {
    0 -> context.getString(R.string.sleep_timer_resume_rewind_off)
    else -> formatDuration(context, totalSeconds)
  }

@Composable
private fun ResumeRewindThresholdRowComposable(
  seconds: Int,
  onClicked: () -> Unit,
) {
  val context = LocalContext.current

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClicked() }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sleep_timer_resume_rewind_threshold_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = formatDuration(context, seconds),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ResumeRewindThresholdBottomSheet(
  currentSeconds: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  val context = LocalContext.current
  var selectedSeconds by remember { mutableIntStateOf(currentSeconds) }

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.sleep_timer_resume_rewind_threshold_title),
          style = typography.bodyLarge,
        )

        CommonSlider(
          internalValue = selectedSeconds.coerceIn(RESUME_REWIND_THRESHOLD_MIN_SECONDS, RESUME_REWIND_THRESHOLD_MAX_SECONDS),
          range = RESUME_REWIND_THRESHOLD_MIN_SECONDS..RESUME_REWIND_THRESHOLD_MAX_SECONDS,
          formatHeader = { value ->
            formatDuration(context, value.roundToInt().coerceIn(RESUME_REWIND_THRESHOLD_MIN_SECONDS, RESUME_REWIND_THRESHOLD_MAX_SECONDS))
          },
          formatIndex = { "$it" },
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          labeledIndexes = resumeRewindThresholdPresets,
          onUpdate = {
            val seconds = it.roundToInt().coerceIn(RESUME_REWIND_THRESHOLD_MIN_SECONDS, RESUME_REWIND_THRESHOLD_MAX_SECONDS)
            selectedSeconds = seconds
            onUpdate(seconds)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          resumeRewindThresholdPresets.forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedSeconds = preset
                  onUpdate(preset)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedSeconds == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selectedSeconds == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = presetLabel(preset),
                style =
                  if (selectedSeconds == preset) {
                    typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  } else {
                    typography.labelMedium
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
  )
}

private fun presetLabel(seconds: Int): String =
  when {
    seconds == 0 -> "0"
    seconds % 60 == 0 -> "${seconds / 60}m"
    else -> "${seconds}s"
  }

private const val RESUME_REWIND_THRESHOLD_MIN_SECONDS = SleepTimerSettings.MIN_RESUME_REWIND_THRESHOLD_SECONDS
private const val RESUME_REWIND_THRESHOLD_MAX_SECONDS = SleepTimerSettings.MAX_RESUME_REWIND_THRESHOLD_SECONDS

private val resumeRewindThresholdPresets = listOf(0, 60, 120, 300, 900)

@Composable
private fun ResumeRewindShortRowComposable(
  seconds: Int,
  onClicked: () -> Unit,
) {
  val context = LocalContext.current

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClicked() }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sleep_timer_resume_rewind_short_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text =
          when (seconds) {
            0 -> stringResource(R.string.sleep_timer_resume_rewind_off)
            else -> context.resources.getQuantityString(R.plurals.resume_rewind_seconds, seconds, seconds)
          },
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ResumeRewindShortBottomSheet(
  currentSeconds: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  val context = LocalContext.current
  var selectedSeconds by remember { mutableIntStateOf(currentSeconds) }

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.sleep_timer_resume_rewind_short_title),
          style = typography.bodyLarge,
        )

        CommonSlider(
          internalValue = selectedSeconds.coerceIn(RESUME_REWIND_SHORT_MIN_SECONDS, RESUME_REWIND_SHORT_MAX_SECONDS),
          range = RESUME_REWIND_SHORT_MIN_SECONDS..RESUME_REWIND_SHORT_MAX_SECONDS,
          formatHeader = { value ->
            val seconds = value.roundToInt().coerceIn(RESUME_REWIND_SHORT_MIN_SECONDS, RESUME_REWIND_SHORT_MAX_SECONDS)
            when (seconds) {
              0 -> context.getString(R.string.sleep_timer_resume_rewind_off)
              else -> context.resources.getQuantityString(R.plurals.resume_rewind_seconds, seconds, seconds)
            }
          },
          formatIndex = { "$it" },
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          labeledIndexes = resumeRewindShortPresets,
          onUpdate = {
            val seconds = it.roundToInt().coerceIn(RESUME_REWIND_SHORT_MIN_SECONDS, RESUME_REWIND_SHORT_MAX_SECONDS)
            selectedSeconds = seconds
            onUpdate(seconds)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          resumeRewindShortPresets.forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedSeconds = preset
                  onUpdate(preset)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedSeconds == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selectedSeconds == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = "$preset",
                style =
                  if (selectedSeconds == preset) {
                    typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  } else {
                    typography.labelMedium
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
  )
}

private const val RESUME_REWIND_SHORT_MIN_SECONDS = SleepTimerSettings.MIN_RESUME_REWIND_SHORT_SECONDS
private const val RESUME_REWIND_SHORT_MAX_SECONDS = SleepTimerSettings.MAX_RESUME_REWIND_SHORT_SECONDS

private val resumeRewindShortPresets = listOf(0, 10, 30, 60, 120)

@Composable
private fun ResumeRewindLongModeComposable(viewModel: PlaybackSettingsViewModel) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }
  val mode by viewModel.sleepTimerResumeRewindLongMode.collectAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { expanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sleep_timer_resume_rewind_long_mode_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = mode.toItem(context).name,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (expanded) {
    CommonSettingsItemComposable(
      items =
        listOf(
          ResumeRewindLongMode.FIXED.toItem(context),
          ResumeRewindLongMode.MATCH_EXTENSION.toItem(context),
          ResumeRewindLongMode.MATCH_TIMER_DURATION.toItem(context),
        ),
      selectedItem = mode.toItem(context),
      onDismissRequest = { expanded = false },
      onItemSelected = { item ->
        ResumeRewindLongMode
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferSleepTimerResumeRewindLongMode(it) }
        expanded = false
      },
    )
  }
}

private fun ResumeRewindLongMode.toItem(context: Context): CommonSettingsItem {
  val name =
    when (this) {
      ResumeRewindLongMode.FIXED -> context.getString(R.string.sleep_timer_resume_rewind_long_mode_fixed)
      ResumeRewindLongMode.MATCH_EXTENSION -> context.getString(R.string.sleep_timer_resume_rewind_long_mode_match_extension)
      ResumeRewindLongMode.MATCH_TIMER_DURATION -> context.getString(R.string.sleep_timer_resume_rewind_long_mode_match_timer)
    }
  return CommonSettingsItem(this.name, name, null)
}

@Composable
private fun ResumeRewindLongRowComposable(
  seconds: Int,
  onClicked: () -> Unit,
) {
  val context = LocalContext.current

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClicked() }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sleep_timer_resume_rewind_long_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = formatDurationOrOff(context, seconds),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ResumeRewindLongBottomSheet(
  currentSeconds: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  val context = LocalContext.current
  var selectedSeconds by remember { mutableIntStateOf(currentSeconds) }

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.sleep_timer_resume_rewind_long_title),
          style = typography.bodyLarge,
        )

        CommonSlider(
          internalValue = selectedSeconds.coerceIn(RESUME_REWIND_LONG_MIN_SECONDS, RESUME_REWIND_LONG_MAX_SECONDS),
          range = RESUME_REWIND_LONG_MIN_SECONDS..RESUME_REWIND_LONG_MAX_SECONDS,
          formatHeader = { value ->
            formatDurationOrOff(context, value.roundToInt().coerceIn(RESUME_REWIND_LONG_MIN_SECONDS, RESUME_REWIND_LONG_MAX_SECONDS))
          },
          formatIndex = { "$it" },
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          labeledIndexes = resumeRewindLongPresets,
          onUpdate = {
            val seconds = it.roundToInt().coerceIn(RESUME_REWIND_LONG_MIN_SECONDS, RESUME_REWIND_LONG_MAX_SECONDS)
            selectedSeconds = seconds
            onUpdate(seconds)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          resumeRewindLongPresets.forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedSeconds = preset
                  onUpdate(preset)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedSeconds == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selectedSeconds == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = presetLabel(preset),
                style =
                  if (selectedSeconds == preset) {
                    typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  } else {
                    typography.labelMedium
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
  )
}

private const val RESUME_REWIND_LONG_MIN_SECONDS = SleepTimerSettings.MIN_RESUME_REWIND_LONG_SECONDS
private const val RESUME_REWIND_LONG_MAX_SECONDS = SleepTimerSettings.MAX_RESUME_REWIND_LONG_SECONDS

private val resumeRewindLongPresets = listOf(0, 60, 300, 900, 1800)

@Composable
private fun ChimeVolumeRowComposable(
  title: String,
  percent: Int,
  enabled: Boolean,
  onClicked: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .let {
          when (enabled) {
            true -> it.clickable { onClicked() }
            false -> it
          }
        }.padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color =
          when (enabled) {
            true -> colorScheme.onBackground
            false -> colorScheme.onBackground.copy(alpha = 0.4f)
          },
      )
      Text(
        text = "$percent%",
        style = typography.bodyMedium,
        color =
          when (enabled) {
            true -> colorScheme.onSurfaceVariant
            false -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          },
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChimeVolumeBottomSheet(
  title: String,
  currentPercent: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  var selectedPercent by remember { mutableIntStateOf(currentPercent) }

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = title,
          style = typography.bodyLarge,
        )

        CommonSlider(
          internalValue = selectedPercent.coerceIn(CHIME_VOLUME_MIN, CHIME_VOLUME_MAX),
          range = CHIME_VOLUME_MIN..CHIME_VOLUME_MAX,
          formatHeader = { value -> "${value.roundToInt().coerceIn(CHIME_VOLUME_MIN, CHIME_VOLUME_MAX)}%" },
          formatIndex = { "$it" },
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          labeledIndexes = chimeVolumePresets,
          onUpdate = {
            val percent = it.roundToInt().coerceIn(CHIME_VOLUME_MIN, CHIME_VOLUME_MAX)
            selectedPercent = percent
            onUpdate(percent)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          chimeVolumePresets.forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedPercent = preset
                  onUpdate(preset)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedPercent == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selectedPercent == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = "$preset",
                style =
                  if (selectedPercent == preset) {
                    typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  } else {
                    typography.labelMedium
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
  )
}

private const val CHIME_VOLUME_MIN = SleepTimerSettings.MIN_CHIME_VOLUME
private const val CHIME_VOLUME_MAX = SleepTimerSettings.MAX_CHIME_VOLUME

private val chimeVolumePresets = listOf(0, 25, 50, 75, 100)

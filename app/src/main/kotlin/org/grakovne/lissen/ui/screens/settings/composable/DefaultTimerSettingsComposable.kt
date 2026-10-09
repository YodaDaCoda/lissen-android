package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.screens.player.composable.TimerComposable
import org.grakovne.lissen.viewmodel.PlaybackSettingsViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DefaultTimerSettingsComposable(
  viewModel: PlaybackSettingsViewModel,
  libraryType: LibraryType,
) {
  var timerExpanded by remember { mutableStateOf(false) }
  var startTimeExpanded by remember { mutableStateOf(false) }
  var endTimeExpanded by remember { mutableStateOf(false) }

  val defaultTimerOption by viewModel.defaultTimerOption.collectAsState()
  val scheduleEnabled by viewModel.sleepTimerDefaultScheduleEnabled.collectAsState()
  val scheduleStartMinute by viewModel.sleepTimerDefaultScheduleStartMinute.collectAsState()
  val scheduleEndMinute by viewModel.sleepTimerDefaultScheduleEndMinute.collectAsState()

  val context = LocalContext.current

  val timerDescription =
    when (val opt = defaultTimerOption) {
      null -> {
        stringResource(R.string.timer_option_disabled)
      }

      CurrentEpisodeTimerOption -> {
        when (libraryType) {
          LibraryType.PODCAST -> stringResource(R.string.timer_option_after_current_episode)
          else -> stringResource(R.string.timer_option_after_current_chapter)
        }
      }

      is DurationTimerOption -> {
        context.resources.getQuantityString(
          R.plurals.timer_option_after_time,
          opt.duration,
          opt.duration,
        )
      }
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { timerExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.settings_screen_default_sleep_timer_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = timerDescription,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (timerExpanded) {
    TimerComposable(
      currentOption = defaultTimerOption,
      libraryType = libraryType,
      onOptionSelected = { viewModel.saveDefaultTimerOption(it) },
      onDismissRequest = { timerExpanded = false },
    )
  }

  val scheduleControlsEnabled = defaultTimerOption != null

  SettingsToggleItem(
    title = stringResource(R.string.default_timer_schedule_title),
    description = stringResource(R.string.default_timer_schedule_description),
    initialState = scheduleEnabled,
    enabled = scheduleControlsEnabled,
  ) { viewModel.preferSleepTimerDefaultScheduleEnabled(it) }

  DefaultTimerScheduleTimeRow(
    title = stringResource(R.string.default_timer_schedule_start_title),
    minuteOfDay = scheduleStartMinute,
    enabled = scheduleControlsEnabled && scheduleEnabled,
    onClicked = { startTimeExpanded = true },
  )

  DefaultTimerScheduleTimeRow(
    title = stringResource(R.string.default_timer_schedule_end_title),
    minuteOfDay = scheduleEndMinute,
    enabled = scheduleControlsEnabled && scheduleEnabled,
    onClicked = { endTimeExpanded = true },
  )

  if (startTimeExpanded) {
    TimePickerDialog(
      initialMinuteOfDay = scheduleStartMinute,
      title = stringResource(R.string.default_timer_schedule_start_title),
      onDismissRequest = { startTimeExpanded = false },
      onConfirm = { viewModel.preferSleepTimerDefaultScheduleStartMinute(it) },
    )
  }

  if (endTimeExpanded) {
    TimePickerDialog(
      initialMinuteOfDay = scheduleEndMinute,
      title = stringResource(R.string.default_timer_schedule_end_title),
      onDismissRequest = { endTimeExpanded = false },
      onConfirm = { viewModel.preferSleepTimerDefaultScheduleEndMinute(it) },
    )
  }
}

@Composable
private fun DefaultTimerScheduleTimeRow(
  title: String,
  minuteOfDay: Int,
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
        text = formatTimeOfDay(minuteOfDay),
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
private fun TimePickerDialog(
  initialMinuteOfDay: Int,
  title: String,
  onDismissRequest: () -> Unit,
  onConfirm: (Int) -> Unit,
) {
  val state =
    rememberTimePickerState(
      initialHour = initialMinuteOfDay / 60,
      initialMinute = initialMinuteOfDay % 60,
      is24Hour = false,
    )

  AlertDialog(
    onDismissRequest = onDismissRequest,
    title = { Text(title) },
    text = { TimePicker(state = state) },
    confirmButton = {
      TextButton(onClick = {
        onConfirm(state.hour * 60 + state.minute)
        onDismissRequest()
      }) {
        Text(stringResource(android.R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismissRequest) {
        Text(stringResource(android.R.string.cancel))
      }
    },
  )
}

private fun formatTimeOfDay(minuteOfDay: Int): String {
  val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
  return DateTimeFormatter.ofPattern("h:mm a").format(time)
}

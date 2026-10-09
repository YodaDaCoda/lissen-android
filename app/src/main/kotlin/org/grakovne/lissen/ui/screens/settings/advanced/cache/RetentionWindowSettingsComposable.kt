package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
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
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.RetentionUnit
import org.grakovne.lissen.domain.RetentionWindow
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.slider.CommonSlider
import org.grakovne.lissen.ui.screens.common.formatMinutesDuration
import org.grakovne.lissen.viewmodel.DownloadSettingsViewModel
import kotlin.math.roundToInt

private const val MAX_CHAPTERS = 10
private val presetChapters = listOf(0, 1, 2, 3, 5)
private val chapterSliderLabeledIndexes = (0..MAX_CHAPTERS).toList()

private const val MAX_MINUTES = 180
private val presetMinutes = listOf(0, 5, 15, 30, 60)
private val minuteSliderLabeledIndexes = listOf(0) + (15..MAX_MINUTES step 15)

private const val MAX_MULTIPLE = 10
private val presetMultiples = listOf(0, 1, 2, 3)
private val multipleSliderLabeledIndexes = (0..MAX_MULTIPLE).toList()

@Composable
fun RetentionWindowSettingsComposable(
  viewModel: DownloadSettingsViewModel,
  enabled: Boolean,
) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }
  val retentionWindow by viewModel.autoCacheRetentionWindow.collectAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled) { expanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.download_settings_retention_window_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color = if (enabled) colorScheme.onBackground else colorScheme.onBackground.copy(alpha = 0.4f),
      )
      Text(
        text = retentionWindow.describe(context),
        style = typography.bodyMedium,
        color = if (enabled) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
      )
    }
  }

  if (expanded) {
    RetentionWindowSheet(
      selected = retentionWindow,
      onSelected = { viewModel.preferAutoCacheRetentionWindow(it) },
      onDismissRequest = { expanded = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionWindowSheet(
  selected: RetentionWindow,
  onSelected: (RetentionWindow) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current

  var unit by remember { mutableStateOf(selected.unit) }
  var chapterValue by remember {
    mutableIntStateOf(
      if (selected.unit ==
        RetentionUnit.CHAPTERS
      ) {
        selected.amount
      } else {
        presetChapters.first()
      },
    )
  }
  var minuteValue by remember { mutableIntStateOf(if (selected.unit == RetentionUnit.MINUTES) selected.amount else presetMinutes.first()) }
  var multipleValue by remember {
    mutableIntStateOf(if (selected.unit == RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE) selected.amount else presetMultiples.first())
  }

  fun current(): RetentionWindow =
    when (unit) {
      RetentionUnit.CHAPTERS -> RetentionWindow(RetentionUnit.CHAPTERS, chapterValue)
      RetentionUnit.MINUTES -> RetentionWindow(RetentionUnit.MINUTES, minuteValue)
      RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> RetentionWindow(RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE, multipleValue)
    }

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
          text = stringResource(R.string.download_settings_retention_window_title),
          style = typography.bodyLarge,
        )

        Row(
          modifier = Modifier.padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          RetentionUnit.entries.forEach { candidate ->
            val selectedUnit = unit == candidate
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  unit = candidate
                  onSelected(current())
                }
              },
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = if (selectedUnit) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor = if (selectedUnit) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
            ) {
              Text(text = candidate.label(context))
            }
          }
        }

        when (unit) {
          RetentionUnit.CHAPTERS -> {
            CommonSlider(
              internalValue = chapterValue,
              range = 0..MAX_CHAPTERS,
              formatHeader = { current ->
                context.resources.getQuantityString(
                  R.plurals.download_settings_retention_chapters,
                  current.roundToInt(),
                  current.roundToInt(),
                )
              },
              formatIndex = { index -> index },
              labeledIndexes = chapterSliderLabeledIndexes,
              modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
              onUpdate = {
                chapterValue = it.roundToInt().coerceIn(0, MAX_CHAPTERS)
                onSelected(current())
              },
            )
          }

          RetentionUnit.MINUTES -> {
            CommonSlider(
              internalValue = minuteValue,
              range = 0..MAX_MINUTES,
              formatHeader = { current -> formatMinutesDuration(context, current.roundToInt()) },
              formatIndex = { index -> index },
              labeledIndexes = minuteSliderLabeledIndexes,
              modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
              onUpdate = {
                minuteValue = it.roundToInt().coerceIn(0, MAX_MINUTES)
                onSelected(current())
              },
            )
          }

          RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> {
            CommonSlider(
              internalValue = multipleValue,
              range = 0..MAX_MULTIPLE,
              formatHeader = { current ->
                context.getString(R.string.download_settings_retention_rearm_multiple, current.roundToInt())
              },
              formatIndex = { index -> index },
              labeledIndexes = multipleSliderLabeledIndexes,
              modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
              onUpdate = {
                multipleValue = it.roundToInt().coerceIn(0, MAX_MULTIPLE)
                onSelected(current())
              },
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          val presets =
            when (unit) {
              RetentionUnit.CHAPTERS -> presetChapters
              RetentionUnit.MINUTES -> presetMinutes
              RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> presetMultiples
            }
          val currentValue =
            when (unit) {
              RetentionUnit.CHAPTERS -> chapterValue
              RetentionUnit.MINUTES -> minuteValue
              RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> multipleValue
            }

          presets.forEach { preset ->
            val isSelected = currentValue == preset

            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  when (unit) {
                    RetentionUnit.CHAPTERS -> chapterValue = preset
                    RetentionUnit.MINUTES -> minuteValue = preset
                    RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> multipleValue = preset
                  }
                  onSelected(current())
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = if (isSelected) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = "$preset",
                style = if (isSelected) typography.labelMedium.copy(fontWeight = FontWeight.Bold) else typography.labelMedium,
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

private fun RetentionUnit.label(context: Context): String =
  when (this) {
    RetentionUnit.CHAPTERS -> context.getString(R.string.settings_download_automatically_unit_chapters)
    RetentionUnit.MINUTES -> context.getString(R.string.settings_download_automatically_unit_time)
    RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> context.getString(R.string.download_settings_retention_unit_rearm)
  }

private fun RetentionWindow.describe(context: Context): String =
  when (unit) {
    RetentionUnit.CHAPTERS -> context.resources.getQuantityString(R.plurals.download_settings_retention_chapters, amount, amount)
    RetentionUnit.MINUTES -> formatMinutesDuration(context, amount)
    RetentionUnit.SLEEP_TIMER_REARM_MULTIPLE -> context.getString(R.string.download_settings_retention_rearm_multiple, amount)
  }

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.DurationDownloadOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.domain.RemainingItemsDownloadOption
import org.grakovne.lissen.domain.makeId
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.slider.CommonSlider
import org.grakovne.lissen.ui.screens.common.makeText
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.viewmodel.DownloadSettingsViewModel
import kotlin.math.roundToInt

private const val OFF_VALUE = 0
private const val MAX_VALUE = 999
private val presetCounts = listOf(1, 3, 5, 10)
private val presetButtons = listOf<Int?>(null) + presetCounts
private val sliderLabeledIndexes = listOf(OFF_VALUE, 1) + (5..MAX_VALUE step 5)

private const val MAX_MINUTES = 24 * 60
private val presetMinutes = listOf(30, 60, 120, 240)
private val minutePresetButtons = listOf<Int?>(null) + presetMinutes
private val minuteSliderLabeledIndexes = listOf(OFF_VALUE, 30) + (60..MAX_MINUTES step 60)

private enum class DepthUnit { CHAPTERS, TIME }

@Composable
fun AutoCacheSettingsComposable(
  viewModel: DownloadSettingsViewModel,
  libraryType: LibraryType,
) {
  val context = LocalContext.current
  var autoCacheExpanded by remember { mutableStateOf(false) }
  val preferredDownloadOption by viewModel.preferredAutoDownloadOption.collectAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { autoCacheExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.settings_download_automatically_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = preferredDownloadOption.toSettingsItem(context, libraryType).name,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (autoCacheExpanded) {
    AutoCacheOptionsSheet(
      selectedOption = preferredDownloadOption,
      libraryType = libraryType,
      onOptionSelected = { viewModel.preferAutoDownloadOption(it) },
      onDismissRequest = { autoCacheExpanded = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoCacheOptionsSheet(
  selectedOption: DownloadOption?,
  libraryType: LibraryType,
  onOptionSelected: (DownloadOption?) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current

  var unit by remember { mutableStateOf(selectedOption.toDepthUnit()) }
  var itemValue by remember { mutableIntStateOf(selectedOption.toSliderValue()) }
  var minuteValue by remember { mutableIntStateOf(selectedOption.toMinuteSliderValue()) }

  fun currentOption(): DownloadOption? =
    when (unit) {
      DepthUnit.CHAPTERS -> itemValue.toDownloadOption()
      DepthUnit.TIME -> minuteValue.toDurationDownloadOption()
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
          text = stringResource(R.string.settings_download_automatically_title),
          style = typography.bodyLarge,
        )

        Row(
          modifier = Modifier.padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DepthUnit.entries.forEach { candidate ->
            val selected = unit == candidate
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  unit = candidate
                  onOptionSelected(currentOption())
                }
              },
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = if (selected) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
            ) {
              Text(
                text =
                  when (candidate) {
                    DepthUnit.CHAPTERS -> stringResource(R.string.settings_download_automatically_unit_chapters)
                    DepthUnit.TIME -> stringResource(R.string.settings_download_automatically_unit_time)
                  },
              )
            }
          }
        }

        when (unit) {
          DepthUnit.CHAPTERS -> {
            CommonSlider(
              internalValue = itemValue,
              range = OFF_VALUE..MAX_VALUE,
              formatHeader = { current ->
                current
                  .roundToInt()
                  .coerceIn(OFF_VALUE, MAX_VALUE)
                  .toDownloadOption()
                  .makeText(context, libraryType)
              },
              formatIndex = { index ->
                if (index <= OFF_VALUE) Icons.Outlined.Close else index
              },
              labeledIndexes = sliderLabeledIndexes,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
              onUpdate = {
                itemValue = it.roundToInt().coerceIn(OFF_VALUE, MAX_VALUE)
                onOptionSelected(itemValue.toDownloadOption())
              },
            )
          }

          DepthUnit.TIME -> {
            CommonSlider(
              internalValue = minuteValue,
              range = OFF_VALUE..MAX_MINUTES,
              formatHeader = { current ->
                current
                  .roundToInt()
                  .coerceIn(OFF_VALUE, MAX_MINUTES)
                  .toDurationDownloadOption()
                  .makeText(context, libraryType)
              },
              formatIndex = { index ->
                if (index <= OFF_VALUE) Icons.Outlined.Close else index
              },
              labeledIndexes = minuteSliderLabeledIndexes,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
              onUpdate = {
                minuteValue = it.roundToInt().coerceIn(OFF_VALUE, MAX_MINUTES)
                onOptionSelected(minuteValue.toDurationDownloadOption())
              },
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          val presets = if (unit == DepthUnit.CHAPTERS) presetButtons else minutePresetButtons
          val selectedValue = if (unit == DepthUnit.CHAPTERS) itemValue else minuteValue

          presets.forEach { preset ->
            val selected =
              when (preset) {
                null -> selectedValue <= OFF_VALUE
                else -> selectedValue == preset
              }

            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  when (unit) {
                    DepthUnit.CHAPTERS -> {
                      itemValue = preset ?: OFF_VALUE
                      onOptionSelected(itemValue.toDownloadOption())
                    }

                    DepthUnit.TIME -> {
                      minuteValue = preset ?: OFF_VALUE
                      onOptionSelected(minuteValue.toDurationDownloadOption())
                    }
                  }
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selected) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              if (preset == null) {
                val fontSize = typography.labelMedium.fontSize
                val iconSize = with(LocalDensity.current) { fontSize.toDp() } * 1.5f

                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = null,
                  modifier = Modifier.size(iconSize),
                )
              } else {
                Text(
                  text = "$preset",
                  style =
                    if (selected) {
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
      }
    },
  )
}

private fun Int.toDownloadOption(): DownloadOption? =
  when {
    this <= OFF_VALUE -> null
    else -> NumberItemDownloadOption(this)
  }

private fun Int.toDurationDownloadOption(): DownloadOption? =
  when {
    this <= OFF_VALUE -> null
    else -> DurationDownloadOption(this)
  }

private fun DownloadOption?.toDepthUnit(): DepthUnit =
  when (this) {
    is DurationDownloadOption -> DepthUnit.TIME
    else -> DepthUnit.CHAPTERS
  }

private fun DownloadOption?.toSliderValue(): Int =
  when (this) {
    null -> OFF_VALUE
    is NumberItemDownloadOption -> itemsNumber.coerceIn(1, MAX_VALUE)
    RemainingItemsDownloadOption, AllItemsDownloadOption -> MAX_VALUE
    else -> 1
  }

private fun DownloadOption?.toMinuteSliderValue(): Int =
  when (this) {
    is DurationDownloadOption -> minutes.coerceIn(1, MAX_MINUTES)
    else -> OFF_VALUE
  }

private fun DownloadOption?.toSettingsItem(
  context: Context,
  libraryType: LibraryType,
): CommonSettingsItem =
  CommonSettingsItem(
    id = this.makeId(),
    name = this.makeText(context, libraryType),
    icon = null,
  )

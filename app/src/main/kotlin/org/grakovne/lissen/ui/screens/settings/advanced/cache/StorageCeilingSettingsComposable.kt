package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
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
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.viewmodel.DownloadSettingsViewModel

private val presetBytes = listOf(500L * 1024 * 1024, 1_000_000_000L, 2_000_000_000L, 5_000_000_000L)

@Composable
fun StorageCeilingSettingsComposable(
  viewModel: DownloadSettingsViewModel,
  enabled: Boolean,
) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }
  val ceilingBytes by viewModel.autoDownloadStorageCeilingBytes.collectAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled) { expanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.download_settings_storage_ceiling_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color = if (enabled) colorScheme.onBackground else colorScheme.onBackground.copy(alpha = 0.4f),
      )
      Text(
        text = ceilingBytes.toItem(context).name,
        style = typography.bodyMedium,
        color = if (enabled) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
      )
    }
  }

  if (expanded) {
    CommonSettingsItemComposable(
      items = presetBytes.map { it.toItem(context) },
      selectedItem = ceilingBytes.toItem(context),
      onDismissRequest = { expanded = false },
      onItemSelected = { item -> item.id.toLongOrNull()?.let { viewModel.preferAutoDownloadStorageCeilingBytes(it) } },
    )
  }
}

private fun Long.toItem(context: Context): CommonSettingsItem =
  CommonSettingsItem(toString(), Formatter.formatShortFileSize(context, this), null)

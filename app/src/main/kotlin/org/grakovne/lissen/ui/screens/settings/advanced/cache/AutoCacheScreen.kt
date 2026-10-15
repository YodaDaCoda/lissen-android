package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.screens.settings.composable.SettingsTopAppBar
import org.grakovne.lissen.viewmodel.AutoCacheBookItem
import org.grakovne.lissen.viewmodel.AutoCacheViewModel

@Composable
fun AutoCacheScreen(
  onBack: () -> Unit,
  viewModel: AutoCacheViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val items by viewModel.items.collectAsState()
  val usedBytes by viewModel.usedBytes.collectAsState()
  var showInfo by remember { mutableStateOf(false) }
  var confirmClearAll by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { viewModel.refresh() }

  Scaffold(
    topBar = { SettingsTopAppBar(title = stringResource(R.string.auto_cache_title), onBack = onBack) },
    modifier = Modifier.systemBarsPadding().fillMaxHeight(),
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            pluralStringResource(
              R.plurals.auto_cache_summary,
              items.size,
              items.size,
              Formatter.formatShortFileSize(context, usedBytes),
              Formatter.formatShortFileSize(context, viewModel.ceilingBytes),
            ),
          style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { showInfo = true }) {
          Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.auto_cache_info_content_description))
        }
      }

      if (items.isEmpty()) {
        Text(
          text = stringResource(R.string.auto_cache_empty),
          style = typography.bodyMedium,
          color = colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
      }

      LazyColumn(modifier = Modifier.weight(1f)) {
        items(items, key = { it.bookId }) { item ->
          AutoCacheBookRow(item, onClear = { viewModel.clear(item.bookId) })
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          modifier = Modifier.weight(1f),
          onClick = {
            val intent =
              Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            context.startActivity(intent)
          },
        ) { Text(stringResource(R.string.auto_cache_open_app_storage)) }

        OutlinedButton(
          modifier = Modifier.weight(1f),
          enabled = items.isNotEmpty(),
          onClick = { confirmClearAll = true },
        ) { Text(stringResource(R.string.auto_cache_clear_all)) }
      }
    }
  }

  if (showInfo) {
    AlertDialog(
      onDismissRequest = { showInfo = false },
      title = { Text(stringResource(R.string.auto_cache_info_title)) },
      text = { Text(stringResource(R.string.auto_cache_info_body)) },
      confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.auto_cache_dialog_close)) } },
    )
  }

  if (confirmClearAll) {
    AlertDialog(
      onDismissRequest = { confirmClearAll = false },
      title = { Text(stringResource(R.string.auto_cache_clear_all)) },
      text = { Text(stringResource(R.string.auto_cache_clear_all_confirmation_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmClearAll = false
            viewModel.clearAll()
          },
        ) { Text(stringResource(R.string.auto_cache_clear_all)) }
      },
      dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text(stringResource(R.string.auto_cache_dialog_cancel)) } },
    )
  }
}

@Composable
private fun AutoCacheBookRow(
  item: AutoCacheBookItem,
  onClear: () -> Unit,
) {
  val context = LocalContext.current

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = item.title, style = typography.bodyLarge, maxLines = 1)

      val lastPlayed =
        if (item.lastActiveAt > 0) {
          stringResource(
            R.string.auto_cache_last_played,
            DateUtils.getRelativeTimeSpanString(item.lastActiveAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
          )
        } else {
          stringResource(R.string.auto_cache_never_played)
        }

      Text(
        text = "${Formatter.formatShortFileSize(context, item.bytes)} · $lastPlayed",
        style = typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )
    }

    IconButton(onClick = onClear) {
      Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.auto_cache_clear_book_content_description, item.title))
    }
  }
}

package org.grakovne.lissen.ui.screens.player.composable

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.viewmodel.PlayerViewModel

/** Stays up until playback works again; the code is always shown, and tapping it copies it. */
@Composable
fun PlaybackFailureBanner(
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
) {
  val failure by viewModel.playbackFailure.collectAsState()
  val current = failure ?: return
  val context = LocalContext.current
  val copyLabel = stringResource(R.string.playback_failure_copy_code)

  Card(
    colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer, contentColor = colorScheme.onErrorContainer),
    modifier = modifier.fillMaxWidth().testTag("playbackFailureBanner"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = stringResource(if (current.network) R.string.playback_failure_network_title else R.string.playback_failure_unknown_title),
        style = typography.titleSmall,
      )
      Text(
        text = stringResource(if (current.network) R.string.playback_failure_network_hint else R.string.playback_failure_unknown_hint),
        style = typography.bodyMedium,
      )
      Text(
        text = current.code,
        style = typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        modifier =
          Modifier
            .clickable(onClickLabel = copyLabel) { copyToClipboard(context, current.code) }
            .semantics { contentDescription = "${current.code}. $copyLabel" },
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (current.retrying) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          Text(
            text = stringResource(R.string.playback_failure_retrying),
            style = typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
          )
        } else {
          TextButton(onClick = { viewModel.togglePlayPause() }) {
            Text(stringResource(R.string.playback_failure_retry))
          }
        }
      }
    }
  }
}

private fun copyToClipboard(
  context: Context,
  text: String,
) {
  val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  manager.setPrimaryClip(ClipData.newPlainText("error code", text))
}

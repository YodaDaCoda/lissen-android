package org.grakovne.lissen.ui.screens.onboarding

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.persistence.preferences.OnboardingPreferences
import org.grakovne.lissen.ui.screens.common.hasLocalNetworkPermission
import org.grakovne.lissen.ui.screens.common.hasNotificationPermission
import org.grakovne.lissen.ui.screens.common.isLocalNetworkPermissionRequired
import org.grakovne.lissen.ui.screens.common.isNotificationPermissionRequired
import org.grakovne.lissen.ui.screens.common.localNetworkPermission
import org.grakovne.lissen.ui.screens.common.notificationPermission
import org.grakovne.lissen.viewmodel.OnboardingViewModel

/** Whether the first-launch wizard has anything left to ask: it is skipped on a device that needs neither permission. */
fun isOnboardingRequired(
  context: Context,
  preferences: OnboardingPreferences,
): Boolean =
  preferences.isCompleted().not() &&
    (
      (isNotificationPermissionRequired() && hasNotificationPermission(context).not()) ||
        (isLocalNetworkPermissionRequired() && hasLocalNetworkPermission(context).not())
    )

@Composable
fun OnboardingScreen(
  onFinished: () -> Unit,
  viewModel: OnboardingViewModel = hiltViewModel(),
) {
  val context = LocalContext.current

  var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
  var localNetworkGranted by remember { mutableStateOf(hasLocalNetworkPermission(context)) }

  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsGranted = it }

  val localNetworkLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { localNetworkGranted = it }

  var settingsImported by rememberSaveable { mutableStateOf(false) }
  val importFailedToast = stringResource(R.string.import_config_failed_toast)

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val json =
        uri?.let {
          runCatching {
            context.contentResolver.openInputStream(it)?.use { stream -> stream.bufferedReader().readText() }
          }.getOrNull()
        }

      settingsImported = json != null && viewModel.importSettingsJson(json)

      if (uri != null && settingsImported.not()) {
        Toast.makeText(context, importFailedToast, Toast.LENGTH_SHORT).show()
      }
    }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp),
  ) {
    Column(
      modifier =
        Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 32.dp)) {
        Text(
          text = stringResource(R.string.onboarding_title),
          style = typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
          color = colorScheme.onBackground,
        )
        Text(
          text = stringResource(R.string.onboarding_subtitle),
          style = typography.bodyLarge,
          color = colorScheme.onSurfaceVariant,
        )
      }

      if (isNotificationPermissionRequired()) {
        PermissionRow(
          title = stringResource(R.string.onboarding_notifications_title),
          description = stringResource(R.string.onboarding_notifications_description),
          granted = notificationsGranted,
          tag = "onboardingNotificationsButton",
          onAllow = { notificationLauncher.launch(notificationPermission()) },
        )
      }

      if (isLocalNetworkPermissionRequired()) {
        PermissionRow(
          title = stringResource(R.string.onboarding_local_network_title),
          description = stringResource(R.string.onboarding_local_network_description),
          granted = localNetworkGranted,
          tag = "onboardingLocalNetworkButton",
          onAllow = { localNetworkLauncher.launch(localNetworkPermission()) },
        )
      }

      PermissionRow(
        title = stringResource(R.string.onboarding_import_title),
        description = stringResource(R.string.onboarding_import_description),
        granted = settingsImported,
        tag = "onboardingImportButton",
        actionLabel = stringResource(R.string.onboarding_import),
        doneLabel = stringResource(R.string.onboarding_imported),
        onAllow = { importLauncher.launch(arrayOf("application/json")) },
      )
    }

    Button(
      onClick = {
        viewModel.complete()
        onFinished()
      },
      modifier =
        Modifier
          .fillMaxWidth()
          .testTag("onboardingContinueButton"),
    ) {
      Text(stringResource(R.string.onboarding_continue))
    }
  }
}

@Composable
private fun PermissionRow(
  title: String,
  description: String,
  granted: Boolean,
  tag: String,
  onAllow: () -> Unit,
  actionLabel: String = stringResource(R.string.onboarding_allow),
  doneLabel: String = stringResource(R.string.onboarding_allowed),
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = title,
        style = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = colorScheme.onBackground,
      )
      Text(text = description, style = typography.bodyMedium, color = colorScheme.onSurfaceVariant)
    }

    FilledTonalButton(
      onClick = onAllow,
      enabled = granted.not(),
      modifier = Modifier.testTag(tag),
    ) {
      Text(if (granted) doneLabel else actionLabel)
    }
  }
}

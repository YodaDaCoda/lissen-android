package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** M3 Slider track drawn by the expressive wavy indicator. Amplitude snaps between flat and wavy; the wave stands still with system animations off. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WavyTrack(
  sliderState: SliderState,
  waving: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val animationsOn = rememberAnimationsOn(context)
  LinearWavyProgressIndicator(
    progress = { sliderState.coercedValueAsFraction },
    modifier = modifier.fillMaxWidth(),
    color = colorScheme.primary,
    trackColor = colorScheme.surfaceVariant,
    amplitude = { if (waving) 1f else 0f },
    waveSpeed = if (animationsOn) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
  )
}

/** The system animations setting, kept current: the user can flip it while the player is on screen. */
@Composable
private fun rememberAnimationsOn(context: Context): Boolean {
  val resolver = context.contentResolver
  val read = { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
  var animationsOn by remember { mutableStateOf(read()) }

  DisposableEffect(resolver) {
    val observer =
      object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
          animationsOn = read()
        }
      }
    resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
    animationsOn = read()
    onDispose { resolver.unregisterContentObserver(observer) }
  }

  return animationsOn
}

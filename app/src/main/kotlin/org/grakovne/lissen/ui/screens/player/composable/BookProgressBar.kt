package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.PlaybackGeometry

/** Book progress as 0..1. A zero, negative or non-finite duration (corrupted metadata) draws as empty. */
internal fun bookProgressFraction(
  position: Double,
  duration: Double,
): Float =
  when (duration.isFinite() && duration > 0 && position.isFinite()) {
    true -> (position / duration).toFloat().coerceIn(0f, 1f)
    false -> 0f
  }

/**
 * Book progress as a lambda over the position [State], so only the bar reading it recomposes on a tick, not the cover around it.
 * The total duration is summed once per book rather than per tick.
 */
@Composable
fun rememberBookProgress(
  book: DetailedItem?,
  totalPosition: State<Double>,
): () -> Float {
  val duration = remember(book) { book?.let(PlaybackGeometry::totalDuration) ?: 0.0 }
  return { bookProgressFraction(totalPosition.value, duration) }
}

/** Thin, flat, display-only Book progress bar meant to sit at the bottom edge of the cover. */
@Composable
fun BookProgressBar(
  progress: () -> Float,
  modifier: Modifier = Modifier,
) {
  val fraction = progress()
  val percent = (fraction * 100).toInt()
  val description = stringResource(R.string.player_screen_book_progress_description, percent)

  Box(
    contentAlignment = Alignment.BottomStart,
    modifier =
      modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))))
        .padding(horizontal = 8.dp, vertical = 6.dp)
        .clearAndSetSemantics {
          contentDescription = description
          progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(4.dp)
          .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxHeight()
            .fillMaxWidth(fraction)
            .background(colorScheme.primary, RoundedCornerShape(2.dp)),
      )
    }
  }
}

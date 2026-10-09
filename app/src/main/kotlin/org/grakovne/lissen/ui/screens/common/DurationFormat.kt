package org.grakovne.lissen.ui.screens.common

import android.content.Context
import org.grakovne.lissen.R

/** "4h 10m" / "4 hours" / "10 minutes", depending on which parts are non-zero. */
fun formatMinutesDuration(
  context: Context,
  totalMinutes: Int,
): String {
  val hours = totalMinutes / 60
  val minutes = totalMinutes % 60

  return when {
    hours > 0 && minutes > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
    hours > 0 -> context.resources.getQuantityString(R.plurals.a11y_hours, hours, hours)
    else -> context.resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
  }
}

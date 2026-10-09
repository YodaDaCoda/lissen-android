package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
enum class RetentionUnit {
  CHAPTERS,
  MINUTES,
  SLEEP_TIMER_REARM_MULTIPLE,
}

/** How much already-played, auto-cached audio to keep behind the playhead before trimming it. */
@Keep
@JsonClass(generateAdapter = true)
data class RetentionWindow(
  val unit: RetentionUnit,
  val amount: Int,
) {
  companion object {
    val DEFAULT = RetentionWindow(RetentionUnit.MINUTES, 5)
  }
}

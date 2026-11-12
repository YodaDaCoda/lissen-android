package org.grakovne.lissen.domain

import androidx.annotation.Keep
import java.io.Serializable

@Keep
sealed interface DownloadOption : Serializable

class NumberItemDownloadOption(
  val itemsNumber: Int,
) : DownloadOption

class DurationDownloadOption(
  val minutes: Int,
) : DownloadOption

data object CurrentItemDownloadOption : DownloadOption

data object RemainingItemsDownloadOption : DownloadOption

data object AllItemsDownloadOption : DownloadOption

fun DownloadOption?.makeId() =
  when (this) {
    null -> "disabled"
    AllItemsDownloadOption -> "all_items"
    CurrentItemDownloadOption -> "current_item"
    is NumberItemDownloadOption -> "number_items_$itemsNumber"
    is DurationDownloadOption -> "duration_minutes_$minutes"
    RemainingItemsDownloadOption -> "remaining_items"
  }

fun String?.makeDownloadOption(): DownloadOption? =
  when {
    this == null -> null
    this == "all_items" -> AllItemsDownloadOption
    this == "current_item" -> CurrentItemDownloadOption
    this == "remaining_items" -> RemainingItemsDownloadOption
    startsWith("number_items_") -> substringAfter("number_items_").toIntOrNull()?.let { NumberItemDownloadOption(it) }
    startsWith("duration_minutes_") -> substringAfter("duration_minutes_").toIntOrNull()?.let { DurationDownloadOption(it) }
    else -> null
  }

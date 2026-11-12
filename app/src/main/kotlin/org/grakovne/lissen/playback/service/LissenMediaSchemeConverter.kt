package org.grakovne.lissen.playback.service

import android.net.Uri

fun toLissenUri(
  mediaItemId: String,
  fileId: String,
): Uri =
  Uri
    .Builder()
    .scheme("lissen")
    .appendPath(mediaItemId)
    .appendPath(fileId)
    .build()

fun parseLissenUri(uri: Uri): Pair<String, String>? {
  if (uri.scheme != "lissen") return null

  val segments = uri.pathSegments
  if (segments.size != 2) return null

  val mediaItemId = segments[0].takeIf { it.isNotEmpty() } ?: return null
  val fileId = segments[1].takeIf { it.isNotEmpty() } ?: return null

  return mediaItemId to fileId
}

/** The key a file's bytes are cached under, independent of which host serves it. */
fun toCacheKey(
  mediaItemId: String,
  fileId: String,
): String = "lissen:$mediaItemId:$fileId"

fun parseCacheKey(key: String): Pair<String, String>? {
  val parts = key.split(':')
  if (parts.size != 3 || parts[0] != "lissen" || parts[1].isEmpty() || parts[2].isEmpty()) return null

  return parts[1] to parts[2]
}

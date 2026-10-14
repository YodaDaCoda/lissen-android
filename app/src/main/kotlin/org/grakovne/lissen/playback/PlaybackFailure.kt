package org.grakovne.lissen.playback

import androidx.media3.common.PlaybackException

/** A player error the UI keeps showing until playback works again; [retrying] while a new attempt is in flight. */
data class PlaybackFailure(
  val code: String,
  val network: Boolean,
  val retrying: Boolean = false,
) {
  companion object {
    private val NETWORK_CODES =
      setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
      )

    fun of(error: PlaybackException) = PlaybackFailure(error.errorCodeName, error.errorCode in NETWORK_CODES)
  }
}

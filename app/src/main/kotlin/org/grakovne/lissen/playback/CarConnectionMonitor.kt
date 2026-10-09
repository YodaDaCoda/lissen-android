package org.grakovne.lissen.playback

import android.content.Context
import androidx.annotation.MainThread
import androidx.car.app.connection.CarConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this device is currently connected to a car head unit - projected (Android Auto) or
 * native (Android Automotive OS). Driver-distraction-sensitive features (the sleep timer) are
 * disabled while this is true, regardless of how they'd otherwise be configured.
 */
@Singleton
class CarConnectionMonitor
  @Inject
  @MainThread
  constructor(
    @ApplicationContext context: Context,
  ) {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
      CarConnection(context).type.observeForever { type ->
        _isConnected.value = type != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
      }
    }
  }

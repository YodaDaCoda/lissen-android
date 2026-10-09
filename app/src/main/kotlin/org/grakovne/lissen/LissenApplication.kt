package org.grakovne.lissen

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.logging.LissenLogProvider
import org.grakovne.lissen.persistence.preferences.DiagnosticsPreferences
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LissenApplication : Application() {
  @Inject
  lateinit var runningComponents: Set<@JvmSuppressWildcards RunningComponent>

  @Inject
  lateinit var lissenLogProvider: LissenLogProvider

  @Inject
  lateinit var preferences: DiagnosticsPreferences

  override fun onCreate() {
    super.onCreate()
    appContext = applicationContext

    initLogging()
    initRunningComponents()
  }

  private fun initRunningComponents() {
    runningComponents.forEach {
      try {
        it.onCreate()
      } catch (ex: Exception) {
        Timber.e("Unable to register Running component due to: ${ex.message}")
      }
    }
  }

  private fun initLogging() {
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }

    if (preferences.isActivityLoggingEnabled()) {
      try {
        Timber.plant(lissenLogProvider.provideLoggingTree())
      } catch (ex: Exception) {
        Timber.e("Unable to plant file logging tree due to: ${ex.message}")
      }
    }
  }

  companion object {
    lateinit var appContext: Context
      private set
  }
}

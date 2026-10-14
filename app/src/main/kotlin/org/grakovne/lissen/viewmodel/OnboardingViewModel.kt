package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.grakovne.lissen.persistence.preferences.LissenConfigProvider
import org.grakovne.lissen.persistence.preferences.OnboardingPreferences
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
  @Inject
  constructor(
    private val preferences: OnboardingPreferences,
    private val configProvider: LissenConfigProvider,
  ) : ViewModel() {
    val completed: Boolean get() = preferences.isCompleted()

    fun complete() = preferences.markCompleted()

    fun importSettingsJson(json: String): Boolean = configProvider.importConfig(json)
  }

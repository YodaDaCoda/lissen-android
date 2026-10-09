package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.grakovne.lissen.persistence.preferences.OnboardingPreferences
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
  @Inject
  constructor(
    private val preferences: OnboardingPreferences,
  ) : ViewModel() {
    val completed: Boolean get() = preferences.isCompleted()

    fun complete() = preferences.markCompleted()
  }

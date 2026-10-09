package org.grakovne.lissen.persistence.preferences

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
  ) {
    /** True once the first-launch permissions wizard has been passed, by granting or by skipping. */
    fun isCompleted(): Boolean = store.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() = store.putBoolean(KEY_COMPLETED, true)

    private companion object {
      private const val KEY_COMPLETED = "onboarding_permissions_completed"
    }
  }

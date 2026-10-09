package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RearmExtensionMode
import org.grakovne.lissen.domain.ResumeRewindLongMode
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.viewmodel.PlaybackSettingsViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SleepTimerSettingsScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  private fun viewModelWith(
    defaultTimerOption: TimerOption?,
    rearmEnabled: Boolean = false,
    fadeEnabled: Boolean = false,
  ): PlaybackSettingsViewModel {
    val viewModel = mockk<PlaybackSettingsViewModel>(relaxed = true)
    every { viewModel.sleepTimerFade } returns MutableStateFlow(if (fadeEnabled) 30 else null)
    every { viewModel.sleepTimerChimeOnFadeStart } returns MutableStateFlow(true)
    every { viewModel.sleepTimerRearmEnabled } returns MutableStateFlow(rearmEnabled)
    every { viewModel.sleepTimerChimeOnRearm } returns MutableStateFlow(true)
    every { viewModel.sleepTimerRearmViaHeadphoneButton } returns MutableStateFlow(true)
    every { viewModel.sleepTimerRearmViaShake } returns MutableStateFlow(true)
    every { viewModel.sleepTimerRearmExtensionMode } returns MutableStateFlow(RearmExtensionMode.FIXED)
    every { viewModel.sleepTimerRearmExtensionSeconds } returns MutableStateFlow(300)
    every { viewModel.sleepTimerResumeRewindThresholdSeconds } returns MutableStateFlow(120)
    every { viewModel.sleepTimerResumeRewindShortSeconds } returns MutableStateFlow(0)
    every { viewModel.sleepTimerResumeRewindLongMode } returns MutableStateFlow(ResumeRewindLongMode.FIXED)
    every { viewModel.sleepTimerResumeRewindLongSeconds } returns MutableStateFlow(0)
    every { viewModel.sleepTimerChimeFadeVolume } returns MutableStateFlow(50)
    every { viewModel.sleepTimerChimeRearmVolume } returns MutableStateFlow(50)
    every { viewModel.sleepTimerDefaultScheduleEnabled } returns MutableStateFlow(false)
    every { viewModel.sleepTimerDefaultScheduleStartMinute } returns MutableStateFlow(20 * 60)
    every { viewModel.sleepTimerDefaultScheduleEndMinute } returns MutableStateFlow(8 * 60)
    every { viewModel.defaultTimerOption } returns MutableStateFlow(defaultTimerOption)
    return viewModel
  }

  @Test
  fun timerSettingsScreen_showsDefaultTimerRowNextToFadeControls() {
    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModelWith(null),
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Fade out").assertIsDisplayed()
    composeRule.onNodeWithText("30 seconds").assertIsDisplayed()
    composeRule.onNodeWithText("Default sleep timer while playing").assertIsDisplayed()
    composeRule.onNodeWithText("Disabled").assertIsDisplayed()
  }

  @Test
  fun timerSettingsScreen_defaultTimerRowShowsStoredDuration() {
    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModelWith(DurationTimerOption(45)),
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Default sleep timer while playing").assertIsDisplayed()
    composeRule.onNodeWithText("45 minutes").assertIsDisplayed()
  }

  @Test
  fun timerSettingsScreen_showsSectionedChimeRearmAndResumeRewindControls() {
    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModelWith(null, rearmEnabled = true, fadeEnabled = true),
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("FADE OUT").assertIsDisplayed()
    composeRule.onNodeWithText("Chime at fade start").assertIsDisplayed()
    composeRule.onNodeWithText("Fade chime volume").assertIsDisplayed()

    composeRule.onNodeWithText("RE-ARM").assertIsDisplayed()
    composeRule.onNodeWithText("Re-arm").assertIsDisplayed()
    composeRule.onNodeWithText("Via headphone button").assertIsDisplayed()
    composeRule.onNodeWithText("Via shake").assertIsDisplayed()
    composeRule.onNodeWithText("Chime on re-arm").assertIsDisplayed()
    composeRule.onNodeWithText("Re-arm chime volume").assertIsDisplayed()
    composeRule.onNodeWithText("Re-arm extends by").assertIsDisplayed()
    composeRule.onNodeWithText("Extension amount").assertIsDisplayed()

    composeRule.onNodeWithText("RESUME REWIND").assertIsDisplayed()
    composeRule.onNodeWithText("Resume within").assertIsDisplayed()
    composeRule.onNodeWithText("Quick rewind").assertIsDisplayed()
    composeRule.onNodeWithText("After that, rewind by").assertIsDisplayed()
    composeRule.onNodeWithText("Long rewind amount").assertIsDisplayed()

    composeRule.onNodeWithText("DEFAULT TIMER").assertIsDisplayed()
    composeRule.onNodeWithText("Only during these hours").assertIsDisplayed()
    composeRule.onNodeWithText("Starts at").assertIsDisplayed()
    composeRule.onNodeWithText("Ends at").assertIsDisplayed()
  }

  @Test
  fun timerSettingsScreen_togglingShakeRearmUpdatesViewModel() {
    val viewModel = viewModelWith(null, rearmEnabled = true, fadeEnabled = true)

    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModel,
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Via shake").performClick()

    verify { viewModel.preferSleepTimerRearmViaShake(false) }
  }

  @Test
  fun timerSettingsScreen_togglingChimeOnRearmUpdatesViewModel() {
    val viewModel = viewModelWith(null, rearmEnabled = true, fadeEnabled = true)

    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModel,
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Chime on re-arm").performClick()

    verify { viewModel.preferSleepTimerChimeOnRearm(false) }
  }

  @Test
  fun timerSettingsScreen_pickingMatchTimerExtensionModeUpdatesViewModel() {
    val viewModel = viewModelWith(null, rearmEnabled = true, fadeEnabled = true)

    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModel,
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Re-arm extends by").performClick()
    composeRule.onNodeWithText("The timer's original length").performClick()

    verify { viewModel.preferSleepTimerRearmExtensionMode(RearmExtensionMode.MATCH_TIMER_DURATION) }
  }

  @Test
  fun timerSettingsScreen_pickingMatchExtensionLongRewindModeUpdatesViewModel() {
    val viewModel = viewModelWith(null)

    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModel,
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("After that, rewind by").performClick()
    composeRule.onNodeWithText("The re-arm extension amount").performClick()

    verify { viewModel.preferSleepTimerResumeRewindLongMode(ResumeRewindLongMode.MATCH_EXTENSION) }
  }

  @Test
  fun timerSettingsScreen_pickingFadeChimeVolumePresetUpdatesViewModel() {
    val viewModel = viewModelWith(null, rearmEnabled = true, fadeEnabled = true)

    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModel,
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Fade chime volume").performClick()
    composeRule.onNodeWithText("75").performClick()

    verify { viewModel.preferSleepTimerChimeFadeVolume(75) }
  }

  @Test
  fun timerSettingsScreen_togglingDefaultTimerScheduleUpdatesViewModel() {
    val viewModel = viewModelWith(DurationTimerOption(45))

    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModel,
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Only during these hours").performClick()

    verify { viewModel.preferSleepTimerDefaultScheduleEnabled(true) }
  }

  @Test
  fun timerSettingsScreen_pickingPresetInSheetSavesDefaultTimerOption() {
    val viewModel = viewModelWith(null)

    composeRule.setContent {
      SleepTimerSettingsScreenContent(
        viewModel = viewModel,
        libraryType = LibraryType.LIBRARY,
        onBack = {},
      )
    }

    composeRule.onNodeWithText("Default sleep timer while playing").performClick()

    composeRule.waitUntilAtLeastOneExists(
      matcher = hasText("Sleep Timer"),
      timeoutMillis = WAIT_MS,
    )

    composeRule
      .onNode(hasText("15") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      .performClick()

    verify {
      viewModel.saveDefaultTimerOption(
        withArg { option -> assertTrue(option is DurationTimerOption && option.duration == 15) },
      )
    }
  }

  private companion object {
    const val WAIT_MS = 10_000L
  }
}

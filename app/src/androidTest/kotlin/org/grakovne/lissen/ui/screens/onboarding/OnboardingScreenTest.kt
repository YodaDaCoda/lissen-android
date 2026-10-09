package org.grakovne.lissen.ui.screens.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.viewmodel.OnboardingViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun continueMarksTheWizardCompletedAndFinishes() {
    val viewModel = mockk<OnboardingViewModel>(relaxed = true)
    var finished = 0

    composeRule.setContent { OnboardingScreen(onFinished = { finished++ }, viewModel = viewModel) }

    composeRule.onNodeWithTag("onboardingContinueButton").assertIsDisplayed().performClick()

    verify(exactly = 1) { viewModel.complete() }
    assertEquals(1, finished)
  }
}

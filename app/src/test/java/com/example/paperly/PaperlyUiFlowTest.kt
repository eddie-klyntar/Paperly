package com.example.paperly

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaperlyUiFlowTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `first launch onboarding shows three options and skip reaches dashboard`() {
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Paperly").assertIsDisplayed()
    composeRule.onNodeWithText("Your documents. Your workspace.").assertIsDisplayed()
    composeRule.onNodeWithTag("onboarding_explore_themes").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("onboarding_create_theme").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("onboarding_skip").performScrollTo().assertIsDisplayed()

    composeRule.onNodeWithTag("onboarding_skip").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("home_open_file_button").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_pdf_reader").assertIsDisplayed()
  }
}

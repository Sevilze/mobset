package com.mobset.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mobset.ui.screen.ProfileScreen
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun showsUserEmailAndSignOut() {
        composeRule.setContent {
            ProfileScreen(displayName = "Test User", email = "user@example.com")
        }
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Email: user@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").assertIsDisplayed()
    }
}


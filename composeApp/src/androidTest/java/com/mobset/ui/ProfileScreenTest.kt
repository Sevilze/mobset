package com.mobset.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mobset.MainActivity
import com.mobset.data.auth.AuthUser
import com.mobset.di.TestAuthState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ProfileScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun setup() {
        hiltRule.inject()
    }

    @Test
    fun profileShowsIdentityAndActions() {
        TestAuthState.setUser(AuthUser("u1", "User One", "u1@example.com", null))
        // Navigate to Profile tab
        composeRule.onNodeWithText("Profile").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("User One").assertIsDisplayed()
        composeRule.onNodeWithText("u1@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("View Detailed History").assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").assertIsDisplayed()
    }
}

package com.mobset.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mobset.MainActivity
import com.mobset.data.auth.AuthUser
import com.mobset.di.TestAuthState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class AuthGatingTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()


    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun showsSignInWhenSignedOut() {
        TestAuthState.setUser(null)
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
    }

    @Test
    fun showsProfileWhenSignedIn() {
        TestAuthState.setUser(AuthUser("u1", "User One", "u1@example.com", null))
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").assertIsDisplayed()
    }
}


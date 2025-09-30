package com.mobset

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

import com.mobset.theme.AppTheme
import com.mobset.ui.viewmodel.AppSettingsViewModel

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settingsVm: AppSettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val dynamic by settingsVm.dynamicColorEnabled.collectAsState()
            val seed by settingsVm.seedColor.collectAsState()
            val cardColors by settingsVm.cardColors.collectAsState()
            AppTheme(dynamicColor = dynamic, seedColor = seed, cardColors = cardColors) {
                SetApp()
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    AppTheme { SetApp() }
}
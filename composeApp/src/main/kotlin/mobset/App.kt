package mobset

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import mobset.theme.AppTheme
import mobset.ui.screen.HomeScreen

@Composable
fun App() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold { paddingValues ->
                HomeScreen(
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
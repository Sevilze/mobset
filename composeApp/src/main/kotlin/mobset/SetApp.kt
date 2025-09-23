package mobset

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview
import mobset.theme.AppTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun SetApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen() }
    }
}

@Composable
fun HomeScreen() {
    Text("Set Mobile - Hello Compose")
}

@Preview
@Composable
fun PreviewHome() {
    AppTheme { HomeScreen() }
}


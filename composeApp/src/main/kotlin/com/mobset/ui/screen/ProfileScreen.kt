package com.mobset.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Profile screen: user info, stats, and settings
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    displayName: String? = null,
    email: String? = null,
    onSignOut: () -> Unit = {}
) {
    var dynamicColor by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }
    var animations by remember { mutableStateOf(true) }

    var syncing by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display name: ${displayName ?: "-"}", style = MaterialTheme.typography.titleMedium)
                Text("Email: ${email ?: "-"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = onSignOut) { Text("Sign out") }
                    Button(onClick = { syncing = !syncing }) { Text(if (syncing) "Stop sync" else "Sync now") }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Statistics", style = MaterialTheme.typography.titleMedium)
                Text("Games played: 42")
                Text("Best time: 02:31")
                Text("Sets found: 128")
                if (syncing) {
                    Spacer(Modifier.height(8.dp))
                    // Expressive loading indicator to reflect sync progress pattern
                    androidx.compose.material3.ContainedLoadingIndicator(
                        modifier = Modifier.size(56.dp)
                    )
                    Text("Syncing profile…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dynamic color")
                    Switch(checked = dynamicColor, onCheckedChange = { dynamicColor = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Haptics")
                    Switch(checked = haptics, onCheckedChange = { haptics = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Animations")
                    Switch(checked = animations, onCheckedChange = { animations = it })
                }
            }
        }
    }
}


package com.mobset.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Friends screen: add friends and quick-invite to a room (UI-only)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(modifier: Modifier = Modifier) {
    val friends = remember { mutableStateListOf("alice@example.com", "bob@example.com") }
    var email by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.GroupAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Friends", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Friend's email or ID") }
            )
            Button(enabled = email.isNotBlank(), onClick = {
                friends.add(email.trim())
                email = ""
            }) { Text("Add") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(friends) { f ->
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(f, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { /* TODO: invite */ }) { Text("Invite") }
                    }
                }
            }
        }
    }
}


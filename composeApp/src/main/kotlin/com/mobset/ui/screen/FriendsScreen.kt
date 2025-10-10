package com.mobset.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.mobset.ui.viewmodel.FriendsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    onJoinRoom: (String) -> Unit = {}
) {
    val vm: FriendsViewModel = hiltViewModel()
    val friends by vm.friendsList.collectAsState()
    val profiles by vm.friendProfiles.collectAsState()
    val incoming by vm.incomingRequests.collectAsState()
    val invites by vm.roomInvites.collectAsState()
    val incomingProfiles by vm.incomingRequestProfiles.collectAsState()
    val inviteProfiles by vm.inviteProfiles.collectAsState()

    var query by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Room invites snackbar
    LaunchedEffect(invites, inviteProfiles) {
        invites.firstOrNull()?.let { inv ->
            val name = inviteProfiles[inv.fromUid]?.displayName ?: inv.fromUid
            val res = snackbarHostState.showSnackbar(
                message = "Room invite from ${name}",
                actionLabel = "Join",
                withDismissAction = true
            )
            if (res == SnackbarResult.ActionPerformed) onJoinRoom(inv.roomId)
            // best-effort clear on action or dismiss
            vm.clearInvite(inv.id)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GroupAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Friends", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                var inboxExpanded by remember { mutableStateOf(false) }
                BadgedBox(badge = {
                    if (incoming.isNotEmpty()) Badge { Text(incoming.size.toString()) }
                }) {
                    ElevatedButton(onClick = { inboxExpanded = true }) { Text("Inbox") }
                }
                DropdownMenu(expanded = inboxExpanded, onDismissRequest = { inboxExpanded = false }) {
                    if (incoming.isEmpty()) DropdownMenuItem(text = { Text("No requests") }, onClick = { inboxExpanded = false })
                    incoming.forEach { req ->
                        DropdownMenuItem(
                            text = { Text("Friend request from ${incomingProfiles[req.fromUid]?.displayName ?: req.fromUid}") },
                            onClick = { /* no-op */ },
                            trailingIcon = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { vm.accept(req); inboxExpanded = false }) { Text("Accept") }
                                    TextButton(onClick = { vm.decline(req.id); inboxExpanded = false }) { Text("Decline") }
                                }
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Friend's email or ID") },
                    singleLine = true
                )
                Button(enabled = query.isNotBlank(), onClick = {
                    val q = query.trim()
                    if ("@" in q) vm.addFriendByEmail(q) else vm.addFriendByUid(q)
                    query = ""
                }) { Text("Add") }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(friends, key = { it.uid }) { f ->
                    val p = profiles[f.uid]
                    FriendCard(
                        displayName = (p?.displayName ?: f.uid).take(16),
                        email = p?.email ?: "",
                        photoUrl = p?.photoUrl,
                        online = p?.isOnline == true,
                        onInvite = { vm.invite(f.uid) },
                        inviteEnabled = vm.currentRoomId.collectAsState().value != null
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendCard(
    displayName: String,
    email: String,
    photoUrl: String?,
    online: Boolean,
    onInvite: () -> Unit,
    inviteEnabled: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(photoUrl),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(if (online) Color(0xFF2ECC71) else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                if (email.isNotBlank()) Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onInvite, enabled = inviteEnabled) { Text("Invite") }
        }
    }
}

@Preview
@Composable
private fun FriendCardPreview() {
    FriendCard("Alice", "alice@example.com", null, online = true, onInvite = {}, inviteEnabled = true)
}


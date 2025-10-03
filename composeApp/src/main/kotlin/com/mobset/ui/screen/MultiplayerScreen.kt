package com.mobset.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator

import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobset.data.rooms.Access
import com.mobset.data.rooms.RoomSummary
import com.mobset.ui.viewmodel.RoomsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LobbyContent(
    rooms: List<RoomSummary>,
    hostNames: Map<String, String>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCreatePublic: (String, String) -> Unit,
    onCreatePassword: (String, String, String) -> Unit,
    onOpenRoom: (String) -> Unit,
) {
    val allowedModes = listOf(
        "normal", "ultraset"
    )
    var roomName by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(allowedModes.first()) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var modeSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Multiplayer", fontWeight = FontWeight.Bold) },
                navigationIcon = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                actions = { }
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    ) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            modifier = Modifier.padding(padding),
            state = pullState,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            indicator = {
                PullToRefreshDefaults.IndicatorBox(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    if (isRefreshing) {
                        CircularWavyProgressIndicator()
                    } else {
                        CircularWavyProgressIndicator(progress = { pullState.distanceFraction })
                    }
                }
            }
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = roomName,
                    onValueChange = { roomName = it.take(50) },
                    label = { Text("Room name") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { modeSheet = true }) { Text("Mode: ${mode}") }
            }
            if (modeSheet) {
                AlertDialog(
                    onDismissRequest = { modeSheet = false },
                    confirmButton = {
                        TextButton(onClick = { modeSheet = false }) { Text("Done") }
                    },
                    title = { Text("Select mode") },
                    text = {
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(allowedModes) { m ->
                                ListItem(
                                    headlineContent = { Text(m) },
                                    trailingContent = { RadioButton(selected = (m == mode), onClick = { mode = m }) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = usePassword, onClick = { usePassword = !usePassword }, label = { Text("Password") })
                if (usePassword) {
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    enabled = roomName.isNotBlank(),
                    onClick = {
                        if (usePassword) onCreatePassword(roomName.trim(), mode, password) else onCreatePublic(roomName.trim(), mode)
                    }
                ) { Text("Create") }
            }

            Text(text = "Public Rooms", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(rooms, key = { it.id }) { r ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.name.ifBlank { r.id }, style = MaterialTheme.typography.titleMedium)
                                val host = hostNames[r.hostId] ?: r.hostName ?: r.hostId
                                Text("Host: ${host} • Mode: ${r.mode} • Players: ${r.playerCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { onOpenRoom(r.id) }) { Text("Join") }
                        }
                    }
                }
            }
        }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplayerScreen(
    modifier: Modifier = Modifier,
    onOpenRoom: (String) -> Unit = {},
) {
    val vm: RoomsViewModel = hiltViewModel()
    val rooms by vm.publicRooms.collectAsState()
    val hostNames by vm.hostNames.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    LobbyContent(
        rooms = rooms,
        hostNames = hostNames,
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        onCreatePublic = { name, m -> vm.createPublicRoom(name, m) },
        onCreatePassword = { name, m, pass -> vm.createPasswordRoom(name, m, pass) },
        onOpenRoom = onOpenRoom,
    )
}

@Preview
@Composable
private fun MultiplayerScreenPreview() {
    LobbyContent(
        rooms = listOf(
            RoomSummary(id = "Alpha", name = "Alpha", hostId = "u1", access = Access.PUBLIC, mode = "normal", createdAt = 0, playerCount = 2),
            RoomSummary(id = "Bravo", name = "Bravo", hostId = "u2", access = Access.PASSWORD, mode = "ultra9", createdAt = 0, playerCount = 4),
        ),
        hostNames = mapOf("u1" to "Alice", "u2" to "Bob"),
        isRefreshing = false,
        onRefresh = {},
        onCreatePublic = { _, _ -> },
        onCreatePassword = { _, _, _ -> },
        onOpenRoom = {},
    )
}

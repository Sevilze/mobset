package com.mobset.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobset.ui.viewmodel.RoomViewModel

@Composable
private fun RoomContent(
    users: List<String>,
    messages: List<Pair<String, String>>,
    currentMode: String?,
    canChangeMode: Boolean,
    onChangeMode: ((String) -> Unit)?,
    onLeave: () -> Unit,
    onSend: (String) -> Unit,
    onStart: (() -> Unit)? = null,
    startEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var modeSheet by remember { mutableStateOf(false) }
    val allowedModes = listOf("normal", "ultraset")

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var scrollOnNextMine by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().padding(16.dp)) {
        val isCompact = maxWidth < 600.dp

        @Composable
        fun ChatList(mod: Modifier) {
            BoxWithConstraints(mod) {
                LazyColumn(
                    state = listState,
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages) { m ->
                        Text("${m.first}: ${m.second}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Small overlay scrollbar using list state proportions
                val layoutInfo = listState.layoutInfo
                val total = layoutInfo.totalItemsCount.coerceAtLeast(1)
                val visible = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val barRatio = (visible.toFloat() / total.toFloat()).coerceIn(0.05f, 1f)
                val firstIndex = listState.firstVisibleItemIndex
                val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
                val itemSize = (firstItem?.size ?: 1).toFloat()
                val offsetPx = kotlin.math.abs((firstItem?.offset ?: 0).toFloat())
                val offsetItems = if (itemSize > 0f) offsetPx / itemSize else 0f
                val progress = ((firstIndex.toFloat() + offsetItems) / total.toFloat()).coerceIn(
                    0f,
                    1f
                )

                val barHeight = maxHeight * barRatio
                val barOffsetY = (maxHeight - barHeight) * progress

                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(barHeight)
                            .align(Alignment.TopEnd)
                            .offset(y = barOffsetY)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    )
                }
            }
        }

        LaunchedEffect(messages) {
            if (scrollOnNextMine && messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
                scrollOnNextMine = false
            }
        }

        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Room Chat", style = MaterialTheme.typography.titleMedium)
                ElevatedCard(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    ChatList(Modifier.fillMaxWidth().heightIn(min = 160.dp))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message…") }
                    )
                    Button(
                        enabled = input.isNotBlank(),
                        onClick = {
                            scrollOnNextMine = true
                            onSend(input.trim())
                            input = ""
                        }
                    ) { Text("Send") }
                }
                Text("Players", style = MaterialTheme.typography.titleMedium)
                ElevatedCard(Modifier.fillMaxWidth()) {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        items(users) { name ->
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onLeave) { Text("Leave") }
                    OutlinedButton(onClick = {
                        modeSheet = true
                    }, enabled = canChangeMode) { Text("Mode: ${currentMode ?: "-"}") }
                    Button(onClick = {
                        onStart?.invoke()
                    }, enabled = startEnabled) { Text("Start game") }
                }
                if (modeSheet && canChangeMode && onChangeMode != null) {
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
                                        headlineContent = {
                                            Text(m)
                                        },
                                        trailingContent = {
                                            RadioButton(selected = (m == currentMode), onClick = {
                                                onChangeMode(m)
                                                modeSheet =
                                                    false
                                            })
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Players", style = MaterialTheme.typography.titleMedium)
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            items(users) { name ->
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = onLeave) { Text("Leave") }
                        OutlinedButton(onClick = {
                            modeSheet = true
                        }, enabled = canChangeMode) { Text("Mode: ${currentMode ?: "-"}") }
                        Button(onClick = {
                            onStart?.invoke()
                        }, enabled = startEnabled) { Text("Start game") }
                    }
                    if (modeSheet && canChangeMode && onChangeMode != null) {
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
                                            headlineContent = {
                                                Text(m)
                                            },
                                            trailingContent = {
                                                RadioButton(
                                                    selected = (m == currentMode),
                                                    onClick = {
                                                        onChangeMode(m)
                                                        modeSheet =
                                                            false
                                                    }
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Room Chat", style = MaterialTheme.typography.titleMedium)
                    ElevatedCard(Modifier.fillMaxWidth().weight(1f)) {
                        ChatList(Modifier.fillMaxSize())
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message…") }
                        )
                        Button(
                            enabled = input.isNotBlank(),
                            onClick = {
                                scrollOnNextMine = true
                                onSend(input.trim())
                                input = ""
                            }
                        ) { Text("Send") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoomScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onNavigateToGame: (String) -> Unit = {}
) {
    val vm: RoomViewModel = hiltViewModel()
    LaunchedEffect(roomId) { vm.setRoom(roomId) }

    val state by vm.room.collectAsState()
    val messages by vm.messages.collectAsState()
    val currentUser by vm.currentUser.collectAsState(initial = null)
    val names by vm.userNames.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var pendingJoin by remember { mutableStateOf(false) }
    var passwordText by remember { mutableStateOf("") }

    val users =
        state
            ?.users
            ?.keys
            ?.toList()
            .orEmpty()
    val isHost = currentUser?.uid != null && currentUser?.uid == state?.hostId
    val canStartNext by vm.canStartNext.collectAsState()
    val canStart = isHost && canStartNext

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = {
                Text("Waiting Room: ${state?.name ?: roomId}", fontWeight = FontWeight.Bold)
            },
            navigationIcon = { /* back handled by onNavigateBack outside */ },
            actions = {
                if (canStart) {
                    IconButton(onClick = {
                        vm.disband()
                        onNavigateBack()
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Disband room") }
                }
            }
        )
    }) { padding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            modifier = Modifier.padding(padding),
            state = pullState,
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            indicator = {
                PullToRefreshDefaults.IndicatorBox(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    if (refreshing) {
                        CircularWavyProgressIndicator()
                    } else {
                        CircularWavyProgressIndicator(progress = { pullState.distanceFraction })
                    }
                }
            }
        ) {
            RoomContent(
                users = users.map { uid -> names[uid] ?: "Unknown" },
                messages = messages.map { ((names[it.userId] ?: "Unknown")) to it.message },
                currentMode = state?.mode,
                canChangeMode = (isHost && (state?.status?.name?.equals("WAITING", true) == true)),
                onChangeMode = ({ m -> vm.updateMode(m) }),
                onLeave = {
                    vm.leave()
                    onNavigateBack()
                },
                onSend = { vm.send(it) },
                onStart = ({ vm.start() }),
                startEnabled = canStart
            )
        }

        // Auto-join behavior: if public and not a member, join; if password, prompt.
        LaunchedEffect(state?.id, currentUser?.uid) {
            val s = state ?: return@LaunchedEffect
            val uid = currentUser?.uid ?: return@LaunchedEffect
            val isMember = s.users.containsKey(uid)
            if (!isMember) {
                when (s.access.name.lowercase()) {
                    "public" -> {
                        if (!pendingJoin) {
                            pendingJoin = true
                            vm.join(null)
                            pendingJoin = false
                        }
                    }
                    "password" -> {
                        // Try whitelist-based join first; if still not a member shortly after, prompt for password
                        if (!pendingJoin) {
                            pendingJoin = true
                            vm.join(null)
                            pendingJoin = false
                        }
                        kotlinx.coroutines.delay(350)
                        val stillMember = state?.users?.containsKey(uid) == true
                        if (!stillMember) showPassword = true
                    }
                }
            }
        }

        if (showPassword) {
            AlertDialog(
                onDismissRequest = { showPassword = false },
                confirmButton = {
                    TextButton(onClick = {
                        val pass = passwordText.trim()
                        if (pass.isNotEmpty()) {
                            pendingJoin = true
                            vm.join(pass)
                            pendingJoin = false
                            passwordText = ""
                            showPassword = false
                        }
                    }) { Text("Join") }
                },
                title = { Text("Enter room password") },
                text = {
                    OutlinedTextField(value = passwordText, onValueChange = {
                        passwordText = it
                    }, placeholder = { Text("Password") })
                }
            )
        }
        // Navigate all current room members to multiplayer game when status flips to INGAME
        val membershipKey = state?.users?.get(currentUser?.uid ?: "")
        LaunchedEffect(state?.status, currentUser?.uid, membershipKey) {
            val s = state ?: return@LaunchedEffect
            val uid = currentUser?.uid ?: return@LaunchedEffect
            val isMember = s.users.containsKey(uid) || uid == s.hostId
            if (s.status.name.equals("INGAME", ignoreCase = true) && isMember) {
                onNavigateToGame(s.id)
            }
        }
    }
}

@Preview
@Composable
private fun RoomScreenPreview() {
    RoomContent(
        users = listOf("Alice (host)", "Bob", "Charlie"),
        messages = listOf("Bob" to "hi!", "Charlie" to "ready"),
        currentMode = "normal",
        canChangeMode = false,
        onChangeMode = null,
        onLeave = {},
        onSend = {}
    )
}

package com.mobset.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter

import com.mobset.ui.viewmodel.AppSettingsViewModel
import com.mobset.ui.viewmodel.ProfileViewModel
import com.mobset.ui.util.formatElapsedTimeMs

import com.mobset.data.history.AggregatedPlayerStats
import com.mobset.data.history.PlayerMode
import com.mobset.data.history.GameModeType


/**
 * Profile screen: user info, stats, and settings
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onViewHistory: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var dynamicColor by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }
    var animations by remember { mutableStateOf(true) }

    var syncing by remember { mutableStateOf(false) }

    val vm: ProfileViewModel = hiltViewModel()
    val user by vm.currentUser.collectAsState()
    val profile by vm.currentProfile.collectAsState()
    val filters by vm.filters.collectAsState()
    val stats = vm.aggregatedStats.collectAsState().value
    val winLoss by vm.winLoss.collectAsState(initial = ProfileViewModel.WinLoss(0, 0))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with avatar and centered identity
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            var showEdit by remember { mutableStateOf(false) }
            var editName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = rememberAsyncImagePainter(user?.photoUrl),
                    contentDescription = "Profile image",
                    modifier = Modifier
                        .size(256.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(12.dp))
                run {
                    var textWidthPx by remember { mutableStateOf(0) }
                    var iconWidthPx: Double by remember { mutableStateOf( value = 0.0) }
                    val density = LocalDensity.current
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            profile?.displayName ?: user?.displayName ?: "-",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center),
                            onTextLayout = { textWidthPx = it.size.width }
                        )
                        IconButton(
                            onClick = { showEdit = true },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .onGloballyPositioned { iconWidthPx = it.size.width.toDouble() }
                                .offset {
                                    IntOffset(
                                        ((textWidthPx / 2 + iconWidthPx / 4 + with(density) { 8.dp.roundToPx() }).toInt()),
                                        0
                                    )
                                }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit name")
                        }
                    }
                }
                Text(
                    user?.email ?: "-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showEdit) {
                AlertDialog(
                    onDismissRequest = { showEdit = false },
                    title = { Text("Edit display name") },
                    text = {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (editName.isNotBlank()) vm.updateDisplayName(editName)
                                showEdit = false
                            },
                            enabled = editName.isNotBlank() && editName != (profile?.displayName
                                ?: "")
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEdit = false }) { Text("Cancel") }
                    }
                )
            }
        }

        // Filters card
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Filters", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GameModeFilter(current = filters.gameMode, onSelect = vm::setGameMode)
                    PlayerModeFilter(current = filters.playerMode, onSelect = vm::setPlayerMode)
                }
            }
        }

        // Statistics card
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Statistics", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onViewHistory) { Text("View detailed history") }
                }
                PieChart(win = winLoss.wins, loss = winLoss.losses)
                StatsGrid(stats)
                if (syncing) {
                    Spacer(Modifier.height(8.dp))
                    ContainedLoadingIndicator(Modifier.size(56.dp))
                    Text("Syncing profile…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            val settingsVm: AppSettingsViewModel = hiltViewModel()
            val dynamic by settingsVm.dynamicColorEnabled.collectAsState()
            val seed by settingsVm.seedColor.collectAsState()
            val cardColors by settingsVm.cardColors.collectAsState()
            var colorsExpanded by remember { mutableStateOf(false) }
            var themeExpanded by remember { mutableStateOf(false) }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)

                // Card Colors (expandable)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Card colors")
                    IconButton(onClick = { colorsExpanded = !colorsExpanded }) {
                        Icon(
                            if (colorsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
                AnimatedVisibility(visible = colorsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var editingIndex by remember { mutableStateOf<Int?>(null) }
                        repeat(3) { idx ->
                            val current = cardColors.getOrNull(idx) ?: Color(0xFF000000)
                            var hex by remember(current) {
                                mutableStateOf(
                                    "#%02X%02X%02X".format(
                                        (current.red * 255).toInt(),
                                        (current.green * 255).toInt(),
                                        (current.blue * 255).toInt()
                                    )
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(current)
                                        .clickable { editingIndex = idx }
                                )
                                OutlinedTextField(
                                    value = hex,
                                    onValueChange = { value ->
                                        hex = value
                                        if (value.length >= 7) settingsVm.setCardColor(idx, value)
                                    },
                                    label = { Text("Hex #RRGGBB") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { settingsVm.applySplitComplementaryFromFirst() },
                                modifier = Modifier.weight(2f)
                            ) { Text("Find Split Complementary") }
                            Button(
                                onClick = { settingsVm.applyTriadicFromFirst() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Find Triadic") }
                        }

                        if (editingIndex != null) {
                            val idx = editingIndex!!
                            val start = cardColors.getOrNull(idx) ?: Color(0xFF000000)
                            var r by remember(start) { mutableStateOf((start.red * 255).toInt()) }
                            var g by remember(start) { mutableStateOf((start.green * 255).toInt()) }
                            var b by remember(start) { mutableStateOf((start.blue * 255).toInt()) }
                            AlertDialog(
                                onDismissRequest = { editingIndex = null },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val hx = "#%02X%02X%02X".format(r, g, b)
                                        settingsVm.setCardColor(idx, hx)
                                        editingIndex = null
                                    }) { Text("Done") }
                                },
                                title = { Text("Adjust RGB") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(r / 255f, g / 255f, b / 255f))
                                            )
                                            Text("#%02X%02X%02X".format(r, g, b))
                                        }
                                        Text("R: $r")
                                        Slider(
                                            value = r.toFloat(),
                                            onValueChange = { r = it.toInt().coerceIn(0, 255) },
                                            valueRange = 0f..255f
                                        )
                                        Text("G: $g")
                                        Slider(
                                            value = g.toFloat(),
                                            onValueChange = { g = it.toInt().coerceIn(0, 255) },
                                            valueRange = 0f..255f
                                        )
                                        Text("B: $b")
                                        Slider(
                                            value = b.toFloat(),
                                            onValueChange = { b = it.toInt().coerceIn(0, 255) },
                                            valueRange = 0f..255f
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 1f)
                )

                // App Theme (expandable)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("App theme")
                    IconButton(onClick = { themeExpanded = !themeExpanded }) {
                        Icon(
                            if (themeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
                AnimatedVisibility(visible = themeExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Use M3 dynamic color (wallpaper)", modifier = Modifier.weight(1f))
                            Switch(
                                checked = dynamic,
                                onCheckedChange = { settingsVm.setDynamicColor(it) })
                        }
                        Text("Accent templates")
                        val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 1f)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val templates =
                                listOf("red", "blue", "green", "yellow", "purple", "orange", "teal")
                            templates.forEach { t ->
                                Box(
                                    // modifier = Modifier.border(1.dp, outlineColor, shape = RoundedCornerShape(8.dp))
                                ) {
                                    AssistChip(
                                        onClick = { settingsVm.setAccentTemplate(t) },
                                        label = { Text(t.replaceFirstChar { it.uppercase() }) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                        Text("Custom accent (hex)")
                        var customHex by remember(seed) {
                            mutableStateOf(
                                "#%02X%02X%02X".format(
                                    (seed.red * 255).toInt(),
                                    (seed.green * 255).toInt(),
                                    (seed.blue * 255).toInt()
                                )
                            )
                        }
                        OutlinedTextField(
                            value = customHex,
                            onValueChange = { value ->
                                customHex = value
                                if (value.length >= 7) settingsVm.setCustomAccentHex(value)
                            },
                            label = { Text("Hex #RRGGBB") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameModeFilter(current: GameModeType?, onSelect: (GameModeType?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = current?.name ?: "All Modes"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        TextField(
            readOnly = true,
            value = label,
            onValueChange = {},
            label = { Text("Game Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .width(160.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            DropdownMenuItem(
                text = { Text("Normal") },
                onClick = {
                    onSelect(GameModeType.NORMAL); expanded = false
                })
            DropdownMenuItem(
                text = { Text("Ultra") },
                onClick = {
                    onSelect(GameModeType.ULTRA); expanded = false
                })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerModeFilter(current: PlayerMode?, onSelect: (PlayerMode?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = current?.name ?: "All Players"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        TextField(
            readOnly = true,
            value = label,
            onValueChange = {},
            label = { Text("Player Mode") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .width(180.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            DropdownMenuItem(
                text = { Text("Solo") },
                onClick = { onSelect(PlayerMode.SOLO); expanded = false })
            DropdownMenuItem(
                text = { Text("Multiplayer") },
                onClick = { onSelect(PlayerMode.MULTIPLAYER); expanded = false })
        }
    }
}

@Composable
private fun PieChart(win: Int, loss: Int) {
    val total = (win + loss)
    val winColor = MaterialTheme.colorScheme.primary
    val lossColor = MaterialTheme.colorScheme.secondary

    Canvas(Modifier
        .fillMaxWidth()
        .height(140.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f
        if (total == 0) {
            // Show a full circle using secondary color when there is no data
            drawCircle(
                color = lossColor,
                radius = radius,
                center = Offset(cx, cy)
            )
            return@Canvas
        }
        val winSweep = 360f * (win.toFloat() / total.toFloat())
        val lossSweep = 360f - winSweep
        if (loss == 0) {
            drawCircle(
                color = winColor,
                radius = radius,
                center = Offset(cx, cy)
            )
        } else if (win == 0) {
            drawCircle(
                color = lossColor,
                radius = radius,
                center = Offset(cx, cy)
            )
        } else {
            drawArc(
                color = winColor,
                startAngle = -90f,
                sweepAngle = winSweep,
                useCenter = true,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f)
            )
            drawArc(
                color = lossColor,
                startAngle = -90f + winSweep,
                sweepAngle = lossSweep,
                useCenter = true,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f)
            )
        }
    }
}

@Composable
private fun StatsGrid(stats: AggregatedPlayerStats?) {
    val s = stats
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Finished games", s?.finishedGames?.toString() ?: "-", Modifier.weight(1f))
            StatCard("Total sets", s?.totalSetsFound?.toString() ?: "-", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                "Avg sets/game",
                s?.averageSetsPerGame?.let { String.format("%.2f", it) } ?: "-",
                Modifier.weight(1f))
            StatCard(
                "Fastest win",
                s?.fastestGameWonMs?.let { formatElapsedTimeMs(it) } ?: "-",
                Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                "Avg length",
                s?.averageGameLengthMs?.let { formatElapsedTimeMs(it) } ?: "-",
                Modifier.weight(1f))
            StatCard("Rating", s?.rating?.toString() ?: "N/A", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

@Preview
@Composable
private fun StatsGridPreview() {
    val stats = AggregatedPlayerStats(
        playerId = "demo",
        finishedGames = 12,
        totalSetsFound = 57,
        averageSetsPerGame = 4.75,
        fastestGameWonMs = 95_000,
        averageGameLengthMs = 180_000,
        rating = 1420
    )
    StatsGrid(stats)
}


@Preview
@Composable
private fun ProfileScreenPreview() {
    ProfileScreen()
}



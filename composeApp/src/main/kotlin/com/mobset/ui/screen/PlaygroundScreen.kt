package com.mobset.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.algorithm.UltraParts
import com.mobset.domain.model.Card as SetCardModel
import com.mobset.domain.model.CardColor
import com.mobset.domain.model.CardShade
import com.mobset.domain.model.CardShape
import com.mobset.domain.model.GameMode
import com.mobset.domain.model.SetType
import com.mobset.domain.model.getColor
import com.mobset.domain.model.getNumber
import com.mobset.domain.model.getShade
import com.mobset.domain.model.getShape
import com.mobset.theme.LocalCardColors
import com.mobset.ui.component.SetCard as SetCardComposable
import com.mobset.ui.component.SetCardDisplay
import kotlinx.coroutines.delay

/**
 * Playground: explore sample sets with filters and a mode selector.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun PlaygroundScreen(modifier: Modifier = Modifier) {
    var selectedModeIndex by rememberSaveable { mutableStateOf(0) }
    val selectedMode = if (selectedModeIndex == 0) GameMode.NORMAL else GameMode.ULTRA
    var loading by remember { mutableStateOf(false) }

    // Session-persistent base selections for both modes (store encodings)
    val baseSaver = androidx.compose.runtime.saveable.listSaver<List<SetCardModel?>, String?>(
        save = { list -> list.map { it?.encoding } },
        restore = { encs -> encs.map { it?.let { code -> SetCardModel(code) } } }
    )
    var baseNormal by rememberSaveable(stateSaver = baseSaver) {
        mutableStateOf(listOf<SetCardModel?>(null, null))
    }
    var baseUltra by rememberSaveable(stateSaver = baseSaver) {
        mutableStateOf(listOf<SetCardModel?>(null, null, null))
    }
    val baseCapacity = if (selectedMode == GameMode.NORMAL) 2 else 3
    val selectedBase: List<SetCardModel?> = if (selectedMode ==
        GameMode.NORMAL
    ) {
        baseNormal
    } else {
        baseUltra
    }

    var showUltraConjugate by rememberSaveable { mutableStateOf(true) }

    var visibleSamples by remember { mutableStateOf<List<List<SetCardModel>>>(emptyList()) }

    // Recompute results whenever mode or base selection changes
    LaunchedEffect(selectedMode, baseNormal, baseUltra) {
        loading = true
        val deck = SetAlgorithms.generateDeck(selectedMode)
        val activeBase = if (selectedMode == GameMode.NORMAL) baseNormal else baseUltra
        val seeds = activeBase.take(baseCapacity).filterNotNull()
        visibleSamples = SetAlgorithms.findSetsWithSeeds(deck, seeds, selectedMode)
        loading = false
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Playground",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            ButtonGroup {
                val options = listOf("Normal", "Ultra")
                options.forEachIndexed { index, label ->
                    ToggleButton(
                        checked = selectedModeIndex == index,
                        onCheckedChange = { selectedModeIndex = index },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ) {
                        Text(label)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (selectedMode ==
                GameMode.NORMAL
            ) {
                "Browse example normal sets"
            } else {
                "Browse example Ultra sets"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (selectedMode == GameMode.ULTRA) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show conjugate card")
                Switch(checked = showUltraConjugate, onCheckedChange = { showUltraConjugate = it })
            }
        }

        if (loading) {
            Spacer(Modifier.height(12.dp))
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(12.dp))
        BaseCardPicker(
            mode = selectedMode,
            slots = selectedBase.take(baseCapacity),
            onEdit = { idx, newCard ->
                if (selectedMode == GameMode.NORMAL) {
                    baseNormal = baseNormal.toMutableList().also { it[idx] = newCard }
                } else {
                    baseUltra = baseUltra.toMutableList().also { it[idx] = newCard }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        val hasSeed = selectedBase.any { it != null }
        if (!hasSeed) {
            Text(
                text = "Select at least one base card to show results.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        val rows = remember(visibleSamples) { visibleSamples.chunked(2) }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(rows.size) { rowIndex ->
                val row = rows[rowIndex]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { set ->
                        OutlinedCard(Modifier.weight(1f)) {
                            Box(Modifier.padding(8.dp)) {
                                if (selectedMode == GameMode.ULTRA) {
                                    UltraResultItem(cards = set, showConjugate = showUltraConjugate)
                                } else {
                                    NormalResultItem(cards = set)
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BaseCardPicker(
    mode: GameMode,
    slots: List<SetCardModel?>,
    onEdit: (Int, SetCardModel?) -> Unit
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingCurrent by remember { mutableStateOf<SetCardModel?>(null) }
    var boomIndex by remember { mutableStateOf<Int?>(null) }

    Row(
        Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        slots.forEachIndexed { index, card ->
            if (card == null) {
                PlusSlot(
                    onClick = {
                        editingIndex = index
                        editingCurrent = null
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                val isBoom = boomIndex == index
                val scale = if (isBoom) 0.9f else 1f
                // Use combinedClickable on a Box wrapping the non-clickable card display
                // to avoid gesture conflicts between parent and child click handlers
                Box(
                    Modifier
                        .weight(1f)
                        .scale(scale)
                        .combinedClickable(
                            onClick = {
                                editingIndex = index
                                editingCurrent = card
                            },
                            onDoubleClick = {
                                boomIndex = index
                            }
                        )
                ) {
                    // Use non-clickable SetCardDisplay to prevent gesture interference
                    SetCardDisplay(
                        card = card,
                        isSelected = editingIndex == index,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    boomIndex?.let { idx ->
        LaunchedEffect(idx) {
            // brief visual feedback
            delay(120)
            if (boomIndex == idx) {
                onEdit(idx, null)
                boomIndex = null
            }
        }
    }

    editingIndex?.let { idx ->
        CardPickerDialog(
            mode = mode,
            current = editingCurrent,
            existing = slots,
            index = idx,
            onAdd = { newCard ->
                onEdit(idx, newCard)
                editingIndex = null
                editingCurrent = null
            },
            onClear = {
                onEdit(idx, null)
                editingIndex = null
                editingCurrent = null
            },
            onCancel = {
                editingIndex = null
                editingCurrent = null
            }
        )
    }
}

@Composable
private fun PlusSlot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.aspectRatio(1.6f)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("+")
        }
    }
}

@Composable
private fun CardPickerDialog(
    mode: GameMode,
    current: SetCardModel?,
    existing: List<SetCardModel?>,
    index: Int,
    onAdd: (SetCardModel) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit
) {
    var color by remember(current) { mutableStateOf(current?.color ?: 0) }
    var shape by remember(current) { mutableStateOf(current?.shape ?: 0) }
    var shade by remember(current) { mutableStateOf(current?.shade ?: 0) }
    var number by remember(current) { mutableStateOf((current?.number ?: 0)) }
    val vColor = (mode.traitVariants.getOrNull(0) ?: 3) - 1
    val vShape = (mode.traitVariants.getOrNull(1) ?: 3) - 1
    val vShade = (mode.traitVariants.getOrNull(2) ?: 3) - 1
    val vNumber = (mode.traitVariants.getOrNull(3) ?: 3) - 1
    val preview =
        remember(color, shape, shade, number) {
            SetCardModel("" + color + shape + shade + number)
        }
    val isDuplicate = remember(preview, existing, index) {
        existing.withIndex().any { (i, c) -> i != index && c?.encoding == preview.encoding }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = { onAdd(preview) }, enabled = !isDuplicate) { Text("Add") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
        title = { Text("Pick card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetCardComposable(card = preview, isSelected = false, onClick = {
                }, modifier = Modifier.fillMaxWidth())

                run {
                    val palette = LocalCardColors.current
                    val actual =
                        palette.getOrNull(color) ?: palette.lastOrNull() ?: Color(0xFFFF0101)
                    val r = (actual.red * 255).toInt()
                    val g = (actual.green * 255).toInt()
                    val b = (actual.blue * 255).toInt()
                    val hex = "#%02X%02X%02X".format(r, g, b)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(20.dp).background(actual, CircleShape))
                        Text("RGB: $r, $g, $b  ($hex)")
                    }
                }
                Slider(value = color.toFloat(), onValueChange = {
                    color =
                        it.toInt().coerceIn(0, vColor)
                }, valueRange = 0f..vColor.toFloat())
                Text("Shape: ${CardShape.fromValue(shape).name}")
                Slider(value = shape.toFloat(), onValueChange = {
                    shape =
                        it.toInt().coerceIn(0, vShape)
                }, valueRange = 0f..vShape.toFloat())
                Text("Shade: ${CardShade.fromValue(shade).name}")
                Slider(value = shade.toFloat(), onValueChange = {
                    shade =
                        it.toInt().coerceIn(0, vShade)
                }, valueRange = 0f..vShade.toFloat())
                Text("Count: ${number + 1}")
                Slider(value = number.toFloat(), onValueChange = {
                    number =
                        it.toInt().coerceIn(0, vNumber)
                }, valueRange = 0f..vNumber.toFloat())
            }
        }
    )
}

@Composable
private fun NormalResultItem(cards: List<SetCardModel>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cards.forEach { c ->
            Box(Modifier.weight(1f)) {
                SetCardComposable(
                    card = c,
                    isSelected = false,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun UltraResultItem(cards: List<SetCardModel>, showConjugate: Boolean) {
    val parts = remember(cards) { SetAlgorithms.computeUltraParts(cards) }
    if (parts == null) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            cards.forEach { c ->
                SetCardComposable(
                    card = c,
                    isSelected = false,
                    onClick = {},
                    modifier = Modifier.width(44.dp)
                )
            }
        }
        return
    }
    val (conjugate, pair1, pair2) = parts
    if (!showConjugate) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SetCardComposable(
                    card = pair1.first,
                    isSelected = false,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
                SetCardComposable(
                    card = pair1.second,
                    isSelected = false,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SetCardComposable(
                    card = pair2.first,
                    isSelected = false,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
                SetCardComposable(
                    card = pair2.second,
                    isSelected = false,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }
    Row(
        Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SetCardComposable(card = pair1.first, isSelected = false, onClick = {
            }, modifier = Modifier.width(44.dp))
            SetCardComposable(card = pair1.second, isSelected = false, onClick = {
            }, modifier = Modifier.width(44.dp))
        }
        SetCardComposable(card = conjugate, isSelected = false, onClick = {
        }, modifier = Modifier.width(52.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SetCardComposable(card = pair2.first, isSelected = false, onClick = {
            }, modifier = Modifier.width(44.dp))
            SetCardComposable(card = pair2.second, isSelected = false, onClick = {
            }, modifier = Modifier.width(44.dp))
        }
    }
}

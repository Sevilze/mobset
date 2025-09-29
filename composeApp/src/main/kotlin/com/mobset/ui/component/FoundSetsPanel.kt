package com.mobset.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.model.Card
import com.mobset.domain.model.FoundSet
import com.mobset.domain.model.GameMode
import com.mobset.domain.model.GameStatus
import com.mobset.domain.model.SetType


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoundSetsPanel(
    status: GameStatus,
    mode: GameMode,
    foundSets: List<FoundSet>,
    showTimestamps: Boolean,
    modifier: Modifier = Modifier
) {
    if (foundSets.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(foundSets, key = { index, fs -> fs.timestamp.hashCode() + index }) { _, fs ->
                FoundSetPreview(fs = fs, mode = mode, showTimestamps = showTimestamps)
            }
        }
    }
}

@Composable
private fun FoundSetPreview(fs: FoundSet, mode: GameMode, showTimestamps: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (fs.type) {
            SetType.ULTRA -> UltraSetPreview(cards = fs.cards, mode = mode)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                fs.cards.forEach { c ->
                    SetCard(card = c, isSelected = false, onClick = {}, isHinted = false, modifier = Modifier.width(64.dp))
                }
            }
        }
        val who = fs.foundBy ?: "Unknown"
        Text(
            text = "found by $who" + if (showTimestamps) " • " else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UltraSetPreview(cards: List<Card>, mode: GameMode) {
    val parts = remember(cards, mode) { computeUltraConjugateAndPairs(cards, mode) }
    if (parts != null) {
        val (conj, p1, p2) = parts
        // Compact H-structure: two vertical columns for the pairs, single center conjugate
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SetCard(card = p1.first, isSelected = false, onClick = {}, isHinted = false, modifier = Modifier.width(44.dp))
                SetCard(card = p1.second, isSelected = false, onClick = {}, isHinted = false, modifier = Modifier.width(44.dp))
            }
            SetCard(card = conj, isSelected = false, onClick = {}, isHinted = false, modifier = Modifier.width(52.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SetCard(card = p2.first, isSelected = false, onClick = {}, isHinted = false, modifier = Modifier.width(44.dp))
                SetCard(card = p2.second, isSelected = false, onClick = {}, isHinted = false, modifier = Modifier.width(44.dp))
            }
        }
    } else {
        // Fallback: show all four in a compact row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            cards.forEach { c -> SetCard(card = c, isSelected = false, onClick = {}, isHinted = false, modifier = Modifier.width(44.dp)) }
        }
    }
}

private data class UltraParts(val conj: Card, val p1: Pair<Card, Card>, val p2: Pair<Card, Card>)

private fun computeUltraConjugateAndPairs(cards: List<Card>, mode: GameMode): UltraParts? {
    if (cards.size != 4) return null
    val partitions = listOf(
        listOf(0 to 1, 2 to 3),
        listOf(0 to 2, 1 to 3),
        listOf(0 to 3, 1 to 2)
    )
    for (p in partitions) {
        val conj1 = SetAlgorithms.conjugateCard(cards[p[0].first], cards[p[0].second], mode)
        val conj2 = SetAlgorithms.conjugateCard(cards[p[1].first], cards[p[1].second], mode)
        if (conj1 == conj2) {
            val pair1 = cards[p[0].first] to cards[p[0].second]
            val pair2 = cards[p[1].first] to cards[p[1].second]
            return UltraParts(conj1, pair1, pair2)
        }
    }
    return null
}

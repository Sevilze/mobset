package com.mobset.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobset.domain.algorithm.SetAlgorithms
import com.mobset.domain.model.Card as SetCardModel
import com.mobset.domain.model.GameMode
import com.mobset.domain.model.SetType
import com.mobset.ui.component.SetCard as SetCardComposable

/**
 * Playground: explore sample normal sets with a neat grid UI.
 * For now, generates a subset of valid sets from the NORMAL deck.
 */
@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun PlaygroundScreen(modifier: Modifier = Modifier) {
    val samples = remember { mutableStateListOf<List<SetCardModel>>() }

    LaunchedEffect(Unit) {
        // Generate a small subset of valid sets for display
        val mode = GameMode.NORMAL
        val deck = SetAlgorithms.generateDeck(mode)
        val found = mutableSetOf<Triple<Int, Int, Int>>()
        var count = 0
        for (i in 0 until deck.size) {
            for (j in i + 1 until deck.size) {
                val c3 = SetAlgorithms.conjugateCard(deck[i], deck[j], mode)
                val k = deck.indexOf(c3)
                if (k > j) {
                    samples += listOf(deck[i], deck[j], deck[k])
                    count++
                    if (count >= 12) break
                }
            }
            if (count >= 12) break
        }
    }

    Column(modifier = modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        .padding(16.dp)) {
        Text(
            text = "Playground",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Browse example normal sets",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(samples) { set ->
                Card {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        set.forEachIndexed { idx, c ->
                            SetCardComposable(
                                card = c,
                                isSelected = false,
                                onClick = {},
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}


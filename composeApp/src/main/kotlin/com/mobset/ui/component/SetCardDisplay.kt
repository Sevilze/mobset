package com.mobset.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.mobset.domain.model.Card

/**
 * Non-clickable version of SetCard for display purposes.
 */
@Composable
fun SetCardDisplay(
    card: Card,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isHinted: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        animationSpec = tween(150),
        label = "card_scale"
    )

    val elevation by animateFloatAsState(
        targetValue = if (isSelected) 8f else 2f,
        animationSpec = tween(150),
        label = "card_elevation"
    )

    OutlinedCard(
        modifier = modifier
            .aspectRatio(1.6f)
            .scale(scale),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isHinted -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = when {
            isSelected -> CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                width = 3.dp
            )
            isHinted -> CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.secondary
                ),
                width = 2.dp
            )
            else -> CardDefaults.outlinedCardBorder()
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val cardHeight = maxHeight
            val margin = cardHeight * 0.1f
            val contentHeight = cardHeight - margin * 2

            // Reuse the shared CardSymbols function from SetCard.kt
            CardSymbols(
                card = card,
                symbolSize = contentHeight * 0.6f,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(margin)
            )
        }
    }
}

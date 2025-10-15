package com.mobset.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobset.domain.model.*
import com.mobset.theme.AppTheme
import com.mobset.theme.LocalCardColors

/**
 * Composable that renders a Set card with proper visual traits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetCard(
    card: Card,
    isSelected: Boolean,
    onClick: () -> Unit,
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

    Card(
        onClick = onClick,
        modifier =
        modifier
            .aspectRatio(1.6f)
            .scale(scale),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
        colors =
        CardDefaults.cardColors(
            containerColor =
            when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isHinted -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border =
        when {
            isSelected ->
                CardDefaults.outlinedCardBorder().copy(
                    brush =
                    androidx.compose.ui.graphics
                        .SolidColor(MaterialTheme.colorScheme.primary),
                    width = 3.dp
                )
            isHinted ->
                CardDefaults.outlinedCardBorder().copy(
                    brush =
                    androidx.compose.ui.graphics
                        .SolidColor(MaterialTheme.colorScheme.secondary),
                    width = 2.dp
                )
            else -> null
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val cardHeight = maxHeight
            val margin = cardHeight * 0.1f
            val contentHeight = cardHeight - margin * 2

            CardSymbols(
                card = card,
                symbolSize = contentHeight * 0.6f,
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(margin)
            )
        }
    }
}

/**
 * Renders the symbols on a Set card using one Canvas so stripe/checker
 * patterns are continuous across symbols.
 * Internal visibility allows reuse in other card components within this package.
 */
@Composable
internal fun CardSymbols(card: Card, symbolSize: Dp, modifier: Modifier = Modifier) {
    val color = card.getColor()
    val shape = card.getShape()
    val shade = card.getShade()
    val number = card.getNumber()
    val symbolColor = getSymbolColor(color)

    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height

        // If the card is wider than tall we treat drawing in a "logical" portrait
        // coordinate system and rotate the whole drawing so symbols face horizontally.
        val shouldRotate = canvasW > canvasH
        val logicalW = if (shouldRotate) canvasH else canvasW
        val logicalH = if (shouldRotate) canvasW else canvasH
        val maxSymbols = 3

        var symbolH = logicalH * 0.60f
        var symbolW = symbolH * (200f / 400f)
        var spacing = symbolW * 0.12f

        // Ensure three symbols fit in logical width, scale down if needed.
        val neededForMax = maxSymbols * symbolW + (maxSymbols - 1) * spacing
        if (neededForMax > logicalW) {
            val scale = logicalW / (neededForMax + 1f)
            symbolH *= scale
            symbolW *= scale
            spacing *= scale
        }

        // Compute placement for the actual number of symbols (centered).
        val totalWidthActual = number * symbolW + (number - 1) * spacing
        val startX = (logicalW - totalWidthActual) / 2f
        val topY = (logicalH - symbolH) / 2f

        // Center the logical block inside actual canvas before rotating.
        val offsetX = (canvasW - logicalW) / 2f
        val offsetY = (canvasH - logicalH) / 2f

        // Build per-symbol positioned paths and a combined union path for clipping.
        val symbolPaths = mutableListOf<Path>()
        val combined = Path()
        for (i in 0 until number) {
            val left = startX + i * (symbolW + spacing)
            val base = createShapePath(shape, Size(symbolW, symbolH))
            val placed = Path().apply { addPath(base, Offset(left + offsetX, topY + offsetY)) }
            symbolPaths.add(placed)
            combined.addPath(placed, Offset.Zero)
        }

        val outlinePx = 18f * (symbolW / 200f)

        if (shouldRotate) {
            rotate(degrees = 90f) {
                renderPathsByShade(
                    shade,
                    symbolPaths,
                    combined,
                    symbolColor,
                    outlinePx,
                    rotated = true
                )
            }
        } else {
            renderPathsByShade(
                shade,
                symbolPaths,
                combined,
                symbolColor,
                outlinePx,
                rotated = false
            )
        }
    }
}

internal fun DrawScope.renderPathsByShade(
    shade: CardShade,
    symbolPaths: List<Path>,
    combined: Path,
    color: Color,
    outlinePx: Float,
    rotated: Boolean
) {
    when (shade) {
        CardShade.SOLID -> {
            symbolPaths.forEach { drawPath(it, color) }
        }
        CardShade.STRIPED -> {
            clipPath(combined) {
                drawStripedPatternContinuous(color, horizontal = rotated)
            }
            symbolPaths.forEach { drawPath(it, color, style = Stroke(width = outlinePx)) }
        }
        CardShade.OUTLINE -> {
            symbolPaths.forEach { drawPath(it, color, style = Stroke(width = outlinePx)) }
        }
        CardShade.CHECKERED -> {
            clipPath(combined) {
                drawCheckeredPattern(color)
            }
            symbolPaths.forEach { drawPath(it, color, style = Stroke(width = outlinePx)) }
        }
    }
}

/**
 * Draw stripes once across full DrawScope.size then clip to the shapes.
 */
internal fun DrawScope.drawStripedPatternContinuous(color: Color, horizontal: Boolean) {
    // Scale stripe thickness and spacing with canvas size for visual consistency across usages
    val unit = (size.minDimension.coerceAtLeast(1f)) / 100f
    val stripeThickness = (unit * 1.6f).coerceAtLeast(0.75f)
    val spacing = stripeThickness * 2f

    if (horizontal) {
        var y = -size.height
        while (y < size.height * 2f) {
            drawRect(
                color = color,
                topLeft = Offset(0f, y),
                size = Size(size.width, stripeThickness)
            )
            y += spacing
        }
    } else {
        var x = -size.width
        while (x < size.width * 2f) {
            drawRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(stripeThickness, size.height)
            )
            x += spacing
        }
    }
}

/** Draw a simple checkered pattern across DrawScope.size */
internal fun DrawScope.drawCheckeredPattern(color: Color) {
    val squareW = 50.dp.toPx()
    val squareH = 100.dp.toPx()

    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else squareW
        while (x < size.width) {
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(minOf(squareW, size.width - x), minOf(squareH, size.height - y))
            )
            x += squareW * 2f
        }
        y += squareH
        row++
    }
}

/**
 * Creates a path for the specified shape using authentic SVG path data.
 */
internal fun createShapePath(shape: CardShape, size: Size): Path {
    val path = Path()

    // SVG viewBox is 200x400, transform to current size
    val scaleX = size.width / 200f
    val scaleY = size.height / 400f

    when (shape) {
        CardShape.OVAL -> createOvalPath(path, scaleX, scaleY)
        CardShape.SQUIGGLE -> createSquigglePath(path, scaleX, scaleY)
        CardShape.DIAMOND -> createDiamondPath(path, scaleX, scaleY)
        CardShape.HOURGLASS -> createHourglassPath(path, scaleX, scaleY)
    }

    return path
}

internal fun createOvalPath(path: Path, scaleX: Float, scaleY: Float) {
    path.addRoundRect(
        RoundRect(
            left = 11.49999f * scaleX,
            top = 13.866646f * scaleY,
            right = 187.5f * scaleX,
            bottom = 386.133354f * scaleY,
            radiusX = 82f * scaleX,
            radiusY = 82f * scaleY
        )
    )
}

internal fun createSquigglePath(path: Path, scaleX: Float, scaleY: Float) {
    path.moveTo(67.892902f * scaleX, 12.746785f * scaleY)
    path.relativeCubicTo(
        43.231313f * scaleX,
        -6.717223f * scaleY,
        107.352741f * scaleX,
        6.609823f * scaleY,
        121.028973f * scaleX,
        58.746408f * scaleY
    )
    path.relativeCubicTo(
        13.676233f * scaleX,
        52.136585f * scaleY,
        -44.848649f * scaleX,
        161.467192f * scaleY,
        -45.07116f * scaleX,
        204.650732f * scaleY
    )
    path.relativeCubicTo(
        4.566246f * scaleX,
        56.959708f * scaleY,
        83.805481f * scaleX,
        87.929227f * scaleY,
        22.329944f * scaleX,
        105.806022f * scaleY
    )
    path.relativeCubicTo(
        -61.475536f * scaleX,
        17.876795f * scaleY,
        -126.122496f * scaleX,
        -1.855045f * scaleY,
        -143.73294f * scaleX,
        -41.933823f * scaleY
    )
    path.relativeCubicTo(
        -17.610444f * scaleX,
        -40.07878f * scaleY,
        49.274638f * scaleX,
        -120.109409f * scaleY,
        46.14822f * scaleX,
        -188.091997f * scaleY
    )
    path.relativeCubicTo(
        -3.126418f * scaleX,
        -67.982588f * scaleY,
        -21.873669f * scaleX,
        -70.257464f * scaleY,
        -49.613153f * scaleX,
        -80.177084f * scaleY
    )
    path.relativeCubicTo(
        -27.739485f * scaleX,
        -9.919618f * scaleY,
        5.678801f * scaleX,
        -52.283035f * scaleY,
        48.910115f * scaleX,
        -59.000258f * scaleY
    )
    path.close()
}

internal fun createDiamondPath(path: Path, scaleX: Float, scaleY: Float) {
    path.moveTo(100f * scaleX, 10f * scaleY)
    path.relativeLineTo(-90f * scaleX, 190f * scaleY)
    path.relativeLineTo(90f * scaleX, 190f * scaleY)
    path.relativeLineTo(90f * scaleX, -190f * scaleY)
    path.relativeLineTo(-90f * scaleX, -190f * scaleY)
    path.close()
}

internal fun createHourglassPath(path: Path, scaleX: Float, scaleY: Float) {
    path.moveTo(118.4386f * scaleX, 201.1739f * scaleY)
    path.relativeCubicTo(
        0f * scaleX,
        -19.9228f * scaleY,
        17.9239f * scaleX,
        -37.4523f * scaleY,
        26.8821f * scaleX,
        -47.8051f * scaleY
    )
    path.relativeCubicTo(
        23.5179f * scaleX,
        -24.6961f * scaleY,
        40.3179f * scaleX,
        -68.5227f * scaleY,
        43.6821f * scaleX,
        -119.5128f * scaleY
    )
    path.relativeCubicTo(
        0.5578f * scaleX,
        -10.3522f * scaleY,
        -3.9211f * scaleX,
        -19.9228f * scaleY,
        -9.5239f * scaleX,
        -19.9228f * scaleY
    )
    path.lineTo(19.3203f * scaleX, 13.8332f * scaleY)
    path.relativeCubicTo(
        -5.6028f * scaleX,
        0f * scaleY,
        -10.0817f * scaleX,
        9.5703f * scaleY,
        -9.5239f * scaleX,
        19.9228f * scaleY
    )
    path.relativeCubicTo(
        3.3634f * scaleX,
        50.9901f * scaleY,
        20.1634f * scaleX,
        94.8155f * scaleY,
        43.6821f * scaleX,
        120.3075f * scaleY
    )
    path.relativeCubicTo(
        8.9578f * scaleX,
        9.5586f * scaleY,
        26.8821f * scaleX,
        27.0888f * scaleY,
        26.8821f * scaleX,
        47.0104f * scaleY
    )
    path.relativeCubicTo(
        0f * scaleX,
        19.1172f * scaleY,
        -17.9239f * scaleX,
        36.6486f * scaleY,
        -26.8821f * scaleX,
        47.0104f * scaleY
    )
    path.relativeCubicTo(
        -23.5179f * scaleX,
        25.4897f * scaleY,
        -40.3179f * scaleX,
        69.3144f * scaleY,
        -43.6821f * scaleX,
        120.3075f * scaleY
    )
    path.relativeCubicTo(
        -0.5578f * scaleX,
        10.3522f * scaleY,
        3.9211f * scaleX,
        19.9228f * scaleY,
        9.5239f * scaleX,
        19.9228f * scaleY
    )
    path.lineTo(178.9203f * scaleX, 386.1668f * scaleY)
    path.relativeCubicTo(
        6.1606f * scaleX,
        0f * scaleY,
        10.6394f * scaleX,
        -9.5586f * scaleY,
        9.5239f * scaleX,
        -19.9228f * scaleY
    )
    path.relativeCubicTo(
        -3.3634f * scaleX,
        -50.9901f * scaleY,
        -20.1634f * scaleX,
        -94.8155f * scaleY,
        -43.6821f * scaleX,
        -120.3075f * scaleY
    )
    path.relativeCubicTo(
        -8.9659f * scaleX,
        -9.5586f * scaleY,
        -26.3235f * scaleX,
        -27.0888f * scaleY,
        -26.3235f * scaleX,
        -47.0104f * scaleY
    )
    path.close()
}

/**
 * Maps card color to actual Color.
 */
@Composable
internal fun getSymbolColor(cardColor: CardColor): Color {
    val palette = LocalCardColors.current
    val idx = cardColor.value
    return palette.getOrNull(idx)
        ?: palette.lastOrNull()
        ?: Color(0xFFFF0101)
}

@Preview
@Composable
private fun SetCardPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SetCard(card = Card("0000"), isSelected = false, onClick = {
                }, modifier = Modifier.width(96.dp))
                SetCard(card = Card("0100"), isSelected = false, onClick = {
                }, modifier = Modifier.width(96.dp))
                SetCard(card = Card("0200"), isSelected = false, onClick = {
                }, modifier = Modifier.width(96.dp))
                SetCard(card = Card("0300"), isSelected = false, onClick = {
                }, modifier = Modifier.width(96.dp))
            }
        }
    }
}

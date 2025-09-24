package com.mobset.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mobset.domain.model.*
import com.mobset.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin

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
        modifier = modifier
            .aspectRatio(0.7f)
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
                brush = SolidColor(MaterialTheme.colorScheme.primary),
                width = 2.dp
            )
            isHinted -> CardDefaults.outlinedCardBorder().copy(
                brush = SolidColor(MaterialTheme.colorScheme.secondary),
                width = 2.dp
            )
            else -> null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            CardSymbols(
                card = card,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Renders the symbols on a Set card.
 */
@Composable
private fun CardSymbols(
    card: Card,
    modifier: Modifier = Modifier
) {
    val color = card.getColor()
    val shape = card.getShape()
    val shade = card.getShade()
    val number = card.getNumber()
    
    val symbolColor = getSymbolColor(color)
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        repeat(number) { index ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Canvas(
                modifier = Modifier
                    .size(width = 40.dp, height = 20.dp)
            ) {
                drawSymbol(
                    shape = shape,
                    shade = shade,
                    color = symbolColor,
                    size = size
                )
            }
        }
    }
}

/**
 * Draws a single symbol on the canvas.
 */
private fun DrawScope.drawSymbol(
    shape: CardShape,
    shade: CardShade,
    color: Color,
    size: Size
) {
    val path = createShapePath(shape, size)
    
    when (shade) {
        CardShade.SOLID -> {
            // Fill the shape completely
            drawPath(
                path = path,
                color = color
            )
        }
        CardShade.STRIPED -> {
            // Draw striped pattern clipped to shape
            clipPath(path) {
                drawStripedPattern(color, size)
            }
            // Draw outline
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        CardShade.OUTLINE -> {
            // Draw only the outline
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        CardShade.CHECKERED -> {
            // Draw checkered pattern clipped to shape
            clipPath(path) {
                drawCheckeredPattern(color, size)
            }
            // Draw outline
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Creates a path for the specified shape using authentic SVG path data.
 */
private fun createShapePath(shape: CardShape, size: Size): Path {
    val path = Path()

    // SVG viewBox is 200x400, transform to current size
    val scaleX = size.width / 200f
    val scaleY = size.height / 400f

    when (shape) {
        CardShape.OVAL -> {
            createOvalPath(path, scaleX, scaleY)
        }
        CardShape.SQUIGGLE -> {
            createSquigglePath(path, scaleX, scaleY)
        }
        CardShape.DIAMOND -> {
            createDiamondPath(path, scaleX, scaleY)
        }
        CardShape.HOURGLASS -> {
            createHourglassPath(path, scaleX, scaleY)
        }
    }

    return path
}

/**
 * Creates the authentic oval shape from reference SVG.
 */
private fun createOvalPath(path: Path, scaleX: Float, scaleY: Float) {
    // SVG: m11.49999,95.866646c0,-44.557076 37.442923,-81.999998 82.000002,-81.999998l12.000015,0c44.557076,0 81.999992,37.442923 81.999992,81.999998l0,206.133354c0,44.557098 -37.442917,82 -81.999992,82l-12.000015,0c-44.557079,0 -82.000002,-37.442902 -82.000002,-82l0,-206.133354z
    path.addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            left = 11.49999f * scaleX,
            top = 13.866646f * scaleY,
            right = 187.5f * scaleX,
            bottom = 386.133354f * scaleY,
            radiusX = 82f * scaleX,
            radiusY = 82f * scaleY
        )
    )
}

/**
 * Creates the authentic squiggle shape from reference SVG.
 */
private fun createSquigglePath(path: Path, scaleX: Float, scaleY: Float) {
    // SVG: m67.892902,12.746785c43.231313,-6.717223 107.352741,6.609823 121.028973,58.746408c13.676233,52.136585 -44.848649,161.467192 -45.07116,204.650732c4.566246,56.959708 83.805481,87.929227 22.329944,105.806022c-61.475536,17.876795 -126.122496,-1.855045 -143.73294,-41.933823c-17.610444,-40.07878 49.274638,-120.109409 46.14822,-188.091997c-3.126418,-67.982588 -21.873669,-70.257464 -49.613153,-80.177084c-27.739485,-9.919618 5.678801,-52.283035 48.910115,-59.000258z

    path.moveTo(67.892902f * scaleX, 12.746785f * scaleY)
    path.relativeCubicTo(
        43.231313f * scaleX, -6.717223f * scaleY,
        107.352741f * scaleX, 6.609823f * scaleY,
        121.028973f * scaleX, 58.746408f * scaleY
    )
    path.relativeCubicTo(
        13.676233f * scaleX, 52.136585f * scaleY,
        -44.848649f * scaleX, 161.467192f * scaleY,
        -45.07116f * scaleX, 204.650732f * scaleY
    )
    path.relativeCubicTo(
        4.566246f * scaleX, 56.959708f * scaleY,
        83.805481f * scaleX, 87.929227f * scaleY,
        22.329944f * scaleX, 105.806022f * scaleY
    )
    path.relativeCubicTo(
        -61.475536f * scaleX, 17.876795f * scaleY,
        -126.122496f * scaleX, -1.855045f * scaleY,
        -143.73294f * scaleX, -41.933823f * scaleY
    )
    path.relativeCubicTo(
        -17.610444f * scaleX, -40.07878f * scaleY,
        49.274638f * scaleX, -120.109409f * scaleY,
        46.14822f * scaleX, -188.091997f * scaleY
    )
    path.relativeCubicTo(
        -3.126418f * scaleX, -67.982588f * scaleY,
        -21.873669f * scaleX, -70.257464f * scaleY,
        -49.613153f * scaleX, -80.177084f * scaleY
    )
    path.relativeCubicTo(
        -27.739485f * scaleX, -9.919618f * scaleY,
        5.678801f * scaleX, -52.283035f * scaleY,
        48.910115f * scaleX, -59.000258f * scaleY
    )
    path.close()
}

/**
 * Creates the authentic diamond shape from reference SVG.
 */
private fun createDiamondPath(path: Path, scaleX: Float, scaleY: Float) {
    // SVG: m100 10-90 190 90 190 90-190-90-190z
    path.moveTo(100f * scaleX, 10f * scaleY)
    path.relativeLineTo(-90f * scaleX, 190f * scaleY)
    path.relativeLineTo(90f * scaleX, 190f * scaleY)
    path.relativeLineTo(90f * scaleX, -190f * scaleY)
    path.relativeLineTo(-90f * scaleX, -190f * scaleY)
    path.close()
}

/**
 * Creates the authentic hourglass shape from reference SVG.
 */
private fun createHourglassPath(path: Path, scaleX: Float, scaleY: Float) {
    // SVG: m118.4386 201.1739c0-19.9228 17.9239-37.4523 26.8821-47.8051 23.5179-24.6961 40.3179-68.5227 43.6821-119.5128.5578-10.3522-3.9211-19.9228-9.5239-19.9228H19.3203c-5.6028 0-10.0817 9.5703-9.5239 19.9228 3.3634 50.9901 20.1634 94.8155 43.6821 120.3075 8.9578 9.5586 26.8821 27.0888 26.8821 47.0104 0 19.1172-17.9239 36.6486-26.8821 47.0104-23.5179 25.4897-40.3179 69.3144-43.6821 120.3075-.5578 10.3522 3.9211 19.9228 9.5239 19.9228h159.6c6.1606 0 10.6394-9.5586 9.5239-19.9228-3.3634-50.9901-20.1634-94.8155-43.6821-120.3075-8.9659-9.5586-26.3235-27.0888-26.3235-47.0104z

    path.moveTo(118.4386f * scaleX, 201.1739f * scaleY)
    path.relativeCubicTo(
        0f * scaleX, -19.9228f * scaleY,
        17.9239f * scaleX, -37.4523f * scaleY,
        26.8821f * scaleX, -47.8051f * scaleY
    )
    path.relativeCubicTo(
        23.5179f * scaleX, -24.6961f * scaleY,
        40.3179f * scaleX, -68.5227f * scaleY,
        43.6821f * scaleX, -119.5128f * scaleY
    )
    path.relativeCubicTo(
        0.5578f * scaleX, -10.3522f * scaleY,
        -3.9211f * scaleX, -19.9228f * scaleY,
        -9.5239f * scaleX, -19.9228f * scaleY
    )
    path.lineTo(19.3203f * scaleX, 13.8332f * scaleY)
    path.relativeCubicTo(
        -5.6028f * scaleX, 0f * scaleY,
        -10.0817f * scaleX, 9.5703f * scaleY,
        -9.5239f * scaleX, 19.9228f * scaleY
    )
    path.relativeCubicTo(
        3.3634f * scaleX, 50.9901f * scaleY,
        20.1634f * scaleX, 94.8155f * scaleY,
        43.6821f * scaleX, 120.3075f * scaleY
    )
    path.relativeCubicTo(
        8.9578f * scaleX, 9.5586f * scaleY,
        26.8821f * scaleX, 27.0888f * scaleY,
        26.8821f * scaleX, 47.0104f * scaleY
    )
    path.relativeCubicTo(
        0f * scaleX, 19.1172f * scaleY,
        -17.9239f * scaleX, 36.6486f * scaleY,
        -26.8821f * scaleX, 47.0104f * scaleY
    )
    path.relativeCubicTo(
        -23.5179f * scaleX, 25.4897f * scaleY,
        -40.3179f * scaleX, 69.3144f * scaleY,
        -43.6821f * scaleX, 120.3075f * scaleY
    )
    path.relativeCubicTo(
        -0.5578f * scaleX, 10.3522f * scaleY,
        3.9211f * scaleX, 19.9228f * scaleY,
        9.5239f * scaleX, 19.9228f * scaleY
    )
    path.lineTo(178.9203f * scaleX, 386.1668f * scaleY)
    path.relativeCubicTo(
        6.1606f * scaleX, 0f * scaleY,
        10.6394f * scaleX, -9.5586f * scaleY,
        9.5239f * scaleX, -19.9228f * scaleY
    )
    path.relativeCubicTo(
        -3.3634f * scaleX, -50.9901f * scaleY,
        -20.1634f * scaleX, -94.8155f * scaleY,
        -43.6821f * scaleX, -120.3075f * scaleY
    )
    path.relativeCubicTo(
        -8.9659f * scaleX, -9.5586f * scaleY,
        -26.3235f * scaleX, -27.0888f * scaleY,
        -26.3235f * scaleX, -47.0104f * scaleY
    )
    path.close()
}

/**
 * Draws a striped pattern matching the reference design.
 * Reference pattern: 2px stripes with 8px gaps in 20px repeat.
 */
private fun DrawScope.drawStripedPattern(color: Color, size: Size) {
    // Reference pattern dimensions scaled to current size
    val patternHeight = 20.dp.toPx()
    val stripeHeight = 8.dp.toPx()

    var y = 0f
    while (y < size.height) {
        // Draw the stripe portion of the pattern
        drawRect(
            color = color,
            topLeft = Offset(0f, y),
            size = androidx.compose.ui.geometry.Size(size.width, stripeHeight)
        )
        y += patternHeight
    }
}

/**
 * Draws a checkered pattern matching the reference design.
 * Reference pattern: 50x100px squares in 100x200px repeat.
 */
private fun DrawScope.drawCheckeredPattern(color: Color, size: Size) {
    // Reference pattern dimensions scaled to current size
    val patternWidth = 100.dp.toPx()
    val patternHeight = 200.dp.toPx()
    val squareWidth = 50.dp.toPx()
    val squareHeight = 100.dp.toPx()

    var y = 0f
    var rowOffset = 0
    while (y < size.height) {
        var x = if (rowOffset % 2 == 0) 0f else squareWidth
        while (x < size.width) {
            // Draw the square portion of the pattern
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(
                    minOf(squareWidth, size.width - x),
                    minOf(squareHeight, size.height - y)
                )
            )
            x += patternWidth
        }
        y += squareHeight
        rowOffset++
    }
}

/**
 * Maps card color to actual Color.
 */
@Composable
private fun getSymbolColor(cardColor: CardColor): Color {
    return when (cardColor) {
        CardColor.RED -> Color(0xFFE53E3E)
        CardColor.GREEN -> Color(0xFF38A169)
        CardColor.BLUE -> Color(0xFF3182CE)
        CardColor.PURPLE -> Color(0xFF805AD5)
    }
}

@Preview
@Composable
private fun SetCardPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SetCard(
                card = Card("0000"), // Red oval solid 1
                isSelected = false,
                onClick = {},
                modifier = Modifier.width(80.dp)
            )
            SetCard(
                card = Card("1111"), // Green squiggle striped 2
                isSelected = true,
                onClick = {},
                modifier = Modifier.width(80.dp)
            )
            SetCard(
                card = Card("2222"), // Blue diamond outline 3
                isSelected = false,
                onClick = {},
                modifier = Modifier.width(80.dp),
                isHinted = true
            )
        }
    }
}

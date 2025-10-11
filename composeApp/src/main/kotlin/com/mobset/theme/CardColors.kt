package com.mobset.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * CompositionLocal providing the 3 card colors used by the game.
 */
val LocalCardColors =
    staticCompositionLocalOf<List<Color>> {
        listOf(Color(0xFFFF0101), Color(0xFF008002), Color(0xFFFB8C00))
    }

package com.mobset.domain.model

/**
 * Represents different game modes with their configurations.
 * Mirrors the reference implementation modes.
 */
data class GameMode(
    val id: String,
    val name: String,
    val description: String,
    val traitCount: Int,
    val traitVariants: List<Int>,
    val deckSize: Int,
    val boardSize: Int,
    val setTypes: List<SetType>
) {
    companion object {
        // Standard Set game mode
        val NORMAL = GameMode(
            id = "normal",
            name = "Normal",
            description = "Classic Set game with 4 traits",
            traitCount = 4,
            traitVariants = listOf(3, 3, 3, 3), // color, shape, shade, number
            deckSize = 81, // 3^4
            boardSize = 12,
            setTypes = listOf(SetType.NORMAL)
        )
        
        // Ultraset, select 4 cards forming two 3-card sets with a shared conjugate
        val ULTRA = GameMode(
            id = "ultra",
            name = "Ultra",
            description = "4 traits like Normal; pick 4 cards: two 3-card sets sharing a conjugate",
            traitCount = 4,
            traitVariants = listOf(3, 3, 3, 3),
            deckSize = 81,
            boardSize = 12,
            setTypes = listOf(SetType.ULTRA)
        )
        
        // 4-Set mode
        val FOUR_SET = GameMode(
            id = "4set",
            name = "4-Set",
            description = "Find sets of 4 cards instead of 3",
            traitCount = 4,
            traitVariants = listOf(4, 4, 4, 4), // 4 variants per trait
            deckSize = 256, // 4^4
            boardSize = 16,
            setTypes = listOf(SetType.FOUR_SET)
        )
        
        // Ghost mode
        val GHOST = GameMode(
            id = "ghost",
            name = "Ghost",
            description = "Find sets with invisible ghost cards",
            traitCount = 4,
            traitVariants = listOf(3, 3, 3, 3),
            deckSize = 81,
            boardSize = 12,
            setTypes = listOf(SetType.GHOST)
        )
        
        val ALL_MODES = listOf(NORMAL, ULTRA, FOUR_SET, GHOST)
        
        fun fromId(id: String): GameMode? = ALL_MODES.find { it.id == id }
    }
}

/**
 * Represents different types of sets that can be found.
 */
enum class SetType(
    val id: String,
    val displayName: String,
    val size: Int,
    val description: String
) {
    NORMAL("normal", "Normal Set", 3, "Classic 3-card set"),
    ULTRA("ultra", "Ultra Set", 4, "4-card selection: two normal sets sharing a common conjugate card"),
    FOUR_SET("4set", "4-Set", 4, "4-card set"),
    GHOST("ghost", "Ghost Set", 3, "3-card set with ghost cards");
    
    companion object {
        fun fromId(id: String): SetType? = entries.find { it.id == id }
    }
}

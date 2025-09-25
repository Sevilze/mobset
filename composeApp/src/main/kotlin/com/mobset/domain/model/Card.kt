package com.mobset.domain.model

/**
 * Represents a Set card using the reference encoding format.
 * Cards are encoded as strings of digits representing traits.
 */
data class Card(val encoding: String) {
    
    init {
        require(encoding.isNotEmpty()) { "Card encoding cannot be empty" }
        require(encoding.all { it.isDigit() }) { "Card encoding must contain only digits" }
    }
    
    /**
     * Gets the trait value at the specified position.
     */
    fun getTrait(position: Int): Int {
        return if (position < encoding.length) {
            encoding[position].digitToInt()
        } else {
            0
        }
    }
    
    /**
     * Gets the number of traits this card has.
     */
    val traitCount: Int get() = encoding.length
    
    // Standard Set game traits (for 4-trait cards)
    val color: Int get() = getTrait(0)
    val shape: Int get() = getTrait(1) 
    val shade: Int get() = getTrait(2)
    val number: Int get() = getTrait(3)
    
    override fun toString(): String = encoding
}

/**
 * Card traits for UI display
 */
enum class CardColor(val value: Int) {
    RED(0), GREEN(1), BLUE(2), PURPLE(3);
    
    companion object {
        fun fromValue(value: Int): CardColor = entries.getOrNull(value) ?: RED
    }
}

enum class CardShape(val value: Int) {
    OVAL(0), SQUIGGLE(1), DIAMOND(2), HOURGLASS(3);
    
    companion object {
        fun fromValue(value: Int): CardShape = entries.getOrNull(value) ?: OVAL
    }
}

enum class CardShade(val value: Int) {
    SOLID(0), STRIPED(1), OUTLINE(2), CHECKERED(3);

    companion object {
        fun fromValue(value: Int): CardShade = entries.getOrNull(value) ?: SOLID
    }
}

/**
 * Extension functions to get strongly-typed traits for UI
 */
fun Card.getColor(): CardColor = CardColor.fromValue(color)
fun Card.getShape(): CardShape = CardShape.fromValue(shape)
fun Card.getShade(): CardShade = CardShade.fromValue(shade)
fun Card.getNumber(): Int = number + 1

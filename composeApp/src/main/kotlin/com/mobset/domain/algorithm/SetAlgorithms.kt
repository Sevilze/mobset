package com.mobset.domain.algorithm

import com.mobset.domain.model.Card
import com.mobset.domain.model.GameMode
import com.mobset.domain.model.SetType
import kotlin.random.Random

/**
 * Core Set game algorithms ported from the reference implementation.
 */
object SetAlgorithms {
    
    /**
     * Conjugates a card for normal 3-card sets.
     * Given two cards, returns the third card that would complete the set.
     */
    fun conjugateCard(card1: Card, card2: Card, mode: GameMode): Card {
        val encoding = StringBuilder()
        
        for (i in 0 until mode.traitCount) {
            val trait1 = card1.getTrait(i)
            val trait2 = card2.getTrait(i)
            val variants = mode.traitVariants[i]
            
            // For normal sets: if traits are same, third is same; if different, third is the remaining one
            val trait3 = if (trait1 == trait2) {
                trait1
            } else {
                // Find the third value that makes a valid set
                (0 until variants).first { it != trait1 && it != trait2 }
            }
            
            encoding.append(trait3)
        }
        
        return Card(encoding.toString())
    }
    
    /**
     * Conjugates cards for 4-card sets.
     * Given three cards, returns the fourth card that would complete the 4-set.
     */
    fun conjugateCard4Set(card1: Card, card2: Card, card3: Card, mode: GameMode): Card {
        val encoding = StringBuilder()
        
        for (i in 0 until mode.traitCount) {
            val trait1 = card1.getTrait(i)
            val trait2 = card2.getTrait(i)
            val trait3 = card3.getTrait(i)
            val variants = mode.traitVariants[i]
            
            // For 4-sets, the fourth trait value is calculated differently
            val trait4 = (trait1 + trait2 + trait3) % variants
            encoding.append(trait4)
        }
        
        return Card(encoding.toString())
    }
    
    /**
     * Checks if the given cards form a valid normal set.
     */
    fun checkSetNormal(cards: List<Card>, mode: GameMode): Boolean {
        if (cards.size != 3) return false
        
        for (i in 0 until mode.traitCount) {
            val traits = cards.map { it.getTrait(i) }
            
            // For a valid set, each trait must be either all the same or all different
            val allSame = traits.all { it == traits[0] }
            val allDifferent = traits.toSet().size == traits.size
            
            if (!allSame && !allDifferent) return false
        }
        
        return true
    }
    
    /**
     * Checks if the given cards form a valid ultra set.
     */
    fun checkSetUltra(cards: List<Card>, mode: GameMode): Boolean {
        // Ultra sets follow the same rules as normal sets but with more traits
        return checkSetNormal(cards, mode)
    }
    
    /**
     * Checks if the given cards form a valid 4-set.
     */
    fun checkSet4Set(cards: List<Card>, mode: GameMode): Boolean {
        if (cards.size != 4) return false
        
        for (i in 0 until mode.traitCount) {
            val traits = cards.map { it.getTrait(i) }
            val variants = mode.traitVariants[i]
            
            // For 4-sets, the sum of traits must be 0 modulo the number of variants
            if (traits.sum() % variants != 0) return false
        }
        
        return true
    }
    
    /**
     * Checks if the given cards form a valid ghost set.
     */
    fun checkSetGhost(cards: List<Card>, mode: GameMode): Boolean {
        // Ghost sets can be 2 cards (with implicit ghost card) or 3 cards (normal set)
        return when (cards.size) {
            2 -> {
                // Check if there exists a valid third card (ghost card)
                val ghostCard = conjugateCard(cards[0], cards[1], mode)
                // Ghost card should not be on the current board (this would need board context)
                true // Simplified for now
            }
            3 -> checkSetNormal(cards, mode)
            else -> false
        }
    }
    
    /**
     * Finds all valid sets on the given board for the specified set type.
     */
    fun findSets(board: List<Card>, setType: SetType, mode: GameMode): List<List<Int>> {
        val sets = mutableListOf<List<Int>>()
        
        when (setType) {
            SetType.NORMAL, SetType.ULTRA -> {
                // Find all combinations of 3 cards
                for (i in board.indices) {
                    for (j in i + 1 until board.size) {
                        for (k in j + 1 until board.size) {
                            val cards = listOf(board[i], board[j], board[k])
                            if (checkSetNormal(cards, mode)) {
                                sets.add(listOf(i, j, k))
                            }
                        }
                    }
                }
            }
            SetType.FOUR_SET -> {
                // Find all combinations of 4 cards
                for (i in board.indices) {
                    for (j in i + 1 until board.size) {
                        for (k in j + 1 until board.size) {
                            for (l in k + 1 until board.size) {
                                val cards = listOf(board[i], board[j], board[k], board[l])
                                if (checkSet4Set(cards, mode)) {
                                    sets.add(listOf(i, j, k, l))
                                }
                            }
                        }
                    }
                }
            }
            SetType.GHOST -> {
                // Find normal 3-card sets and 2-card ghost sets
                sets.addAll(findSets(board, SetType.NORMAL, mode))
                
                // Find 2-card ghost sets
                for (i in board.indices) {
                    for (j in i + 1 until board.size) {
                        val cards = listOf(board[i], board[j])
                        if (checkSetGhost(cards, mode)) {
                            sets.add(listOf(i, j))
                        }
                    }
                }
            }
        }
        
        return sets
    }
    
    /**
     * Generates a complete deck for the given game mode.
     */
    fun generateDeck(mode: GameMode, seed: Long? = null): List<Card> {
        val cards = mutableListOf<Card>()
        
        fun generateCards(currentEncoding: String, traitIndex: Int) {
            if (traitIndex >= mode.traitCount) {
                cards.add(Card(currentEncoding))
                return
            }
            
            val variants = mode.traitVariants[traitIndex]
            for (variant in 0 until variants) {
                generateCards(currentEncoding + variant, traitIndex + 1)
            }
        }
        
        generateCards("", 0)
        
        // Shuffle with seed for deterministic results if provided
        return if (seed != null) {
            cards.shuffled(Random(seed))
        } else {
            cards.shuffled()
        }
    }
    
    /**
     * Finds a valid board configuration with at least one set.
     */
    fun findBoard(deck: List<Card>, mode: GameMode, setType: SetType): List<Card> {
        val boardSize = mode.boardSize
        
        // Try different combinations until we find a board with at least one set
        for (startIndex in 0..maxOf(0, deck.size - boardSize)) {
            val board = deck.subList(startIndex, minOf(startIndex + boardSize, deck.size))
            if (findSets(board, setType, mode).isNotEmpty()) {
                return board
            }
        }
        
        // If no valid board found, return the first boardSize cards
        return deck.take(boardSize)
    }
}

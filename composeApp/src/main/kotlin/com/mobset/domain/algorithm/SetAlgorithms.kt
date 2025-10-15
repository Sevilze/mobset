package com.mobset.domain.algorithm

import com.mobset.domain.model.Card
import com.mobset.domain.model.GameMode
import com.mobset.domain.model.SetType
import kotlin.random.Random

// Data class representing the decomposition of an Ultra set into its conjugate card and two pairs.
data class UltraParts(val conjugate: Card, val pair1: Pair<Card, Card>, val pair2: Pair<Card, Card>)

/**
 * Core Set game algorithms.
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
            val trait3 =
                if (trait1 == trait2) {
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

            // For a valid set, the sum of traits modulo 3 must equal 0
            if (traits.sum() % 3 != 0) return false
        }

        return true
    }

    /**
     * Checks if the given cards form a valid ultra set.
     */
    fun checkSetUltra(cards: List<Card>, mode: GameMode): Boolean {
        if (cards.size != 4) return false
        // Consider the three disjoint pair partitions: (0,1)-(2,3), (0,2)-(1,3), (0,3)-(1,2)
        val pairs =
            listOf(
                listOf(0 to 1, 2 to 3),
                listOf(0 to 2, 1 to 3),
                listOf(0 to 3, 1 to 2)
            )
        for (partition in pairs) {
            val conj1 = conjugateCard(cards[partition[0].first], cards[partition[0].second], mode)
            val conj2 = conjugateCard(cards[partition[1].first], cards[partition[1].second], mode)
            if (conj1 == conj2) return true
        }
        return false
    }

    /**
     * Checks if the given cards form a valid 4-set.
     */
    fun checkSet4Set(cards: List<Card>, mode: GameMode): Boolean {
        if (cards.size != 4) return false

        for (i in 0 until mode.traitCount) {
            val traits = cards.map { it.getTrait(i) }

            // For 4-sets, the XOR of all traits must equal 0
            val xorResult = traits.reduce { acc, trait -> acc xor trait }
            if (xorResult != 0) return false
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
            SetType.NORMAL -> {
                // Find all combinations of 3 cards for normal
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
            SetType.ULTRA -> {
                // Find all combinations of 4 cards for Ultra
                for (i in board.indices) {
                    for (j in i + 1 until board.size) {
                        for (k in j + 1 until board.size) {
                            for (l in k + 1 until board.size) {
                                val cards = listOf(board[i], board[j], board[k], board[l])
                                if (checkSetUltra(cards, mode)) {
                                    sets.add(listOf(i, j, k, l))
                                }
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

    /**
     * Computes the Ultra set decomposition into conjugate card and two pairs.
     * Returns null if the cards don't form a valid Ultra set.
     *
     */
    fun computeUltraParts(cards: List<Card>): UltraParts? {
        if (cards.size != 4) return null

        // Consider the three disjoint pair partitions: (0,1)-(2,3), (0,2)-(1,3), (0,3)-(1,2)
        val pairPartitions = listOf(
            listOf(0 to 1, 2 to 3),
            listOf(0 to 2, 1 to 3),
            listOf(0 to 3, 1 to 2)
        )

        for (partition in pairPartitions) {
            val conj1 = conjugateCard(
                cards[partition[0].first],
                cards[partition[0].second],
                GameMode.ULTRA
            )
            val conj2 = conjugateCard(
                cards[partition[1].first],
                cards[partition[1].second],
                GameMode.ULTRA
            )

            if (conj1 == conj2) {
                return UltraParts(
                    conjugate = conj1,
                    pair1 = cards[partition[0].first] to cards[partition[0].second],
                    pair2 = cards[partition[1].first] to cards[partition[1].second]
                )
            }
        }

        return null
    }

    /**
     * Finds all sets that include the given seed cards.
     */
    fun findSetsWithSeeds(deck: List<Card>, seeds: List<Card>, mode: GameMode): List<List<Card>> {
        val results = mutableListOf<List<Card>>()

        if (seeds.isEmpty()) return results

        if (mode == GameMode.NORMAL) {
            when (seeds.size) {
                1 -> {
                    val seed = seeds.first()
                    val seen = mutableSetOf<String>()
                    for (i in deck.indices) {
                        val card2 = deck[i]
                        if (card2 == seed) continue
                        val card3 = conjugateCard(seed, card2, mode)
                        if (card3 in deck) {
                            val triple = listOf(seed, card2, card3).sortedBy { it.encoding }
                            val key = triple.joinToString("-") { it.encoding }
                            if (seen.add(key)) {
                                results += triple
                            }
                        }
                    }
                }
                else -> {
                    val card1 = seeds[0]
                    val card2 = seeds[1]
                    val card3 = conjugateCard(card1, card2, mode)
                    if (card3 in deck) {
                        results += listOf(card1, card2, card3)
                    }
                }
            }
        } else {
            when (seeds.size) {
                1 -> {
                    val seed = seeds.first()
                    val others = deck.filter { it != seed }
                    for (i in 0 until others.size) {
                        for (j in i + 1 until others.size) {
                            for (k in j + 1 until others.size) {
                                val candidate = listOf(seed, others[i], others[j], others[k])
                                if (checkSetUltra(candidate, mode)) {
                                    results += candidate
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val (seed1, seed2) = seeds
                    val others = deck.filter { it != seed1 && it != seed2 }
                    for (i in 0 until others.size) {
                        for (j in i + 1 until others.size) {
                            val candidate = listOf(seed1, seed2, others[i], others[j])
                            if (checkSetUltra(candidate, mode)) {
                                results += candidate
                            }
                        }
                    }
                }
                else -> {
                    val (seed1, seed2, seed3) = seeds
                    val others = deck.filter { it != seed1 && it != seed2 && it != seed3 }
                    for (other in others) {
                        val candidate = listOf(seed1, seed2, seed3, other)
                        if (checkSetUltra(candidate, mode)) {
                            results += candidate
                        }
                    }
                }
            }
        }

        return results
    }
}

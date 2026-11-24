package kr.dalmuti.game

import java.util.Collections
import java.util.Random

object Rules {
    const val JESTER = 13

    fun deck(random: Random = Random()): MutableList<Int> {
        val deck = buildList(80) {
            for (rank in 1..12) repeat(rank) { add(rank) }
            repeat(2) { add(JESTER) }
        }.toMutableList()
        Collections.shuffle(deck, random)
        return deck
    }

    /** Returns the effective rank, or zero when the selection is not a set. */
    fun rank(cards: List<Int>): Int {
        if (cards.isEmpty()) return 0
        var rank = JESTER
        for (card in cards) {
            if (card !in 1..JESTER) return 0
            if (card != JESTER) {
                if (rank != JESTER && rank != card) return 0
                rank = card
            }
        }
        return rank
    }

    fun legal(cards: List<Int>, pileRank: Int, pileCount: Int): Boolean {
        val rank = rank(cards)
        return rank != 0 && (pileCount == 0 || (cards.size == pileCount && rank < pileRank))
    }

    /** Weakest legal set; wildcards are used only when a natural set is unavailable. */
    fun botPlay(hand: List<Int>, pileRank: Int, pileCount: Int): List<Int> {
        val need = if (pileCount == 0) 1 else pileCount
        val ceiling = if (pileCount == 0) JESTER + 1 else pileRank
        val wild = hand.count { it == JESTER }
        for (useWild in 0..minOf(wild, need)) {
            for (rank in minOf(12, ceiling - 1) downTo 1) {
                val natural = need - useWild
                if (natural > 0 && hand.count { it == rank } >= natural) {
                    return buildList(need) {
                        repeat(natural) { add(rank) }
                        repeat(useWild) { add(JESTER) }
                    }
                }
            }
        }
        return if (JESTER < ceiling && wild >= need) List(need) { JESTER } else emptyList()
    }

    fun containsCards(hand: List<Int>, cards: List<Int>): Boolean {
        val copy = hand.toMutableList()
        return cards.all { copy.remove(it) }
    }
}

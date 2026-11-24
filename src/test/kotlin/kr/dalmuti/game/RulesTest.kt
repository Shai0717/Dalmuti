package kr.dalmuti.game

import java.util.Random

private fun checkRule(value: Boolean, message: String) {
    check(value) { message }
}

fun main() {
    val deck = Rules.deck(Random(1))
    checkRule(deck.size == 80, "deck size")
    for (rank in 1..12) checkRule(deck.count { it == rank } == rank, "rank $rank")
    checkRule(deck.count { it == 13 } == 2, "jokers")
    checkRule(Rules.legal(listOf(5, 5, 13), 6, 3), "wildcard matches set")
    checkRule(!Rules.legal(listOf(5, 6, 13), 7, 3), "mixed ranks")
    checkRule(!Rules.legal(listOf(5, 5), 5, 2), "equal rank")
    checkRule(!Rules.legal(listOf(4), 5, 2), "wrong count")
    checkRule(Rules.legal(listOf(13, 13), 0, 0), "joker lead")
    checkRule(!Rules.legal(listOf(13), 12, 1), "joker alone cannot beat 12")
    checkRule(Rules.botPlay(listOf(2, 8, 8, 13), 9, 2) == listOf(8, 8), "natural weakest set")
    checkRule(Rules.botPlay(listOf(2, 8, 13), 9, 2) == listOf(8, 13), "joker when needed")
    checkRule(Rules.botPlay(listOf(13), 12, 1).isEmpty(), "bot passes")
    checkRule(Rules.botPlay(listOf(13), 0, 0) == listOf(13), "bot leads joker")
    checkRule(!Rules.containsCards(listOf(1, 13), listOf(13, 13)), "multiplicity")

    val trick = Trick(4, 0)
    trick.play(listOf(9), true)
    checkRule(trick.turn == 1, "next after finisher")
    checkRule(!trick.pass() && !trick.pass() && trick.pass(), "all active players pass")
    checkRule(trick.turn == 1 && trick.count == 0, "lead passes to next active player")
    trick.play(listOf(8), false)
    checkRule(!trick.pass() && trick.pass(), "other active players pass")
    checkRule(trick.turn == 1, "unfinished last player leads")

    for (seats in 4..8) {
        for (seed in 0 until 100) simulate(seats, seed)
    }
    println("RulesTest passed")
}

private fun simulate(seats: Int, seed: Int) {
    val hands = List(seats) { mutableListOf<Int>() }
    val deck = Rules.deck(Random(seed.toLong()))
    deck.forEachIndexed { index, card -> hands[index % seats].add(card) }
    val trick = Trick(seats, 0)
    var moves = 0
    while (!trick.isFinished() && moves++ < 5000) {
        val hand = hands[trick.turn]
        val cards = Rules.botPlay(hand, trick.rank, trick.count)
        if (cards.isEmpty()) {
            checkRule(trick.count != 0, "bot must lead")
            trick.pass()
        } else {
            checkRule(Rules.legal(cards, trick.rank, trick.count), "bot produces legal play")
            checkRule(Rules.containsCards(hand, cards), "bot holds cards")
            cards.forEach { hand.remove(it) }
            trick.play(cards, hand.isEmpty())
        }
    }
    checkRule(trick.isFinished(), "round terminates, seats=$seats, seed=$seed")
    checkRule(trick.order().distinct().size == seats, "unique finishers")
}

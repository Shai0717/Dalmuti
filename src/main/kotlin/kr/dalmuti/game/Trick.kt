package kr.dalmuti.game

/** Turn and finishing order for one dealt round. */
class Trick(players: Int, leader: Int) {
    private val finished = BooleanArray(players)
    private val finishOrder = mutableListOf<Int>()
    var turn: Int = leader
        private set
    private var lastPlayer = -1
    var rank: Int = 0
        private set
    var count: Int = 0
        private set
    private var passes = 0

    init {
        require(players >= 2 && leader in 0 until players)
    }

    fun order(): List<Int> = finishOrder.toList()
    fun isFinished(): Boolean = finishOrder.size == finished.size

    fun play(cards: List<Int>, emptied: Boolean) {
        check(!isFinished() && Rules.legal(cards, rank, count)) { "Invalid play" }
        rank = Rules.rank(cards)
        count = cards.size
        passes = 0
        lastPlayer = turn
        if (emptied) {
            finished[turn] = true
            finishOrder.add(turn)
            if (finishOrder.size == finished.size - 1) {
                val last = finished.indices.first { !finished[it] }
                finished[last] = true
                finishOrder.add(last)
                return
            }
        }
        turn = next(turn)
    }

    fun pass(): Boolean {
        check(!isFinished() && count != 0) { "Cannot pass on an empty pile" }
        passes++
        val required = activeCount() - if (finished[lastPlayer]) 0 else 1
        if (passes >= required) {
            turn = if (finished[lastPlayer]) next(lastPlayer) else lastPlayer
            count = 0
            rank = 0
            passes = 0
            lastPlayer = -1
            return true
        }
        turn = next(turn)
        return false
    }

    private fun activeCount() = finished.size - finishOrder.size

    private fun next(from: Int): Int {
        var index = from
        do {
            index = (index + 1) % finished.size
        } while (finished[index])
        return index
    }
}

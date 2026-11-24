package kr.dalmuti.game

import kr.dalmuti.display.TableDisplay
import kr.dalmuti.participant.BotParticipant
import kr.dalmuti.participant.DalmutiParticipant
import kr.dalmuti.participant.HumanParticipant
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Collections
import java.util.Random
import java.util.UUID

class GameSession(
    private val plugin: JavaPlugin,
    center: Location,
    private val seats: Int,
    private val revolutionSeconds: Int
) {
    private enum class Phase { LOBBY, REVOLUTION, TAX, PLAY, ROUND_END, STOPPED }

    private val display = TableDisplay(plugin, center)
    private val random = Random()
    private val participants = mutableListOf<DalmutiParticipant>()
    private val returnLocations = mutableMapOf<UUID, Location>()
    private val selections = mutableMapOf<UUID, MutableSet<Int>>()
    private var nextOrder: List<DalmutiParticipant>? = null
    private var phase = Phase.LOBBY
    private var trick: Trick? = null
    private var tick = 0L
    private var deadline = 0L
    private var botAt = 0L
    private var round = 0
    private var taxStep = 0

    init {
        require(seats in 4..8 && revolutionSeconds >= 1) { "Invalid game configuration" }
    }

    fun join(player: Player): Boolean {
        if (phase != Phase.LOBBY) {
            player.sendMessage("§c이미 게임이 시작되었습니다.")
            return false
        }
        if (player.world != display.center().world) {
            player.sendMessage("§c테이블과 같은 월드에서 참가하세요.")
            return false
        }
        if (participant(player) != null) {
            player.sendMessage("§e이미 참가했습니다.")
            return false
        }
        if (participants.size >= seats) {
            player.sendMessage("§c자리가 가득 찼습니다.")
            return false
        }
        participants += HumanParticipant(player)
        announce("${player.name} 참가 (${participants.size}/$seats)")
        return true
    }

    fun start(sender: Player): Boolean {
        if (phase != Phase.LOBBY) {
            sender.sendMessage("§c이미 게임이 시작되었습니다.")
            return false
        }
        if (participants.isEmpty()) {
            sender.sendMessage("§c먼저 /dalmuti join 으로 참가하세요.")
            return false
        }
        for (participant in participants) {
            val human = participant as HumanParticipant
            if (!human.player.isOnline) {
                sender.sendMessage("§c오프라인 참가자가 있습니다.")
                return false
            }
            returnLocations[participant.id] = human.player.location.clone()
        }
        var botNumber = 1
        while (participants.size < seats) participants += BotParticipant(botNumber++)
        beginRound()
        return true
    }

    fun isMember(player: Player): Boolean = participant(player) != null
    fun isRunning(): Boolean = phase != Phase.LOBBY && phase != Phase.STOPPED

    fun seatOf(player: Player): Location? {
        val participant = participant(player) ?: return null
        if (!isRunning()) return null
        return display.seat(participants.indexOf(participant), seats)
    }

    private fun participant(player: Player): DalmutiParticipant? =
        participants.firstOrNull { it.id == player.uniqueId }

    private fun beginRound() {
        nextOrder?.let {
            participants.clear()
            participants.addAll(it)
            nextOrder = null
        }
        round++
        selections.clear()
        val deck = Rules.deck(random)
        participants.forEach { it.hand.clear() }
        deck.forEachIndexed { index, card -> participants[index % seats].hand += card }
        participants.forEach { it.hand.sort() }
        display.build(participants)
        refreshHands()
        phase = Phase.REVOLUTION
        deadline = tick + revolutionSeconds * 20L
        botAt = tick + 25
        announce("§6${round}라운드! §f광대 2장을 가진 사람은 ${revolutionSeconds}초 안에 /dalmuti revolution 을 사용할 수 있습니다.")
    }

    fun revolution(player: Player) {
        val participant = participant(player)
        if (participant == null) {
            player.sendMessage("§c게임 참가자가 아닙니다.")
            return
        }
        if (phase != Phase.REVOLUTION) {
            player.sendMessage("§c지금은 혁명 선언 시간이 아닙니다.")
            return
        }
        if (participant.hand.count { it == Rules.JESTER } != 2) {
            player.sendMessage("§c혁명에는 광대 2장이 필요합니다.")
            return
        }
        revolt(participant)
    }

    private fun revolt(participant: DalmutiParticipant) {
        announce("§d§l혁명! §f${participant.name} — 이번 라운드의 세금이 취소됩니다.")
        if (participants.indexOf(participant) == seats - 1) {
            Collections.swap(participants, 0, seats - 1)
            display.center().world.strikeLightningEffect(display.center())
            announce("§6대 평민과 대 달무티가 자리를 바꿉니다!")
            display.build(participants)
            refreshHands()
        }
        startPlay()
    }

    private fun beginTax() {
        if (round == 1) {
            startPlay()
            return
        }
        phase = Phase.TAX
        taxStep = 0
        transferStrongest(seats - 1, 0, 2)
        transferStrongest(seats - 2, 1, 1)
        advanceTax()
    }

    private fun transferStrongest(from: Int, to: Int, count: Int) {
        val source = participants[from]
        val target = participants[to]
        source.hand.sort()
        val cards = source.hand.take(count)
        transfer(source, target, cards)
        announce("§e${source.name} → ${target.name}: 세금 ${count}장")
        animateTransfer(from, to, count)
    }

    private fun transfer(from: DalmutiParticipant, to: DalmutiParticipant, cards: List<Int>) {
        check(Rules.containsCards(from.hand, cards)) { "Card transfer mismatch" }
        cards.forEach { from.hand.remove(it) }
        to.hand.addAll(cards)
        to.hand.sort()
        selections.remove(from.id)
        selections.remove(to.id)
        refreshHands()
    }

    private fun advanceTax() {
        if (taxStep >= 2) {
            startPlay()
            return
        }
        val giver = participants[taxStep]
        val count = if (taxStep == 0) 2 else 1
        announce("§e${giver.name} 님이 돌려줄 카드 ${count}장을 선택합니다.")
        if (giver.bot) botAt = tick + 20 + random.nextInt(11)
        else (giver as HumanParticipant).player.sendMessage("§e카드를 선택한 뒤 웅크리고 클릭하세요.")
    }

    private fun returnTax(giver: DalmutiParticipant, cards: List<Int>) {
        val count = if (taxStep == 0) 2 else 1
        if (cards.size != count || !Rules.containsCards(giver.hand, cards)) {
            (giver as? HumanParticipant)?.player?.sendMessage("§c정확히 ${count}장을 선택하세요.")
            return
        }
        val recipient = if (taxStep == 0) seats - 1 else seats - 2
        val source = taxStep
        transfer(giver, participants[recipient], cards)
        animateTransfer(source, recipient, count)
        announce("§e${giver.name} → ${participants[recipient].name}: 답례 ${count}장")
        taxStep++
        advanceTax()
    }

    private fun animateTransfer(from: Int, to: Int, count: Int) {
        repeat(count) { index ->
            display.fly(
                display.seat(from, seats).add(0.0, 1.5 + index * 0.1, 0.0),
                display.seat(to, seats).add(0.0, 1.5, 0.0),
                "세금"
            )
        }
    }

    private fun startPlay() {
        selections.clear()
        phase = Phase.PLAY
        trick = Trick(seats, 0)
        display.pile(0, 0)
        announce("§a${participants[0].name} 님이 선공합니다.")
        nextTurn()
    }

    fun click(player: Player, submit: Boolean) {
        val participant = participant(player) ?: return
        if (!isRunning()) return
        if (submit) {
            val cards = selectedCards(participant)
            when {
                phase == Phase.TAX && participants[taxStep] == participant -> returnTax(participant, cards)
                phase == Phase.PLAY && participants[trick!!.turn] == participant -> play(participant, cards)
                else -> player.sendMessage("§c지금은 행동할 차례가 아닙니다.")
            }
            return
        }
        val index = display.hovered(participant.id)
        if (index !in participant.hand.indices) return
        val selected = selections.getOrPut(participant.id) { mutableSetOf() }
        if (!selected.add(index)) selected.remove(index)
    }

    private fun selectedCards(participant: DalmutiParticipant): List<Int> =
        selections[participant.id].orEmpty()
            .filter { it in participant.hand.indices }
            .map { participant.hand[it] }

    private fun play(participant: DalmutiParticipant, cards: List<Int>) {
        val trick = trick!!
        if (!Rules.containsCards(participant.hand, cards) || !Rules.legal(cards, trick.rank, trick.count)) {
            (participant as? HumanParticipant)?.player?.sendMessage("§c같은 숫자와 광대로 구성된 유효한 세트를 선택하세요.")
            return
        }
        val rank = Rules.rank(cards)
        val seat = participants.indexOf(participant)
        cards.forEach { participant.hand.remove(it) }
        selections.remove(participant.id)
        repeat(cards.size) { index ->
            display.fly(
                display.seat(seat, seats).add(0.0, 1.5 + index * 0.05, 0.0),
                display.center(),
                TableDisplay.rankName(rank)
            )
        }
        trick.play(cards, participant.hand.isEmpty())
        announce("§f${participant.name}: ${TableDisplay.rankName(rank)} §f${cards.size}장")
        refreshHands()
        display.pile(trick.rank, trick.count)
        if (trick.isFinished()) endRound() else nextTurn()
    }

    fun pass(player: Player) {
        val participant = participant(player)
        val trick = trick
        if (participant == null || phase != Phase.PLAY || participants[trick!!.turn] != participant) {
            player.sendMessage("§c지금은 패스할 차례가 아닙니다.")
            return
        }
        if (trick.count == 0) {
            player.sendMessage("§c선공에서는 패스할 수 없습니다.")
            return
        }
        passCurrent()
    }

    private fun passCurrent() {
        val trick = trick!!
        val participant = participants[trick.turn]
        selections.remove(participant.id)
        val cleared = trick.pass()
        announce("§7${participant.name} 패스${if (cleared) " — 테이블 정리!" else ""}")
        if (cleared) display.pile(0, 0)
        nextTurn()
    }

    private fun nextTurn() {
        val participant = participants[trick!!.turn]
        announce("§b차례: ${participant.name}")
        if (participant.bot) botAt = tick + 20 + random.nextInt(11)
    }

    private fun endRound() {
        val ordered = trick!!.order().map { participants[it] }
        nextOrder = ordered
        announce("§6라운드 종료: ${ordered.joinToString(" §7> §6") { it.name }}")
        phase = Phase.ROUND_END
        deadline = tick + 100
    }

    fun tick() {
        if (phase == Phase.STOPPED || phase == Phase.LOBBY) return
        tick++
        display.tickFlights()
        participants.filterIsInstance<HumanParticipant>().forEach {
            display.updateHand(it, selections[it.id].orEmpty())
        }
        when (phase) {
            Phase.REVOLUTION -> {
                if (tick >= botAt) {
                    botAt = Long.MAX_VALUE
                    participants.firstOrNull { it.bot && it.hand.count { card -> card == Rules.JESTER } == 2 }
                        ?.let {
                            revolt(it)
                            return
                        }
                }
                if (tick >= deadline) beginTax()
            }
            Phase.TAX -> if (tick >= botAt && participants[taxStep].bot) {
                val giver = participants[taxStep]
                val cards = giver.hand.sortedDescending().take(if (taxStep == 0) 2 else 1)
                returnTax(giver, cards)
            }
            Phase.PLAY -> if (tick >= botAt && participants[trick!!.turn].bot) {
                val bot = participants[trick!!.turn]
                val cards = Rules.botPlay(bot.hand, trick!!.rank, trick!!.count)
                if (cards.isEmpty()) passCurrent() else play(bot, cards)
            }
            Phase.ROUND_END -> if (tick >= deadline) beginRound()
            Phase.LOBBY, Phase.STOPPED -> Unit
        }
    }

    private fun refreshHands() {
        display.updateBadges(participants)
        participants.filterIsInstance<HumanParticipant>().forEach(display::hand)
    }

    private fun announce(message: String) {
        participants.filterIsInstance<HumanParticipant>()
            .filter { it.player.isOnline }
            .forEach { it.player.sendMessage("§8[§6달무티§8] §r$message") }
    }

    fun stop(reason: String) {
        if (phase == Phase.STOPPED) return
        announce(reason)
        phase = Phase.STOPPED
        display.clear()
        participants.filterIsInstance<HumanParticipant>().forEach { human ->
            val old = returnLocations[human.id]
            if (old != null && human.player.isOnline) {
                human.player.teleportAsync(old).whenComplete { success, error ->
                    if (error != null) plugin.logger.warning("Could not return ${human.name}: ${error.message}")
                    else if (success != true) plugin.logger.warning("Could not return ${human.name} to their original location")
                }
            }
        }
        participants.clear()
        returnLocations.clear()
        selections.clear()
    }
}

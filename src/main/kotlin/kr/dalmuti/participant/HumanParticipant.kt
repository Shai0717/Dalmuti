package kr.dalmuti.participant

import org.bukkit.entity.Player
import java.util.UUID

class HumanParticipant(val player: Player) : DalmutiParticipant {
    override val id: UUID get() = player.uniqueId
    override val name: String get() = player.name
    override val bot: Boolean get() = false
    override val hand: MutableList<Int> = mutableListOf()
}

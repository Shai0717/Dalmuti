package kr.dalmuti.participant

import java.util.UUID

class BotParticipant(number: Int) : DalmutiParticipant {
    override val id: UUID = UUID.randomUUID()
    override val name: String = "Bot $number"
    override val bot: Boolean = true
    override val hand: MutableList<Int> = mutableListOf()
}

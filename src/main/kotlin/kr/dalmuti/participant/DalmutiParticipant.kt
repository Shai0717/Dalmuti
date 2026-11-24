package kr.dalmuti.participant

import java.util.UUID

interface DalmutiParticipant {
    val id: UUID
    val name: String
    val bot: Boolean
    val hand: MutableList<Int>
}

package kr.dalmuti.command

import io.github.Shai0717.kolor.kolorCommand
import kr.dalmuti.DalmutiPlugin

class DalmutiCommand(private val plugin: DalmutiPlugin) {
    fun register() {
        plugin.kolorCommand("dalmuti") {
            literal("setup") {
                executesOnEntity { player, _ ->
                    if (player.hasPermission("dalmuti.admin")) plugin.dispatch { plugin.setup(player) }
                    else player.sendMessage("§c관리자 권한이 필요합니다.")
                }
            }
            literal("join") {
                executesOnEntity { player, _ -> plugin.dispatch { plugin.join(player) } }
            }
            literal("start") {
                executesOnEntity { player, _ ->
                    if (player.hasPermission("dalmuti.admin")) plugin.dispatch { plugin.start(player) }
                    else player.sendMessage("§c관리자 권한이 필요합니다.")
                }
            }
            literal("stop") {
                executesOnEntity { player, _ ->
                    if (player.hasPermission("dalmuti.admin")) plugin.dispatch { plugin.stop(player) }
                    else player.sendMessage("§c관리자 권한이 필요합니다.")
                }
            }
            literal("revolution") {
                executesOnEntity { player, _ -> plugin.dispatch { plugin.revolution(player) } }
            }
        }
    }
}

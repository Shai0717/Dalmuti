package kr.dalmuti

import io.github.Shai0717.kolor.KolorPlugin
import io.github.Shai0717.kolor.KolorScheduler
import kr.dalmuti.command.DalmutiCommand
import kr.dalmuti.display.TableDisplay
import kr.dalmuti.game.GameSession
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot

class DalmutiPlugin : KolorPlugin(), Listener {
    private val scheduler by lazy { KolorScheduler(this) }
    @Volatile private var session: GameSession? = null

    override fun onEnable() {
        saveDefaultConfig()
        Bukkit.getWorlds().forEach { world ->
            world.entities.filter { TableDisplay.TAG in it.scoreboardTags }.forEach { entity ->
                with(scheduler) {
                    entity.runOn { entity.remove() }
                }
            }
        }
        DalmutiCommand(this).register()
        server.pluginManager.registerEvents(this, this)
        scheduler.runGlobal(delayTicks = 1, periodTicks = 1) { session?.tick() }
    }

    override fun onKolorDisable() {
        session?.stop("§e플러그인이 종료되어 게임이 중지되었습니다.")
        session = null
    }

    fun dispatch(action: () -> Unit) {
        scheduler.runGlobal(task = action)
    }

    fun setup(player: Player) {
        if (session?.isRunning() == true) {
            player.sendMessage("§c먼저 게임을 중지하세요.")
            return
        }
        session?.stop("§e테이블 설정이 변경되었습니다.")
        session = null
        val center = player.location
        getConfig().set("arena.world", center.world.name)
        getConfig().set("arena.x", center.x)
        getConfig().set("arena.y", center.y)
        getConfig().set("arena.z", center.z)
        saveConfig()
        player.sendMessage("§a이 위치를 테이블 중심으로 설정했습니다. 반경 6블록의 평평하고 안전한 공간을 확보하세요.")
    }

    private fun getOrCreateSession(player: Player): GameSession? {
        session?.let { return it }
        val worldName = getConfig().getString("arena.world")
        val world: World? = worldName?.let(Bukkit::getWorld)
        if (world == null) {
            player.sendMessage("§c먼저 로드된 월드에서 /dalmuti setup 을 실행하세요.")
            return null
        }
        val seats = getConfig().getInt("participants", 4)
        val revolutionSeconds = getConfig().getInt("revolution-seconds", 10)
        if (seats !in 4..8 || revolutionSeconds !in 1..300) {
            player.sendMessage("§cconfig.yml: participants는 4–8, revolution-seconds는 1–300이어야 합니다.")
            return null
        }
        val center = Location(
            world,
            getConfig().getDouble("arena.x"),
            getConfig().getDouble("arena.y"),
            getConfig().getDouble("arena.z")
        )
        return GameSession(this, center, seats, revolutionSeconds).also { session = it }
    }

    fun join(player: Player) {
        getOrCreateSession(player)?.join(player)
    }

    fun start(player: Player) {
        getOrCreateSession(player)?.start(player)
    }

    fun revolution(player: Player) {
        val current = session
        if (current == null) player.sendMessage("§c진행 중인 게임이 없습니다.")
        else current.revolution(player)
    }

    fun stop(player: Player) {
        val current = session
        if (current == null) {
            player.sendMessage("§e진행 중인 게임이 없습니다.")
            return
        }
        current.stop("§e관리자가 게임을 중지했습니다.")
        session = null
        player.sendMessage("§a달무티 게임을 중지했습니다.")
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (session?.isRunning() != true || session?.isMember(event.player) != true) return
        if (event.hand != EquipmentSlot.HAND || event.action == Action.PHYSICAL) return
        event.isCancelled = true
        val player = event.player
        val submit = player.isSneaking
        dispatch { session?.click(player, submit) }
    }

    @EventHandler
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        if (session?.isRunning() != true || session?.isMember(event.player) != true) return
        event.isCancelled = true
        val player = event.player
        dispatch { session?.pass(player) }
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        if (event is PlayerTeleportEvent) return
        val seat = session?.seatOf(event.player) ?: return
        val destination = event.to
        if (destination.x != seat.x || destination.y != seat.y || destination.z != seat.z) {
            val locked = seat.clone().apply {
                yaw = destination.yaw
                pitch = destination.pitch
            }
            event.to = locked
            event.player.fallDistance = 0f
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        if (session?.isMember(event.player) != true) return
        dispatch {
            session?.stop("§c참가자가 나가서 게임이 중지되었습니다.")
            session = null
        }
    }
}

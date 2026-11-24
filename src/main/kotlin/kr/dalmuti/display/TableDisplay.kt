package kr.dalmuti.display

import io.github.Shai0717.kolor.KolorDisplay
import io.github.Shai0717.kolor.KolorScheduler
import kr.dalmuti.participant.BotParticipant
import kr.dalmuti.participant.DalmutiParticipant
import kr.dalmuti.participant.HumanParticipant
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TableDisplay(plugin: Plugin, center: Location) {
    companion object {
        const val TAG = "dalmuti_prototype_entity"
        private val legacy = LegacyComponentSerializer.legacySection()
        fun rankName(rank: Int): String = if (rank == 13) "★ 광대" else "◆ $rank"
    }

    private data class HandCard(val entity: TextDisplay, var position: Location, var label: String)
    private data class Flight(val entity: TextDisplay, val from: Location, val to: Location, var age: Int = 0)

    private val plugin = plugin
    private val center = center.clone()
    private val scheduler = KolorScheduler(plugin)
    private val kolorDisplay = KolorDisplay(plugin)
    private val generation = AtomicInteger()
    private val entities = ConcurrentHashMap.newKeySet<Entity>()
    private val hands = ConcurrentHashMap<UUID, MutableList<HandCard>>()
    private val handGeneration = ConcurrentHashMap<UUID, Int>()
    private val hovered = ConcurrentHashMap<UUID, Int>()
    private val badges = ConcurrentHashMap<UUID, TextDisplay>()
    private val flights = CopyOnWriteArrayList<Flight>()
    @Volatile private var pile: TextDisplay? = null

    fun seat(index: Int, total: Int): Location {
        val angle = 2 * Math.PI * index / total
        val height = when (index) {
            0 -> 1.0
            total - 1 -> 0.05
            else -> 0.3
        }
        val result = center.clone().add(kotlin.math.sin(angle) * 4.5, height, kotlin.math.cos(angle) * 4.5)
        val direction = center.toVector().subtract(result.toVector()).setY(0.0)
        result.direction = direction
        return result
    }

    private fun <T : Entity> track(entity: T, expectedGeneration: Int): T {
        if (generation.get() != expectedGeneration) {
            when (entity) {
                is Display -> kolorDisplay.remove(entity)
                else -> with(scheduler) { entity.runOn { entity.remove() } }
            }
            return entity
        }
        entity.isPersistent = false
        entity.addScoreboardTag(TAG)
        entities += entity
        return entity
    }

    private fun text(location: Location, message: String, expectedGeneration: Int = generation.get()):
        CompletableFuture<TextDisplay> =
        kolorDisplay.text(location, legacy.deserialize(message)).thenApply { entity ->
            entity.text(legacy.deserialize(message))
            entity.billboard = Display.Billboard.CENTER
            entity.backgroundColor = Color.fromARGB(180, 20, 20, 25)
            entity.isSeeThrough = false
            entity.isShadowed = true
            track(entity, expectedGeneration)
        }

    private fun text(
        viewer: Player,
        location: Location,
        message: String,
        expectedGeneration: Int = generation.get()
    ): CompletableFuture<TextDisplay> =
        kolorDisplay.text(viewer, location, legacy.deserialize(message)).thenApply { entity ->
            entity.text(legacy.deserialize(message))
            entity.billboard = Display.Billboard.CENTER
            entity.backgroundColor = Color.fromARGB(180, 20, 20, 25)
            entity.isSeeThrough = false
            entity.isShadowed = true
            track(entity, expectedGeneration)
        }

    fun build(participants: List<DalmutiParticipant>) {
        clear()
        val expectedGeneration = generation.get()
        text(center.clone().add(0.0, 1.4, 0.0), "§6§lDALMUTI\n§7빈 테이블", expectedGeneration)
            .thenAccept { if (generation.get() == expectedGeneration) pile = it }
        for ((index, participant) in participants.withIndex()) {
            val location = seat(index, participants.size)
            val material = when (index) {
                0 -> Material.GOLD_BLOCK
                participants.lastIndex -> Material.MUD
                else -> Material.SPRUCE_PLANKS
            }
            val blockLocation = location.clone().add(-0.5, -1.0, -0.5)
            kolorDisplay.block(blockLocation, material.createBlockData())
                .thenAccept { track(it, expectedGeneration) }
            if (index == participants.lastIndex) {
                kolorDisplay.block(blockLocation.clone().add(0.0, 0.0, 1.0), Material.IRON_BARS.createBlockData())
                    .thenAccept { track(it, expectedGeneration) }
            }
            if (index == 0) {
                kolorDisplay.item(location.clone().add(0.0, 2.7, 0.0), ItemStack(Material.GOLDEN_HELMET))
                    .thenAccept {
                        it.billboard = Display.Billboard.CENTER
                        track(it, expectedGeneration)
                    }
            }
            if (participant is BotParticipant) spawnBot(participant, location, expectedGeneration)
            else if (participant is HumanParticipant) {
                with(scheduler) {
                    participant.player.runOn {
                        participant.player.teleportAsync(location)
                    }
                }
            }
            text(location.clone().add(0.0, 2.3, 0.0), "", expectedGeneration).thenAccept {
                if (generation.get() == expectedGeneration) {
                    badges[participant.id] = it
                    updateBadge(it, index, participant, participants.size)
                }
            }
        }
    }

    private fun spawnBot(participant: BotParticipant, location: Location, expectedGeneration: Int) {
        with(scheduler) {
            location.runAt {
                val stand = location.world.spawn(location, ArmorStand::class.java)
                stand.setGravity(false)
                stand.isInvulnerable = true
                stand.setBasePlate(false)
                stand.setArms(true)
                stand.equipment.setHelmet(ItemStack(Material.PLAYER_HEAD))
                stand.customName(Component.text(participant.name, NamedTextColor.YELLOW))
                stand.isCustomNameVisible = true
                track(stand, expectedGeneration)
            }
        }
    }

    fun updateBadges(participants: List<DalmutiParticipant>) {
        participants.forEachIndexed { index, participant ->
            badges[participant.id]?.let { updateBadge(it, index, participant, participants.size) }
        }
    }

    private fun updateBadge(
        badge: TextDisplay,
        index: Int,
        participant: DalmutiParticipant,
        total: Int
    ) {
        val role = when (index) {
            0 -> "§6대 달무티"
            1 -> "§e소 달무티"
            total - 2 -> "§7소 평민"
            total - 1 -> "§8대 평민"
            else -> "§f상인"
        }
        kolorDisplay.update(badge) {
            (it as TextDisplay).text(legacy.deserialize("$role\n§f${participant.name} §7| §f${participant.hand.size}장"))
        }
    }

    fun pile(rank: Int, count: Int) {
        pile?.let {
            val message = if (count == 0) "§6§lDALMUTI\n§7빈 테이블"
            else "§6§lDALMUTI\n§f${rankName(rank)} §7× §f$count"
            kolorDisplay.update(it) { display -> (display as TextDisplay).text(legacy.deserialize(message)) }
        }
    }

    fun hand(participant: HumanParticipant) {
        removeHand(participant.id)
        val player = participant.player
        val expectedGeneration = handGeneration.compute(participant.id) { _, value -> (value ?: 0) + 1 }!!
        with(scheduler) {
            player.runOn(retiredCallback = { removeHand(participant.id) }) {
                val eye = player.eyeLocation
                val cards = CopyOnWriteArrayList<HandCard>()
                hands[participant.id] = cards
                participant.hand.forEach { rank ->
                    val label = rankName(rank)
                    text(player, eye, label).thenAccept { entity ->
                        if (handGeneration[participant.id] != expectedGeneration) {
                            kolorDisplay.remove(entity)
                        } else {
                            cards += HandCard(entity, eye.clone(), label)
                        }
                    }
                }
            }
        }
    }

    fun updateHand(participant: HumanParticipant, selected: Set<Int>) {
        val player = participant.player
        with(scheduler) {
            player.runOn(retiredCallback = { hovered.remove(participant.id) }) {
                val cards = hands[participant.id] ?: return@runOn
                if (cards.size != participant.hand.size) return@runOn
                val eye = player.eyeLocation
                var forward = center.toVector().subtract(eye.toVector()).setY(0.0)
                if (forward.lengthSquared() < 0.01) forward = eye.direction.setY(0.0)
                if (forward.lengthSquared() < 0.01) forward = Vector(0.0, 0.0, 1.0)
                forward.normalize()
                val right = Vector(forward.z, 0.0, -forward.x)
                val spacing = minOf(0.31, 4.2 / maxOf(1, cards.size - 1))
                val positions = ArrayList<Location>(cards.size)
                var target = -1
                var best = 0.16 * 0.16
                val look = eye.direction.normalize()
                cards.indices.forEach { index ->
                    val offset = (index - (cards.size - 1) / 2.0) * spacing
                    val position = eye.clone()
                        .add(forward.clone().multiply(2.8 - kotlin.math.abs(offset) * 0.12))
                        .add(right.clone().multiply(offset))
                        .add(0.0, -0.35 - offset * offset * 0.025, 0.0)
                    positions += position
                    val delta = position.toVector().subtract(eye.toVector())
                    val along = delta.dot(look)
                    if (along > 0) {
                        val distance = delta.subtract(look.clone().multiply(along)).lengthSquared()
                        if (distance < best) {
                            best = distance
                            target = index
                        }
                    }
                }
                hovered[participant.id] = target
                cards.indices.forEach { index ->
                    val raised = index in selected || index == target
                    val position = positions[index].clone().add(0.0, if (raised) 0.15 else 0.0, 0.0)
                    val card = cards[index]
                    if (card.position.world != position.world || card.position.distanceSquared(position) > 0.0001) {
                        kolorDisplay.teleport(card.entity, position)
                        card.position = position
                    }
                    val label = (if (index in selected) "§a" else "§f") + rankName(participant.hand[index])
                    if (card.label != label) {
                        kolorDisplay.update(card.entity) {
                            (it as TextDisplay).text(legacy.deserialize(label))
                        }
                        card.label = label
                    }
                }
            }
        }
    }

    fun hovered(player: UUID): Int = hovered[player] ?: -1

    fun fly(from: Location, to: Location, label: String) {
        val expectedGeneration = generation.get()
        text(from, "§e$label", expectedGeneration).thenAccept { entity ->
            if (generation.get() == expectedGeneration) flights += Flight(entity, from.clone(), to.clone())
        }
    }

    fun tickFlights() {
        val iterator = flights.iterator()
        while (iterator.hasNext()) {
            val flight = iterator.next()
            flight.age++
            if (flight.age >= 12) {
                flights.remove(flight)
                kolorDisplay.remove(flight.entity)
                entities.remove(flight.entity)
            } else {
                val fraction = flight.age / 12.0
                val delta = flight.to.toVector().subtract(flight.from.toVector()).multiply(fraction)
                kolorDisplay.teleport(
                    flight.entity,
                    flight.from.clone().add(delta).add(0.0, kotlin.math.sin(Math.PI * fraction) * 0.7, 0.0)
                )
            }
        }
    }

    fun center(): Location = center.clone().add(0.0, 1.4, 0.0)

    private fun removeHand(id: UUID) {
        handGeneration.compute(id) { _, value -> (value ?: 0) + 1 }
        hovered.remove(id)
        hands.remove(id)?.forEach {
            kolorDisplay.remove(it.entity)
            entities.remove(it.entity)
        }
    }

    fun clear() {
        generation.incrementAndGet()
        entities.toList().forEach { entity ->
            when (entity) {
                is Display -> kolorDisplay.remove(entity)
                else -> with(scheduler) {
                    entity.runOn(retiredCallback = {
                        plugin.logger.fine("Dalmuti entity retired during cleanup")
                    }) { entity.remove() }
                }
            }
        }
        entities.clear()
        hands.keys.toList().forEach(::removeHand)
        hovered.clear()
        badges.clear()
        flights.clear()
        pile = null
    }
}

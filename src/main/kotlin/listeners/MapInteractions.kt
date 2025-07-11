package twizzy.tech.listeners

import com.github.shynixn.mccoroutine.minestom.scope
import kotlinx.coroutines.launch
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import twizzy.tech.game.RegionManager
import twizzy.tech.player.PlayerData
import twizzy.tech.util.Worlds
import twizzy.tech.util.YamlFactory

class MapInteractions(private val minecraftServer: MinecraftServer, private val regionManager: RegionManager, private val worlds: Worlds) {

    init {
        // Register the event listener to handle player block breaking
        MinecraftServer.getGlobalEventHandler().addListener(PlayerBlockBreakEvent::class.java) { event ->
            val player = event.player

            // Always allow creative mode players to break blocks
            if (player.gameMode == GameMode.CREATIVE) {
                return@addListener
            }

            val blockPos = event.blockPosition
            val regions = regionManager.getRegionsAt(worlds.getWorldNameFromInstance(player.instance), Pos(blockPos))

            // Check if any of the regions at this position have the "break" flag
            val canBreak = regions.any { it.hasFlag("break") }

            if (canBreak) {
                // Get the block type before breaking
                val blockState = player.instance.getBlock(blockPos)
                val blockId = blockState.name()

                // Decrease durability
                if (player.itemInMainHand.get(ItemComponent.UNBREAKABLE) == null && player.itemInMainHand.get(ItemComponent.TOOL) != null) {
                    val durability = player.itemInMainHand.builder()
                        .set(ItemComponent.DAMAGE, player.itemInMainHand.get(ItemComponent.DAMAGE)?.inc())
                        .build()
                    if (player.itemInMainHand.get(ItemComponent.DAMAGE) == player.itemInMainHand.get(ItemComponent.MAX_DAMAGE)) {
                        player.itemInMainHand = ItemStack.of(Material.AIR)
                        player.playSound(
                            Sound.sound(
                                Key.key("minecraft:entity.item.break"),
                                Sound.Source.PLAYER,
                                1.0f,
                                1.0f
                            )
                        )
                    } else {
                        player.itemInMainHand = durability
                    }
                }
                minecraftServer.scope.launch {
                    // Increment blocks mined counter
                    PlayerData.incrementBlocksMined(player.uuid, 1)

                    // Add the broken block to the player's backpack
                    PlayerData.addToBackpack(player.uuid, blockId, 1)
                }

            } else {
                // Cancel the block break if no region has break permission
                event.isCancelled = true

                // Show "can't break" particles since action is denied
                val particlePacket = ParticlePacket(
                    Particle.SMOKE,
                    Pos(blockPos.x() + 0.5, blockPos.y() + 0.5, blockPos.z() + 0.5),
                    Vec(0.2, 0.2, 0.2),
                    0.05f,
                    8
                )
                player.sendPacket(particlePacket)

                // Send message about denied action
                if (regions.isNotEmpty()) {
                    val message = YamlFactory.getMessage("commands.region.protection.cannot_break",)
                    player.sendMessage(Component.text(message))
                }
            }
        }

        MinecraftServer.getGlobalEventHandler().addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val player = event.player

            // Always allow creative mode players to place blocks
            if (player.gameMode == GameMode.CREATIVE) {
                return@addListener
            }

            val blockPos = event.blockPosition
            val regions = regionManager.getRegionsAt(worlds.getWorldNameFromInstance(player.instance), Pos(blockPos))

            // Check if any of the regions at this position have the "place" flag
            val canPlace = regions.any { it.hasFlag("place") }

            if (!canPlace) {
                // Cancel the block placement if no region has place permission
                event.isCancelled = true

                // Show "can't place" particles since action is denied
                val particlePacket = ParticlePacket(
                    Particle.SMOKE,
                    Pos(blockPos.x() + 0.5, blockPos.y() + 0.5, blockPos.z() + 0.5),
                    Vec(0.2, 0.2, 0.2),
                    0.05f,
                    8
                )
                player.sendPacket(particlePacket)

                // Send message about denied action
                if (regions.isNotEmpty()) {
                    val message = YamlFactory.getMessage("commands.region.protection.cannot_place",)
                    player.sendMessage(Component.text(message))
                }
            }
            // If canPlace is true, the event will proceed normally (not cancelled) without particles
        }
    }
}
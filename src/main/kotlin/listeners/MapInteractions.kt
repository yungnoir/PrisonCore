package twizzy.tech.listeners

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.network.packet.server.play.ParticlePacket
import net.minestom.server.particle.Particle
import twizzy.tech.game.PlayerData
import twizzy.tech.game.RegionManager
import twizzy.tech.util.Worlds

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

                // Allow the block to be broken - no particles needed for success
                val data = PlayerData(player.uuid)
                data.incrementBlocksBroken()

                data.addBlockToBackpack(event.block.name(), 1)

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
                    player.sendMessage("You cannot break blocks in this region.")
                }
            }
        }

        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, PlayerBlockPlaceEvent::class.java) { event ->
            val player = event.player

            // Always allow creative mode players to place blocks
            if (player.gameMode == GameMode.CREATIVE) {
                return@addSuspendingListener
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
                    player.sendMessage("You cannot place blocks in this region.")
                }
            }
            // If canPlace is true, the event will proceed normally (not cancelled) without particles
        }
    }
}
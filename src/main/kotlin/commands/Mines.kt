package twizzy.tech.commands

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import twizzy.tech.game.MineManager
import twizzy.tech.game.RegionManager
import twizzy.tech.util.DurationParser
import twizzy.tech.util.InstanceMap
import twizzy.tech.util.Worlds
import twizzy.tech.util.YamlFactory

class Mine(
    private val regionManager: RegionManager,
    private val worlds: Worlds,
    private val mineManager: MineManager,
    private val instanceMap: InstanceMap
) {

    @Command("mine create")
    suspend fun createMine(actor: Player) {
        // Start mine creation process using MineManager
        val success = mineManager.startMineCreation(actor)

        if (!success) {
            actor.sendMessage(
                Component.text("Failed to start mine creation. A mine may already exist in this world.")
                    .color(NamedTextColor.RED)
            )
        }
    }

    @Command("mine go <player>")
    fun goToMine(actor: Player, @Optional player: Player?) {
        if (player == null) {
            val instance = instanceMap.getInstance(actor)
            val spawnPos = instanceMap.getSpawn(actor)
            if (instance != null && spawnPos != null) {
                actor.setInstance(instance, spawnPos)
                actor.sendMessage(
                    Component.text("Teleported to your mine!")
                        .color(NamedTextColor.GREEN)
                )
            } else {
                actor.sendMessage(
                    Component.text("You do not have a personal mine instance. Use /mine create first.")
                        .color(NamedTextColor.RED)
                )
            }
        } else {
            // Teleport to another player's mine
            val targetInstance = instanceMap.getInstance(player)
            val targetSpawn = instanceMap.getSpawn(player)

            if (targetInstance != null && targetSpawn != null) {
                actor.setInstance(targetInstance, targetSpawn)
                actor.sendMessage(
                    Component.text("Teleported to ${player.username}'s mine!")
                        .color(NamedTextColor.GREEN)
                )
            } else {
                actor.sendMessage(
                    Component.text("${player.username} does not have a mine instance.")
                        .color(NamedTextColor.RED)
                )
            }
        }
    }

    @Command("mine setblocks")
    @Description("Set the mine blocks using blocks from your hotbar")
    suspend fun setMineBlocks(actor: Player) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"

        // First check if the mine exists
        val mine = mineManager.getMine(worldName, mineName)
        if (mine == null) {
            actor.sendMessage(
                Component.text("No mine found in this world. Create one first with /mine create")
                    .color(NamedTextColor.RED)
            )
            return
        }

        // Extract blocks from player's hotbar
        val blockTypes = mutableListOf<String>()
        for (i in 0 until 9) {
            val itemStack = actor.inventory.getItemStack(i)
            if (!itemStack.isAir) {
                try {
                    // Try to convert material name to a valid block
                    val materialName = itemStack.material().name()
                    Block.fromKey(materialName) // Just to validate it's a valid block
                    blockTypes.add(materialName)
                } catch (e: IllegalArgumentException) {
                    // Skip if not a valid block
                    continue
                }
            }
        }

        if (blockTypes.isEmpty()) {
            actor.sendMessage(
                Component.text("You need to have at least one valid block in your hotbar to set mine blocks.")
                    .color(NamedTextColor.RED)
            )
            return
        }

        // Update mine blocks with the blocks from hotbar
        val success = mineManager.setMineBlocks(worldName, mineName, blockTypes)

        if (success) {
            // Reset the mine to apply the new blocks
            mineManager.resetMine(worldName, mineName)

            actor.sendMessage(
                Component.text("Mine blocks updated with blocks from your hotbar!")
                    .color(NamedTextColor.GREEN)
            )

            actor.sendMessage(
                Component.text("Use /mine info to see the list of blocks in your mine.")
                    .color(NamedTextColor.YELLOW)
            )
        } else {
            actor.sendMessage(
                Component.text("Failed to update mine blocks.")
                    .color(NamedTextColor.RED)
            )
        }
    }

    @Command("mine reset")
    suspend fun resetMine(actor: Player) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"

        // Try to reset the mine
        val success = mineManager.resetMine(worldName, mineName)

        if (success) {
            actor.sendMessage(
                Component.text("Mine reset successfully!")
                    .color(NamedTextColor.GREEN)
            )
        } else {
            actor.sendMessage(
                Component.text("No mine found in this world or failed to reset.")
                    .color(NamedTextColor.RED)
            )
        }
    }

    @Command("mine delete")
    suspend fun deleteMine(actor: Player) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"

        // Try to delete the mine
        val success = mineManager.deleteMine(worldName, mineName)

        if (success) {
            actor.sendMessage(
                Component.text("Mine deleted successfully!")
                    .color(NamedTextColor.GREEN)
            )
        } else {
            actor.sendMessage(
                Component.text("No mine found in this world.")
                    .color(NamedTextColor.RED)
            )
        }
    }

    @Command("mine interval <duration>")
    suspend fun mineInterval(actor: Player, duration: String) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"
        val seconds = DurationParser.parse(duration)
        if (seconds == null || seconds <= 0) {
            actor.sendMessage(Component.text("Invalid interval! Use formats like 30s, 15m, 2h, etc.").color(NamedTextColor.RED))
            return
        }
        val success = mineManager.setMineInterval(worldName, mineName, seconds)
        if (success) {
            actor.sendMessage(Component.text("Mine reset interval set to $duration.").color(NamedTextColor.GREEN))
        } else {
            actor.sendMessage(Component.text("No mine found in this world.").color(NamedTextColor.RED))
        }
    }

    @Command("mine info")
    suspend fun mineInfo(actor: Player) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"

        // Get information about the mine
        val mine = mineManager.getMine(worldName, mineName)

        if (mine == null) {
            actor.sendMessage(
                Component.text("No mine found in this world.")
                    .color(NamedTextColor.RED)
            )
            return
        }

        // Display mine information
        actor.sendMessage(
            Component.text("==== Mine Info ====")
                .color(NamedTextColor.GOLD)
        )

        actor.sendMessage(
            Component.text("Name: ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(mine.name).color(NamedTextColor.WHITE))
        )

        val interval = mine.resetInterval ?: 60 // Default to 60 seconds if not set
        actor.sendMessage(
            Component.text("Last reset (${DurationParser.format(interval) ?: "null"}): ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text("${(System.currentTimeMillis() - mine.lastReset) / 1000} seconds ago").color(NamedTextColor.WHITE))
        )

        actor.sendMessage(
            Component.text("Blocks used: ")
                .color(NamedTextColor.YELLOW)
        )

        if (mine.blocks.isEmpty()) {
            actor.sendMessage(
                Component.text("  - None (using STONE as default)")
                    .color(NamedTextColor.WHITE)
            )
        } else {
            mine.blocks.forEach { block ->
                actor.sendMessage(
                    Component.text("  - ${block.block}")
                        .color(NamedTextColor.WHITE)
                )
            }
        }

        // Display instructions for setting blocks
        actor.sendMessage(
            Component.text("To change the blocks, place them in your hotbar and use /mine setblocks")
                .color(NamedTextColor.AQUA)
        )
    }
}
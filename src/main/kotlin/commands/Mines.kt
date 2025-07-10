package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.game.MineManager
import twizzy.tech.game.RegionManager
import twizzy.tech.util.DurationParser
import twizzy.tech.util.InstanceMap
import twizzy.tech.util.Worlds

@Command("mines")
@CommandPermission("admin.mines")
class Mines(
    private val regionManager: RegionManager,
    private val worlds: Worlds,
    private val mineManager: MineManager,
    private val instanceMap: InstanceMap
) {

    @Subcommand("create")
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

    @Subcommand("setblocks")
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

    @Subcommand("reset")
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

    @Subcommand("delete")
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

    @Subcommand("interval <duration>")
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

    @Subcommand("info")
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
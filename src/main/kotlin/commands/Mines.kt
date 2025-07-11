package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.instance.block.Block
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.game.MineManager
import twizzy.tech.game.RegionManager
import twizzy.tech.util.DurationParser
import twizzy.tech.util.InstanceMap
import twizzy.tech.util.Worlds
import twizzy.tech.util.YamlFactory

@Command("mines")
@CommandPermission("admin.mines")
@Description("Manage global mines on the server")
class Mines(
    private val regionManager: RegionManager,
    private val worlds: Worlds,
    private val mineManager: MineManager,
    private val instanceMap: InstanceMap
) {

    @Command("mines")
    fun minesUsage(actor: Player) {
        val helpMessages = YamlFactory.getCommandHelp("mines")
        helpMessages.forEach { message ->
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("create")
    suspend fun createMine(actor: Player) {
        // Start mine creation process using MineManager
        val success = mineManager.startMineCreation(actor)

        if (success) {
            val message = YamlFactory.getMessage("commands.mines.create.success")
            actor.sendMessage(Component.text(message))
        } else {
            val message = YamlFactory.getMessage("commands.mines.create.failed")
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("setblocks")
    suspend fun setMineBlocks(actor: Player) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"

        // First check if the mine exists
        val mine = mineManager.getMine(worldName, mineName)
        if (mine == null) {
            val message = YamlFactory.getMessage("commands.mines.setblocks.no_mine")
            actor.sendMessage(Component.text(message))
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
            val message = YamlFactory.getMessage("commands.mines.setblocks.no_blocks")
            actor.sendMessage(Component.text(message))
            return
        }

        // Update mine blocks with the blocks from hotbar
        val success = mineManager.setMineBlocks(worldName, mineName, blockTypes)

        if (success) {
            // Reset the mine to apply the new blocks
            mineManager.resetMine(worldName, mineName)

            val message = YamlFactory.getMessage("commands.mines.setblocks.success")
            actor.sendMessage(Component.text(message))

            val usageMessage = YamlFactory.getMessage("commands.mines.setblocks.usage_info")
            actor.sendMessage(Component.text(usageMessage))
        } else {
            val message = YamlFactory.getMessage("commands.mines.setblocks.failed")
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("reset")
    suspend fun resetMine(actor: Player) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"

        // Try to reset the mine
        val success = mineManager.resetMine(worldName, mineName)

        if (success) {
            val message = YamlFactory.getMessage("commands.mines.reset.success")
            actor.sendMessage(Component.text(message))
        } else {
            val message = YamlFactory.getMessage("commands.mines.reset.failed")
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("delete")
    suspend fun deleteMine(actor: Player) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"

        // Try to delete the mine
        val success = mineManager.deleteMine(worldName, mineName)

        if (success) {
            val message = YamlFactory.getMessage("commands.mines.delete.success")
            actor.sendMessage(Component.text(message))
        } else {
            val message = YamlFactory.getMessage("commands.mines.delete.failed")
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("interval <duration>")
    suspend fun mineInterval(actor: Player, duration: String) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"
        val seconds = DurationParser.parse(duration)

        if (seconds == null || seconds <= 0) {
            val message = YamlFactory.getMessage("commands.mines.interval.invalid")
            actor.sendMessage(Component.text(message))
            return
        }

        val success = mineManager.setMineInterval(worldName, mineName, seconds)

        if (success) {
            val message = YamlFactory.getMessage(
                "commands.mines.interval.success",
                mapOf("duration" to duration)
            )
            actor.sendMessage(Component.text(message))
        } else {
            val message = YamlFactory.getMessage("commands.mines.interval.failed")
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("info")
    suspend fun mineInfo(actor: Player) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance)
        val mineName = "${worldName}_mine"

        // Get information about the mine
        val mine = mineManager.getMine(worldName, mineName)

        if (mine == null) {
            val message = YamlFactory.getMessage("commands.mines.info.no_mine")
            actor.sendMessage(Component.text(message))
            return
        }

        // Display mine information
        val headerMessage = YamlFactory.getMessage("commands.mines.info.header")
        actor.sendMessage(Component.text(headerMessage))

        val nameMessage = YamlFactory.getMessage(
            "commands.mines.info.name",
            mapOf("name" to mine.name)
        )
        actor.sendMessage(Component.text(nameMessage))

        val interval = mine.resetInterval ?: 60 // Default to 60 seconds if not set
        val lastResetMessage = YamlFactory.getMessage(
            "commands.mines.info.last_reset",
            mapOf(
                "interval" to (DurationParser.format(interval) ?: "null"),
                "time" to ((System.currentTimeMillis() - mine.lastReset) / 1000).toString()
            )
        )
        actor.sendMessage(Component.text(lastResetMessage))

        val blocksHeaderMessage = YamlFactory.getMessage("commands.mines.info.blocks_header")
        actor.sendMessage(Component.text(blocksHeaderMessage))

        if (mine.blocks.isEmpty()) {
            val noBlocksMessage = YamlFactory.getMessage("commands.mines.info.no_blocks")
            actor.sendMessage(Component.text(noBlocksMessage))
        } else {
            mine.blocks.forEach { block ->
                val blockMessage = YamlFactory.getMessage(
                    "commands.mines.info.block_entry",
                    mapOf("block" to block.block)
                )
                actor.sendMessage(Component.text(blockMessage))
            }
        }

        // Display instructions for setting blocks
        val instructionsMessage = YamlFactory.getMessage("commands.mines.info.instructions")
        actor.sendMessage(Component.text(instructionsMessage))
    }
}
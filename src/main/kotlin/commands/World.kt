package twizzy.tech.commands

import net.minestom.server.command.CommandSender
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import twizzy.tech.util.Worlds
import java.io.File
import java.util.*

@Command("world")
class World(private val worlds: Worlds) {

    @Command("world")
    suspend fun worldHelp(sender: CommandSender) {
        sender.sendMessage("§6World Commands:")
        sender.sendMessage("§e/world create <name> §7- Create a new world")
        sender.sendMessage("§e/world delete <name> §7- Delete an existing world")
        sender.sendMessage("§e/world setspawn <name> §7- Set spawn point of a world to your location")
        sender.sendMessage("§e/world tp <name> §7- Teleport to a world")
        sender.sendMessage("§e/world list §7- List all available worlds")
    }

    @Subcommand("create")
    suspend fun createWorld(sender: CommandSender, name: String) {
        // Validate world name
        if (!isValidWorldName(name)) {
            sender.sendMessage("§cInvalid world name. Use only letters, numbers, and underscores.")
            return
        }

        // Check if world already exists
        if (worlds.worldExists(name)) {
            sender.sendMessage("§cA world with that name already exists.")
            return
        }

        try {
            worlds.createWorld(name)
            sender.sendMessage("§aWorld '$name' has been created successfully.")
        } catch (e: Exception) {
            sender.sendMessage("§cFailed to create world: ${e.message}")
        }
    }

    @Subcommand("delete")
    suspend fun deleteWorld(sender: CommandSender, name: String) {
        // Don't allow deleting the spawn world
        if (name.equals("spawn", ignoreCase = true)) {
            sender.sendMessage("§cYou cannot delete the spawn world.")
            return
        }

        // Check if world exists
        if (!worlds.worldExists(name)) {
            sender.sendMessage("§cWorld '$name' does not exist.")
            return
        }

        try {
            worlds.deleteWorld(name)
            sender.sendMessage("§aWorld '$name' has been deleted.")
        } catch (e: Exception) {
            sender.sendMessage("§cFailed to delete world: ${e.message}")
        }
    }

    @Subcommand("setspawn")
    suspend fun setWorldSpawn(player: Player) {
        val name = worlds.getWorldNameFromInstance(player.instance)

        if (name == null) {
            player.sendMessage("§cCould not determine the world you're in.")
            return
        }

        try {
            val position = player.position
            worlds.setWorldSpawn(name, position)
            player.sendMessage("§aSpawn point for world '$name' has been set to your location.")
        } catch (e: Exception) {
            player.sendMessage("§cFailed to set spawn point: ${e.message}")
        }
    }

    @Subcommand("info")
    suspend fun worldInfo(player: Player, @Optional worldName: String?) {
        // Determine which world to show info for - either specified or current
        val targetWorldName: String
        val instance: Instance

        if (worldName != null) {
            // User specified a world name
            if (!worlds.worldExists(worldName)) {
                player.sendMessage("§cWorld '$worldName' does not exist.")
                return
            }

            targetWorldName = worldName
            // Get the instance from the world name
            instance = worlds.getWorld(worldName) ?: run {
                player.sendMessage("§cFailed to load world '$worldName'.")
                return
            }
        } else {
            // Use the player's current world
            instance = player.instance
            val currentWorldName = worlds.getWorldNameFromInstance(instance)

            if (currentWorldName == null) {
                player.sendMessage("§cCould not determine the world you're in.")
                return
            }

            targetWorldName = currentWorldName
        }

        try {
            val worldInfo = worlds.getWorldInfo(targetWorldName)

            player.sendMessage("§6§m---------------------§6[ §eWorld Info §6]§m---------------------")
            player.sendMessage("§6World Name: §e$targetWorldName")
            player.sendMessage("§6Spawn Location: §ex: ${worldInfo.spawnPoint.x}, y: ${worldInfo.spawnPoint.y}, z: ${worldInfo.spawnPoint.z}")
            player.sendMessage("§6Polar File: §e${if (worldInfo.filepath.isEmpty()) "None" else worldInfo.filepath}")
            player.sendMessage("§6Chunk Count: §e${instance.chunks.size}")
            player.sendMessage("§6Players: §e${instance.players.size}")
            player.sendMessage("§6§m------------------------------------------------------")
        } catch (e: Exception) {
            player.sendMessage("§cFailed to get world info: ${e.message}")
        }
    }

    @Subcommand("tp")
    suspend fun teleportToWorld(player: Player, name: String) {
        // Check if world exists
        if (!worlds.worldExists(name)) {
            player.sendMessage("§cWorld '$name' does not exist.")
            return
        }

        try {
            worlds.teleportToWorld(player, name)
            player.sendMessage("§aTeleported to world '$name'.")
        } catch (e: Exception) {
            player.sendMessage("§cFailed to teleport: ${e.message}")
        }
    }

    @Subcommand("list")
    suspend fun listWorlds(sender: CommandSender) {
        val worldList = worlds.listWorlds()

        if (worldList.isEmpty()) {
            sender.sendMessage("§cNo worlds found.")
            return
        }

        sender.sendMessage("§6Available worlds (${worldList.size}):")
        for (worldName in worldList) {
            sender.sendMessage("§e- $worldName")
        }
    }

    private suspend fun isValidWorldName(name: String): Boolean {
        return name.matches(Regex("^[a-zA-Z0-9_]+$"))
    }
}
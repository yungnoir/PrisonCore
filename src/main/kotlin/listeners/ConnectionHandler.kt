package twizzy.tech.listeners

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerLoadedEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import twizzy.tech.isServerLoaded
import twizzy.tech.player.PlayerData
import twizzy.tech.util.InstanceMap
import twizzy.tech.util.LettuceCache
import twizzy.tech.util.MongoStream
import twizzy.tech.gameEngine
import java.util.*

class ConnectionHandler(minecraftServer: MinecraftServer, instanceMap: InstanceMap) {

    init {
        val lettuce = LettuceCache.getInstance()
        val mongoStream = MongoStream.getInstance()

        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer,
            AsyncPlayerConfigurationEvent::class.java) { event ->

            if (!isServerLoaded) {
                event.player.kick(Component.text("Server is still loading, please wait...", NamedTextColor.RED))
                return@addSuspendingListener
            }

            val player = event.player
            val profileResult = lettuce.getProfile(player.uuid)

            // Load player data from MongoDB or create new if not exists
            loadPlayerData(player.uuid, player.username)

            setPlayerInventory(player)

            // Load backpack from YAML
            PlayerData.loadBackpack(player.uuid)

            instanceMap.createInstance(player, "GTA")

            // Give player a wooden pickaxe only if they don't have any pickaxe in their inventory
            val hasPickaxe = (0 until player.inventory.size).any { slot ->
                val item = player.inventory.getItemStack(slot)
                !item.isAir && item.material().name().contains("pickaxe")
            }

            if (!hasPickaxe) {
                player.inventory.addItemStack(ItemStack.of(Material.WOODEN_PICKAXE))
            }
        }

        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, PlayerLoadedEvent::class.java) { event ->
            val player = event.player

            // Get scoreboard title from config
            val scoreboardConfig = try {
                twizzy.tech.util.YamlFactory.loadConfig(java.io.File("game/scoreboard.yml"))
            } catch (e: Exception) {
                null
            }

            val titleText = scoreboardConfig?.let { config ->
                val scoreboard = config["scoreboard"] as? Map<*, *>
                val title = scoreboard?.get("title") as? String
                title?.replace("&", "§") ?: "Mythic Prison"
            } ?: "Mythic Prison"

            // Create a sidebar for the player with config title
            val sidebarTitle = Component.text(titleText, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
            val sidebar = twizzy.tech.util.ComponentSidebar(sidebarTitle)
            sidebar.addViewer(player)

            // Store the sidebar for this player
            twizzy.tech.util.ComponentSidebar.setSidebar(player, sidebar)

            // Get player data and update using config
            val playerData = PlayerData.getFromCache(player.uuid)
            if (playerData != null) {
                sidebar.updateFromConfig(playerData)
            } else {
                // Fallback if player data isn't loaded yet - use config or defaults
                try {
                    val fallbackData = PlayerData(player.uuid)
                    sidebar.updateFromConfig(fallbackData)
                } catch (e: Exception) {
                    // Ultimate fallback to hardcoded values
                    sidebar.update(
                        Component.text("Balance: $0", NamedTextColor.GREEN),
                        Component.text("Tokens: 0", NamedTextColor.GOLD),
                        Component.text("Souls: 0", NamedTextColor.LIGHT_PURPLE),
                        Component.text("Backpack: 0", NamedTextColor.AQUA)
                    )
                }
            }
        }


        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, PlayerDisconnectEvent::class.java) { event ->
            if (!isServerLoaded) {
                return@addSuspendingListener
            }
            val player = event.player

            // Clean up the batching system for this player
            gameEngine.cleanupPlayerBatching(player.uuid)

            // Remove sidebar for this player
            twizzy.tech.util.ComponentSidebar.removeSidebar(player)

            // Save inventory to YAML
            PlayerData.saveInventory(player.uuid, player.inventory)

            // Save backpack to YAML
            PlayerData.saveBackpack(player.uuid)

            // Save player data to MongoDB when they disconnect
            savePlayerData(player.uuid, player.username)
        }
    }

    /**
     * Sets a player's inventory from saved YAML data
     * @param player The player whose inventory needs to be set
     */
    suspend fun setPlayerInventory(player: Player) {
        try {
            // Load the inventory from YAML
            val loadedInventory = PlayerData.loadInventory(player.uuid)

            // Clear the player's current inventory first
            player.inventory.clear()

            // Set each item in the player's inventory
            for (i in 0 until loadedInventory.size) {
                val item = loadedInventory.getItemStack(i)
                if (!item.isAir) {
                    player.inventory.setItemStack(i, item)
                }
            }

        } catch (e: Exception) {
            println("Error setting inventory for player ${player.username}: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Loads player data from MongoDB and stores it in cache
     * @param uuid The player's UUID
     * @param username The player's username (for logging)
     */
    private suspend fun loadPlayerData(uuid: UUID, username: String) {
        try {
            val mongoStream = MongoStream.getInstance()
            val document = mongoStream.getPlayerData(uuid)

            // Convert document to PlayerData
            val playerData = PlayerData.fromDocument(document)

            // Save to in-memory cache
            PlayerData.saveToCache(playerData)
        } catch (e: Exception) {
            println("Failed to load player data for $username: ${e.message}")
            e.printStackTrace()

            // Create default player data if loading fails
            val playerData = PlayerData(uuid)
            PlayerData.saveToCache(playerData)
        }
    }

    /**
     * Saves player data from cache to MongoDB
     * @param uuid The player's UUID
     * @param username The player's username (for logging)
     */
    private suspend fun savePlayerData(uuid: UUID, username: String) {
        try {
            // Get data from cache
            val playerData = PlayerData.getFromCache(uuid)

            if (playerData != null) {
                // Save to MongoDB
                val mongoStream = MongoStream.getInstance()
                mongoStream.savePlayerData(playerData)

                // Remove from cache
                PlayerData.removeFromCache(uuid)
            }
        } catch (e: Exception) {
            println("Failed to save player data for $username: ${e.message}")
            e.printStackTrace()
        }
    }
}
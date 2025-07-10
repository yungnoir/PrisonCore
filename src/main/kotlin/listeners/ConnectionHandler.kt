package twizzy.tech.listeners

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import twizzy.tech.util.LettuceCache
import twizzy.tech.util.MongoStream
import twizzy.tech.player.PlayerData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlinx.coroutines.runBlocking
import net.minestom.server.entity.Player
import net.minestom.server.inventory.PlayerInventory
import java.util.UUID

class ConnectionHandler(minecraftServer: MinecraftServer) {

    init {
        val lettuce = LettuceCache.getInstance()
        val mongoStream = MongoStream.getInstance()

        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer,
            AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player
            val profileResult = lettuce.getProfile(player.uuid)

            // Load player data from MongoDB or create new if not exists
            loadPlayerData(player.uuid, player.username)

            setPlayerInventory(player)

            // Load backpack from YAML
            PlayerData.loadBackpack(player.uuid)
        }

        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, PlayerDisconnectEvent::class.java) { event ->
            val player = event.player

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

                println("Saved MongoDB data for $username: Balance=${playerData.balance}, Blocks Mined=${playerData.blocksMined}")

                // Remove from cache
                PlayerData.removeFromCache(uuid)
            }
        } catch (e: Exception) {
            println("Failed to save player data for $username: ${e.message}")
            e.printStackTrace()
        }
    }
}
package twizzy.tech.player

import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.inventory.PlayerInventory
import net.minestom.server.item.ItemStack
import net.minestom.server.utils.mojang.MojangUtils
import org.bson.Document
import twizzy.tech.util.YamlFactory
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a player's persistent data and provides caching functionality.
 */
class PlayerData(
    val uuid: UUID,
    var balance: BigDecimal = BigDecimal.ZERO,
    var blocksMined: Int = 0,
    val inventory: MutableMap<String, InventoryItem> = mutableMapOf(),
    val backpack: MutableMap<String, Int> = mutableMapOf()
) {
    /**
     * Represents an item in a player's inventory
     */
    data class InventoryItem(
        val id: String,
        var count: Int
    )


    /**
     * Converts this PlayerData instance to a MongoDB Document
     * Note: Inventory and backpack are stored in YAML, not MongoDB
     */
    fun toDocument(): Document {
        return Document()
            .append("_id", uuid.toString())
            .append("balance", balance)
            .append("blocksMined", blocksMined)
    }

    companion object {
        private val playerCache = ConcurrentHashMap<UUID, PlayerData>()
        private const val PLAYERS_DIR = "players"

        /**
         * Gets player data from the cache, or loads from MongoDB if not present.
         * @param uuid Player UUID
         * @return PlayerData if found in cache or MongoDB, null if not found in either
         */
        suspend fun getFromCache(uuid: UUID): PlayerData? {
            val cached = playerCache[uuid]
            if (cached != null) return cached
            // Try to load from MongoDB
            return try {
                val mongoStream = twizzy.tech.util.MongoStream.getInstance()
                val document = mongoStream.getPlayerData(uuid)
                val playerData = fromDocument(document)
                saveToCache(playerData)
                playerData
            } catch (e: Exception) {
                println("Failed to load player data for UUID $uuid: ${e.message}")
                null
            }
        }

        /**
         * Finds player data from the cache or database without creating it if not present.
         * @param uuid Player UUID
         * @return PlayerData if found in cache or MongoDB, null if not found in either
         */
        suspend fun findFromCache(uuid: UUID): PlayerData? {
            val cached = playerCache[uuid]
            if (cached != null) return cached
            // Try to load from MongoDB, but don't create if not found
            return try {
                val mongoStream = twizzy.tech.util.MongoStream.getInstance()
                val document = mongoStream.findPlayerData(uuid) ?: return null
                val playerData = fromDocument(document)
                saveToCache(playerData)
                playerData
            } catch (e: Exception) {
                println("Failed to find player data for UUID $uuid: ${e.message}")
                null
            }
        }

        suspend fun findFromCache(username: String): PlayerData? {
            // Try to get UUID from username, return null if username is invalid
            val uuid = try {
                MojangUtils.getUUID(username)
            } catch (e: IOException) {
                println("Failed to get UUID for username '$username': ${e.message}")
                return null
            } catch (e: Exception) {
                println("Unexpected error getting UUID for username '$username': ${e.message}")
                return null
            }

            val cached = playerCache[uuid]
            if (cached != null) return cached
            // Try to load from MongoDB, but don't create if not found
            return try {
                val mongoStream = twizzy.tech.util.MongoStream.getInstance()
                val document = mongoStream.findPlayerData(uuid) ?: return null
                val playerData = fromDocument(document)
                saveToCache(playerData)
                playerData
            } catch (e: Exception) {
                println("Failed to find player data for UUID $uuid: ${e.message}")
                null
            }
        }

        /**
         * Saves player data to the cache
         * @param data PlayerData to save
         */
        fun saveToCache(data: PlayerData) {
            playerCache[data.uuid] = data
        }

        /**
         * Removes player data from the cache
         * @param uuid Player UUID
         * @return The removed PlayerData, or null if not present
         */
        fun removeFromCache(uuid: UUID): PlayerData? = playerCache.remove(uuid)

        /**
         * Creates PlayerData from a MongoDB Document
         * @param document MongoDB Document
         * @return PlayerData instance
         */
        fun fromDocument(document: Document): PlayerData {
            val uuid = UUID.fromString(document.getString("_id"))
            val balanceObj = document.get("balance")
            val balance = when (balanceObj) {
                is org.bson.types.Decimal128 -> balanceObj.bigDecimalValue()
                is Double -> BigDecimal.valueOf(balanceObj)
                is Int -> BigDecimal.valueOf(balanceObj.toLong())
                is Long -> BigDecimal.valueOf(balanceObj)
                is String -> balanceObj.toBigDecimalOrNull() ?: BigDecimal.ZERO
                else -> BigDecimal.ZERO
            }
            val blocksMined = document.getInteger("blocksMined") ?: 0
            return PlayerData(uuid, balance, blocksMined)
        }

        /**
         * Sets a player's balance
         * @param uuid Player's UUID
         * @param amount The new balance amount
         * @return true if successful, false if player data not found
         */
        suspend fun setBalance(uuid: UUID, amount: BigDecimal): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            playerData.balance = amount
            return true
        }

        /**
         * Adds to a player's balance
         * @param uuid Player's UUID
         * @param amount The amount to add (can be negative to subtract)
         * @return true if successful, false if player data not found
         */
        suspend fun addBalance(uuid: UUID, amount: BigDecimal): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            playerData.balance = playerData.balance.add(amount)
            return true
        }

        /**
         * Increments the blocks mined count for a player
         * @param uuid Player's UUID
         * @param amount Amount of blocks to add to count (defaults to 1)
         * @return true if successful, false if player data not found
         */
        suspend fun incrementBlocksMined(uuid: UUID, amount: Int = 1): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            playerData.blocksMined += amount
            return true
        }

        /**
         * Gets a player's current balance
         * @param username Player's username
         * @return The player's balance, or null if player data not found or username is invalid
         */
        suspend fun getBalance(username: String): BigDecimal? {
            return findFromCache(username)?.balance
        }

        /**
         * Gets a player's blocks mined count
         * @param uuid Player's UUID
         * @return The player's blocks mined count, or null if player data not found
         */
        suspend fun getBlocksMined(uuid: UUID): Int? = getFromCache(uuid)?.blocksMined

        /**
         * Sets an item in a player's inventory
         * @param uuid Player's UUID
         * @param slot The inventory slot (as a string)
         * @param itemId The Minecraft item ID
         * @param count The item count
         * @return true if successful, false if player data not found
         */
        suspend fun setInventoryItem(uuid: UUID, slot: String, itemId: String, count: Int): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            playerData.inventory[slot] = InventoryItem(itemId, count)
            return true
        }

        /**
         * Removes an item from a player's inventory
         * @param uuid Player's UUID
         * @param slot The inventory slot
         * @return true if successful, false if player data not found
         */
        suspend fun removeInventoryItem(uuid: UUID, slot: String): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            playerData.inventory.remove(slot)
            return true
        }

        /**
         * Gets a player's entire inventory
         * @param uuid Player's UUID
         * @return Map of slot to InventoryItem, or null if player data not found
         */
        suspend fun getInventory(uuid: UUID): Map<String, InventoryItem>? = getFromCache(uuid)?.inventory

        /**
         * Gets an item from a player's inventory
         * @param uuid Player's UUID
         * @param slot The inventory slot
         * @return The InventoryItem, or null if not found
         */
        suspend fun getInventoryItem(uuid: UUID, slot: String): InventoryItem? = getFromCache(uuid)?.inventory?.get(slot)

        /**
         * Adds an item to the player's backpack
         * @param uuid Player's UUID
         * @param itemId The Minecraft item ID
         * @param amount The amount to add
         * @return true if successful, false if player data not found
         */
        suspend fun addToBackpack(uuid: UUID, itemId: String, amount: Int): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            val currentAmount = playerData.backpack.getOrDefault(itemId, 0)
            playerData.backpack[itemId] = currentAmount + amount
            return true
        }

        /**
         * Removes an item from the player's backpack
         * @param uuid Player's UUID
         * @param itemId The Minecraft item ID
         * @param amount The amount to remove
         * @return true if successful, false if player data not found or insufficient items
         */
        suspend fun removeFromBackpack(uuid: UUID, itemId: String, amount: Int): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            val currentAmount = playerData.backpack.getOrDefault(itemId, 0)

            if (currentAmount < amount) {
                return false
            }

            val newAmount = currentAmount - amount
            if (newAmount > 0) {
                playerData.backpack[itemId] = newAmount
            } else {
                playerData.backpack.remove(itemId)
            }

            return true
        }

        /**
         * Gets a player's backpack contents
         * @param uuid Player's UUID
         * @return Map of item ID to count, or null if player data not found
         */
        suspend fun getBackpack(uuid: UUID): Map<String, Int>? = getFromCache(uuid)?.backpack

        /**
         * Gets the count of a specific item in a player's backpack
         * @param uuid Player's UUID
         * @param itemId The Minecraft item ID
         * @return The item count, or 0 if player data not found or item not in backpack
         */
        suspend fun getBackpackAmount(uuid: UUID, itemId: String): Int =
            getFromCache(uuid)?.backpack?.getOrDefault(itemId, 0) ?: 0

        /**
         * Saves player inventory data to YAML file directly from a PlayerInventory
         * @param uuid Player UUID
         * @param inventory The player's inventory object
         * @return true if successful, false otherwise
         */
        suspend fun saveInventory(uuid: UUID, inventory: PlayerInventory): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            val playerFile = File("$PLAYERS_DIR/${uuid}.yml")

            // Ensure the players directory exists
            File(PLAYERS_DIR).mkdirs()

            try {
                // Load existing data if player file exists
                val yamlData = if (playerFile.exists()) {
                    YamlFactory.loadConfig(playerFile).toMutableMap()
                } else {
                    mutableMapOf()
                }

                // Convert inventory to YAML format
                val inventoryData = mutableMapOf<String, Any>()

                // Process main inventory slots
                for (i in 0 until inventory.size) {
                    val item = inventory.getItemStack(i)
                    if (!item.isAir) {
                        try {
                            // Get the NBT data for the item
                            val nbt = item.toItemNBT()

                            // Write the NBT data to a binary format
                            val outputStream = java.io.ByteArrayOutputStream()
                            BinaryTagIO.writer().write(nbt, outputStream)
                            val binaryData = outputStream.toByteArray()

                            // Encode the binary data to Base64 for safe storage
                            val base64Data = java.util.Base64.getEncoder().encodeToString(binaryData)

                            inventoryData[i.toString()] = base64Data
                        } catch (e: Exception) {
                            println("Error serializing item in slot $i: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }

                // Save to file
                yamlData["inventory"] = inventoryData
                YamlFactory.saveConfig(yamlData, playerFile)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        /**
         * Loads player inventory from YAML file and creates a PlayerInventory object
         * @param uuid Player UUID
         * @return PlayerInventory with items loaded from YAML, or an empty inventory if loading fails
         */
        @Suppress("UNCHECKED_CAST")
        suspend fun loadInventory(uuid: UUID): PlayerInventory {
            // Create a new inventory
            val playerInventory = PlayerInventory()

            // Attempt to load player data from cache
            val playerData = getFromCache(uuid) ?: return playerInventory // Return empty inventory if no player data

            val playerFile = File("$PLAYERS_DIR/${uuid}.yml")
            if (!playerFile.exists()) {
                return playerInventory // Return empty inventory if no file exists
            }

            try {
                val yamlData = YamlFactory.loadConfig(playerFile)

                // Load inventory data
                val inventoryData = yamlData["inventory"] as? Map<String, Any>

                // Process inventory data from YAML
                inventoryData?.forEach { (slot, nbtString) ->
                    try {
                        val slotIndex = slot.toInt()

                        // Skip invalid slots
                        if (slotIndex < 0 || slotIndex >= playerInventory.size) {
                            return@forEach
                        }

                        // Parse the NBT string and deserialize to ItemStack
                        if (nbtString is String) {
                            try {
                                try {
                                    // Check if it's Base64 encoded (new format)
                                    val binaryData = try {
                                        java.util.Base64.getDecoder().decode(nbtString)
                                    } catch (e: IllegalArgumentException) {
                                        // If decoding fails, it might be the old string format
                                        null
                                    }

                                    val nbt = if (binaryData != null) {
                                        // Use the binary data directly
                                        BinaryTagIO.reader().read(java.io.ByteArrayInputStream(binaryData))
                                    } else {
                                        // Try the legacy string format as fallback
                                        BinaryTagIO.reader().read(nbtString.byteInputStream())
                                    }

                                    val itemStack = ItemStack.fromItemNBT(nbt as CompoundBinaryTag)

                                    // Set the item in the inventory
                                    playerInventory.setItemStack(slotIndex, itemStack)
                                } catch (e: IllegalArgumentException) {
                                    println("Format error in NBT data for slot $slot: ${e.message}")
                                } catch (e: Exception) {
                                    println("Error deserializing NBT for item in slot $slot: ${e.message}")
                                    e.printStackTrace() // Print full stack trace for debugging
                                }
                            } catch (e: Exception) {
                                println("Error processing NBT string for slot $slot: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    } catch (e: NumberFormatException) {
                        // Skip invalid slot numbers
                        println("Invalid slot number in inventory data: $slot")
                    } catch (e: Exception) {
                        println("Error processing item in slot $slot: ${e.message}")
                    }
                }

                return playerInventory
            } catch (e: Exception) {
                e.printStackTrace()
                return playerInventory // Return empty inventory in case of error
            }
        }

        /**
         * Loads player backpack data from YAML file
         * @param uuid Player UUID
         * @return true if successful, false otherwise
         */
        @Suppress("UNCHECKED_CAST")
        suspend fun loadBackpack(uuid: UUID): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            val playerFile = File("$PLAYERS_DIR/${uuid}.yml")

            if (!playerFile.exists()) {
                // No data file yet, that's okay
                return true
            }

            try {
                val yamlData = YamlFactory.loadConfig(playerFile)

                // Load backpack
                val backpackData = yamlData["backpack"] as? Map<String, Any>
                playerData.backpack.clear() // Clear existing backpack before loading
                backpackData?.forEach { (itemId, countObj) ->
                    val count = when (countObj) {
                        is Int -> countObj
                        is Double -> countObj.toInt()
                        is String -> countObj.toIntOrNull() ?: 0
                        else -> 0
                    }
                    playerData.backpack[itemId] = count
                }

                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        /**
         * Saves player backpack data to YAML file
         * @param uuid Player UUID
         * @return true if successful, false otherwise
         */
        suspend fun saveBackpack(uuid: UUID): Boolean {
            val playerData = getFromCache(uuid) ?: return false
            val playerFile = File("$PLAYERS_DIR/${uuid}.yml")

            // Ensure the players directory exists
            File(PLAYERS_DIR).mkdirs()

            try {
                // Load existing data if file exists
                val yamlData = if (playerFile.exists()) {
                    YamlFactory.loadConfig(playerFile).toMutableMap()
                } else {
                    mutableMapOf()
                }

                // Update backpack data
                yamlData["backpack"] = playerData.backpack

                // Save to file
                YamlFactory.saveConfig(yamlData, playerFile)
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        /**
         * Retrieves all player data currently stored in the cache
         * Used primarily for server shutdown to save all data
         * @return List of all PlayerData objects in cache
         */
        fun getAllCachedData(): List<PlayerData> {
            return playerCache.values.toList()
        }
    }
}

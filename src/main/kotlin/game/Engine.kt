package twizzy.tech.game

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import twizzy.tech.game.items.pickaxe.Pickaxe
import twizzy.tech.util.YamlFactory
import kotlinx.coroutines.*
import com.github.shynixn.mccoroutine.minestom.minecraftDispatcher
import com.github.shynixn.mccoroutine.minestom.launch
import net.minestom.server.event.player.PlayerBlockBreakEvent
import twizzy.tech.game.items.pickaxe.boosters.Backpack
import twizzy.tech.player.PlayerData
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import twizzy.tech.player.PlayerData.Companion.incrementBlocksMined
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Core game engine optimized for async operations with accurate visual feedback
 */
class Engine(
    private val server: MinecraftServer,
    private val regionManager: RegionManager,
    private val worlds: twizzy.tech.util.Worlds
) {

    companion object {
        private const val FALLBACK_SELL_VALUE = 1.0
        private const val LEVEL_UP_COOLDOWN_MS = 1000L
        private const val ENCHANT_COOLDOWN_MS = 5000L // 5 second cooldown for enchants

        // Configuration caches
        private var blocksConfig: Map<String, Any>? = null
        var gameConfig: Map<String, Any>? = null // Make this accessible
        private var blocksFile: File? = null
        private var gameConfigFile: File? = null

        // Level-up tracking
        private val playerLastLevelUp = ConcurrentHashMap<UUID, Pair<Int, Long>>()

        // Enchant cooldown tracking: Player UUID -> Enchant Name -> Last Activation Time
        private val enchantCooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    }

    // Mining summary tracking
    data class MiningSummary(
        val blocksMined: Long = 0L,
        val enchantBlocks: Long = 0L,
        val tokensGained: BigDecimal = BigDecimal.ZERO,
        val experienceGained: Long = 0L,
        val startTime: Long = System.currentTimeMillis()
    )

    private val playerMiningSummaries = ConcurrentHashMap<UUID, MiningSummary>()
    private val summaryTimers = ConcurrentHashMap<UUID, Job>()

    // ==================== BATCHING ====================
    private val miningBatchChannels = ConcurrentHashMap<UUID, Channel<Triple<Player, Pickaxe, String>>>()
    private val batchMutex = Mutex()
    private val playerMiningCounts = ConcurrentHashMap<UUID, AtomicLong>()

    init {
        setupGameDirectory()
        setupGameConfiguration()
        setupBlockPricing()
        setupEventListeners()
    }

    /**
     * Checks if an enchant is on cooldown for a specific player
     */
    private fun isEnchantOnCooldown(playerUuid: UUID, enchantName: String): Boolean {
        val playerCooldowns = enchantCooldowns[playerUuid] ?: return false
        val lastActivation = playerCooldowns[enchantName] ?: return false
        return System.currentTimeMillis() - lastActivation < ENCHANT_COOLDOWN_MS
    }

    /**
     * Sets an enchant on cooldown for a specific player
     */
    private fun setEnchantCooldown(playerUuid: UUID, enchantName: String) {
        val playerCooldowns = enchantCooldowns.computeIfAbsent(playerUuid) { ConcurrentHashMap() }
        playerCooldowns[enchantName] = System.currentTimeMillis()
    }

    private fun setupEventListeners() {
        MinecraftServer.getGlobalEventHandler().addSuspendingListener(server, PlayerBlockBreakEvent::class.java) { event ->
            if (event.isCancelled) return@addSuspendingListener

            val player = event.player
            val pickaxe = Pickaxe.fromItemStack(player.itemInMainHand)

            if (pickaxe != null) {
                // Start mining summary if not already tracking
                if (!playerMiningSummaries.containsKey(player.uuid)) {
                    startMiningSummary(player)
                }

                // Process token reward for every block break
                processTokenReward(player, pickaxe)

                // Only roll enabled block enchants that are not on cooldown
                val blockEnchants = pickaxe.enchants
                    .filterIsInstance<twizzy.tech.game.items.pickaxe.Enchant.BlockEnchant>()
                    .filter { it.isEnabled() } // Only process enabled enchants
                    .filter { !isEnchantOnCooldown(player.uuid, it.name) } // Exclude enchants on cooldown
                    .shuffled()
                val blocksToProcess = 1L
                val blockType = event.block.name()
                var handledByEnchant = false

                for (enchant in blockEnchants) {
                    val chance = enchant.getActivationChance()
                    val roll = Math.random() * 100

                    if (roll < chance) {
                        // Set the enchant on cooldown when it activates
                        setEnchantCooldown(player.uuid, enchant.name)

                        // Debug: Log enchant activation attempt
                        println("[DEBUG] Trying to activate enchant: ${enchant.name} for player ${player.username}")

                        // Check if this enchant has an onActivate method using reflection
                        val removed = tryActivateEnchant(enchant, player, pickaxe, event.blockPosition)

                        // Debug: Log result of enchant activation
                        println("[DEBUG] Enchant ${enchant.name} removed $removed blocks for player ${player.username}")

                        if (removed > 0) {
                            handledByEnchant = true
                            // Debug: Log mining summary update for enchant blocks
                            println("[DEBUG] Updating mining summary: enchantBlocks += $removed for player ${player.username}")
                            // Update mining summary for enchant blocks
                            updateMiningSummary(player.uuid, 0L, removed, BigDecimal.ZERO, 0L)
                            // Update pickaxe XP/level and UI after enchant activation
                            val (updatedPickaxe, _) = pickaxe.mineBatchAsync(removed)
                            safelyUpdatePickaxe(player, updatedPickaxe)

                            // Send the enchant blocks separately with "ENCHANT_ACTIVATED" marker
                            sendToBatchProcessor(player, pickaxe, removed, "ENCHANT_ACTIVATED")
                        }
                        break
                    }
                }

                // Only send to batch processor if not handled by enchant
                if (!handledByEnchant) {
                    sendToBatchProcessor(player, pickaxe, blocksToProcess, blockType)
                }
            }
        }
    }

    /**
     * Attempts to activate an enchant using reflection to find and call its onActivate method
     * Returns the number of blocks removed, or 0 if the enchant doesn't have an onActivate method
     */
    private suspend fun tryActivateEnchant(
        enchant: twizzy.tech.game.items.pickaxe.Enchant.BlockEnchant,
        player: Player,
        pickaxe: Pickaxe,
        blockPosition: net.minestom.server.coordinate.Point
    ): Long {
        return try {
            println("[DEBUG] Attempting to activate enchant: ${enchant::class.java.simpleName}")

            // Try to find the onActivate method - for suspend functions, we need to include the Continuation parameter
            val onActivateMethod = try {
                val method = enchant::class.java.getDeclaredMethod(
                    "onActivate",
                    Player::class.java,
                    Pickaxe::class.java,
                    RegionManager::class.java,
                    twizzy.tech.util.Worlds::class.java,
                    net.minestom.server.coordinate.Point::class.java,
                    kotlin.coroutines.Continuation::class.java // Add continuation for suspend functions
                )
                println("[DEBUG] Found suspend onActivate method for ${enchant::class.java.simpleName}")
                method
            } catch (e: NoSuchMethodException) {
                // Fallback: try without the continuation parameter (for non-suspend methods)
                val method = enchant::class.java.getDeclaredMethod(
                    "onActivate",
                    Player::class.java,
                    Pickaxe::class.java,
                    RegionManager::class.java,
                    twizzy.tech.util.Worlds::class.java,
                    net.minestom.server.coordinate.Point::class.java
                )
                println("[DEBUG] Found non-suspend onActivate method for ${enchant::class.java.simpleName}")
                method
            }

            // Make the method accessible if it's private
            onActivateMethod.isAccessible = true

            // Call the method - for suspend functions, we need to handle them properly
            val result = if (onActivateMethod.parameterCount == 6) {
                println("[DEBUG] Calling suspend onActivate method for ${enchant::class.java.simpleName}")
                // This is a suspend function - call it directly as a suspend function
                // Cast the method to the correct function type and call it
                val result = withContext(Dispatchers.Default) {
                    // For suspend functions, we can call them directly since we're in a suspend context
                    when (enchant) {
                        is twizzy.tech.game.items.pickaxe.enchants.Jackhammer -> {
                            enchant.onActivate(player, pickaxe, regionManager, worlds, blockPosition)
                        }
                        is twizzy.tech.game.items.pickaxe.enchants.Drill -> {
                            enchant.onActivate(player, pickaxe, regionManager, worlds, blockPosition)
                        }
                        else -> {
                            // Fallback to reflection for unknown enchants
                            println("[DEBUG] Using reflection fallback for unknown enchant type")
                            0L
                        }
                    }
                }
                result
            } else {
                println("[DEBUG] Calling non-suspend onActivate method for ${enchant::class.java.simpleName}")
                // This is a regular function
                onActivateMethod.invoke(enchant, player, pickaxe, regionManager, worlds, blockPosition) as? Long ?: 0L
            }

            println("[DEBUG] onActivate method returned: $result for ${enchant::class.java.simpleName}")
            result

        } catch (e: NoSuchMethodException) {
            // This enchant doesn't have an onActivate method, which is fine
            println("[DEBUG] No onActivate method found for ${enchant::class.java.simpleName}")
            0L
        } catch (e: Exception) {
            // Log critical errors but don't crash the system
            println("[Engine ERROR] Error activating enchant ${enchant.name}: ${e.message}")
            e.printStackTrace()
            0L
        }
    }

    // ==================== INITIALIZATION ====================

    private fun setupGameDirectory() {
        val gameDir = File("game")
        if (!gameDir.exists()) gameDir.mkdirs()
    }

    private fun setupGameConfiguration() {
        val configPath = "game/config.yml"
        gameConfigFile = YamlFactory.createConfigIfNotExists(configPath)

        if (gameConfigFile!!.length() == 0L) {
            createDefaultGameConfiguration()
        }
        loadGameConfiguration()
    }

    private fun createDefaultGameConfiguration() {
        val defaultConfig = mapOf(
            "pickaxe" to mapOf(
                "levelExperience" to 500,
                "experienceIncrement" to 0,
                "maxLevel" to 100000,
                "maxBoosterMultiplier" to 1000000.0
            ),
            "mining" to mapOf(
                "summaryIntervalSeconds" to 30L
            )
        )
        YamlFactory.saveConfig(defaultConfig, gameConfigFile!!)
    }

    private fun loadGameConfiguration() {
        gameConfig = YamlFactory.loadConfig(gameConfigFile!!)
        twizzy.tech.game.items.pickaxe.Enchant.loadAllConfigs(this)
    }

    private fun setupBlockPricing() {
        val blocksPath = "game/blocks.yml"
        blocksFile = YamlFactory.createConfigIfNotExists(blocksPath)

        if (blocksFile!!.length() == 0L) {
            createDefaultBlockPricing()
        }
        loadBlockPricing()
    }

    private fun createDefaultBlockPricing() {
        val defaultConfig = mapOf(
            "fallback_value" to FALLBACK_SELL_VALUE,
            "blocks" to mapOf(
                "minecraft:stone" to 1.0,
                "minecraft:cobblestone" to 1.5,
                "minecraft:coal_ore" to 5.0,
                "minecraft:iron_ore" to 10.0,
                "minecraft:gold_ore" to 25.0,
                "minecraft:diamond_ore" to 100.0,
                "minecraft:emerald_ore" to 150.0,
                "minecraft:deepslate_coal_ore" to 7.5,
                "minecraft:deepslate_iron_ore" to 15.0,
                "minecraft:deepslate_gold_ore" to 37.5,
                "minecraft:deepslate_diamond_ore" to 150.0,
                "minecraft:deepslate_emerald_ore" to 225.0,
                "minecraft:coal_block" to 45.0,
                "minecraft:iron_block" to 90.0,
                "minecraft:gold_block" to 225.0,
                "minecraft:diamond_block" to 900.0,
                "minecraft:emerald_block" to 1350.0
            )
        )
        YamlFactory.saveConfig(defaultConfig, blocksFile!!)
    }

    private fun loadBlockPricing() {
        blocksFile?.let { file ->
            blocksConfig = YamlFactory.loadConfig(file)
        }
    }

    // ==================== CONFIGURATION DATA CLASSES ====================
    data class PickaxeConfig(
        val levelExperience: Int,
        val experienceIncrement: Double,
        val maxLevel: Int,
        val maxBoosterMultiplier: Double
    )

    data class TokenConfig(
        val baseTokens: Int,
        val findChance: Double,
        val chanceIncrease: Double,
        val greedBasePercentage: Double,
        val greedPercentageIncrease: Double
    )

    data class RankupConfig(
        val basePrice: Double,
        val priceIncrease: Double,
        val maxRank: Int
    )

    // ==================== CONFIGURATION GETTERS (COMBINED) ====================
    fun getPickaxeConfig(): PickaxeConfig {
        val config = gameConfig ?: emptyMap<String, Any>()
        return PickaxeConfig(
            levelExperience = YamlFactory.getValue(config, "pickaxe.levelExperience", 500),
            experienceIncrement = YamlFactory.getValue(config, "pickaxe.experienceIncrement", 0.0),
            maxLevel = YamlFactory.getValue(config, "pickaxe.maxLevel", 100000),
            maxBoosterMultiplier = YamlFactory.getValue(config, "pickaxe.maxBoosterMultiplier", 1000000.0)
        )
    }

    fun getTokenConfig(): TokenConfig {
        val config = gameConfig ?: emptyMap<String, Any>()
        return TokenConfig(
            baseTokens = YamlFactory.getValue(config, "pickaxe.tokens.baseTokens", 10),
            findChance = YamlFactory.getValue(config, "pickaxe.tokens.findChance", 0.05),
            chanceIncrease = YamlFactory.getValue(config, "pickaxe.tokens.chanceIncrease", 0.01),
            greedBasePercentage = YamlFactory.getValue(config, "pickaxe.enchants.soul.TokenGreed.basePercentage", 0.05),
            greedPercentageIncrease = YamlFactory.getValue(config, "pickaxe.enchants.soul.TokenGreed.percentageIncrease", 0.0)
        )
    }

    fun getRankupConfig(): RankupConfig {
        val config = gameConfig ?: emptyMap<String, Any>()
        return RankupConfig(
            basePrice = YamlFactory.getValue(config, "ranks.basePrice", 10000.0),
            priceIncrease = YamlFactory.getValue(config, "ranks.priceIncrease", 15.0),
            maxRank = YamlFactory.getValue(config, "ranks.maxRank", 100)
        )
    }

    // ==================== BLOCK PRICING ====================
    fun getBlockSellValue(blockId: String): Double {
        val config = blocksConfig ?: return FALLBACK_SELL_VALUE
        val blocks = YamlFactory.getValue(config, "blocks", emptyMap<String, Any>())
        val blockValue = YamlFactory.getValue(blocks, blockId, null as Double?)
        return blockValue ?: YamlFactory.getValue(config, "fallback_value", FALLBACK_SELL_VALUE)
    }

    fun calculateTotalSellValue(blocks: Map<String, Int>): BigDecimal {
        var total = BigDecimal.ZERO
        for ((blockId, quantity) in blocks) {
            val sellValue = getBlockSellValue(blockId)
            val blockTotal = BigDecimal.valueOf(sellValue).multiply(BigDecimal.valueOf(quantity.toLong()))
            total = total.add(blockTotal)
        }
        return total
    }

    // ==================== ENCHANT CONFIG GETTERS ====================

    fun loadEnchantConfigs() {
        val config = gameConfig ?: emptyMap<String, Any>()
        val tokenPath = "pickaxe.enchants.token"
        val soulPath = "pickaxe.enchants.soul"

        // Initialize both registries to discover all enchants and boosters
        Pickaxe.Companion.EnchantRegistry.initialize()
        twizzy.tech.game.items.pickaxe.Booster.Companion.BoosterRegistry.initialize()

        // Get all registered enchant types and load their configurations dynamically
        val allEnchantTypes = Pickaxe.Companion.EnchantRegistry.getAllRegisteredTypes()

        for (enchantType in allEnchantTypes) {
            try {
                // Create a temporary enchant instance to determine its type
                val tempEnchant = Pickaxe.Companion.EnchantRegistry.createEnchant(enchantType, 1)
                if (tempEnchant != null) {
                    val configPath = if (tempEnchant.isTokenEnchant()) tokenPath else soulPath

                    // Use reflection to call the loadConfig method on the enchant class companion object
                    val enchantClass = tempEnchant::class.java

                    try {
                        // Try to get the Companion class (Kotlin companion object)
                        val companionClass = Class.forName("${enchantClass.name}\$Companion")
                        val companionField = enchantClass.getDeclaredField("Companion")
                        companionField.isAccessible = true
                        val companionInstance = companionField.get(null)

                        // Get the loadConfig method from the companion object
                        val loadConfigMethod = companionClass.getDeclaredMethod(
                            "loadConfig",
                            Map::class.java,
                            String::class.java
                        )

                        // Call the loadConfig method on the companion instance
                        loadConfigMethod.invoke(companionInstance, config, configPath)

                    } catch (companionException: Exception) {
                        // Fallback: try calling as static method directly
                        try {
                            val loadConfigMethod = enchantClass.getDeclaredMethod(
                                "loadConfig",
                                Map::class.java,
                                String::class.java
                            )
                            loadConfigMethod.isAccessible = true
                            loadConfigMethod.invoke(null, config, configPath)

                            println("[Engine] Loaded config for enchant (static): ${enchantClass.simpleName}")
                        } catch (staticException: Exception) {
                            println("[Engine] Failed to load config for enchant $enchantType (both companion and static failed): ${companionException.message} | ${staticException.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("[Engine] Failed to load config for enchant $enchantType: ${e.message}")
                // Continue with other enchants even if one fails
            }
        }

        println("[Engine] Finished loading enchant configurations for ${allEnchantTypes.size} enchants")
    }

    // ==================== RANKUP CONFIG GETTERS ====================
    fun getRankupPriceForStep(step: Int): Double {
        val rankupConfig = getRankupConfig()
        val base = rankupConfig.basePrice
        val increase = rankupConfig.priceIncrease / 100.0
        val price = base * Math.pow(1.0 + increase, step.toDouble())
        return String.format("%.2f", price).toDouble()
    }

    // ==================== CORE PICKAXE MINING ====================

    /**
     * Safely updates a pickaxe in the player's inventory, ensuring we only update the correct pickaxe by UID
     */
    private suspend fun safelyUpdatePickaxe(player: Player, updatedPickaxe: Pickaxe): Boolean = withContext(server.minecraftDispatcher) {
        // Check main hand first (most common case)
        val mainHandPickaxe = Pickaxe.fromItemStack(player.itemInMainHand)
        if (mainHandPickaxe?.uid == updatedPickaxe.uid) {
            player.itemInMainHand = updatedPickaxe.toItemStack()
            return@withContext true
        }

        // Check off hand
        val offHandPickaxe = Pickaxe.fromItemStack(player.itemInOffHand)
        if (offHandPickaxe?.uid == updatedPickaxe.uid) {
            player.itemInOffHand = updatedPickaxe.toItemStack()
            return@withContext true
        }

        // Check inventory slots
        for (slot in 0 until player.inventory.size) {
            val itemStack = player.inventory.getItemStack(slot)
            val pickaxe = Pickaxe.fromItemStack(itemStack)
            if (pickaxe?.uid == updatedPickaxe.uid) {
                player.inventory.setItemStack(slot, updatedPickaxe.toItemStack())
                return@withContext true
            }
        }

        // If we get here, the pickaxe wasn't found (player might have dropped it or it was replaced)
        return@withContext false
    }

    // ==================== BATCHING SYSTEM ====================
    /**
     * Sends a mining event to the batch processor for this player
     */
    private fun sendToBatchProcessor(player: Player, pickaxe: Pickaxe, blocks: Long = 1L, blockType: String) {
        val playerUUID = player.uuid
        val channel = miningBatchChannels.computeIfAbsent(playerUUID) {
            val newChannel = Channel<Triple<Player, Pickaxe, String>>(Channel.UNLIMITED)
            server.launch {
                processMiningBatchOptimized(playerUUID, newChannel)
            }
            newChannel
        }
        repeat(blocks.toInt()) {
            channel.trySend(Triple(player, pickaxe, blockType))
        }
    }

    /**
     * Chunked batch processor - processes every 100 blocks or when mining stops
     */
    private suspend fun processMiningBatchOptimized(playerUUID: UUID, channel: Channel<Triple<Player, Pickaxe, String>>) {
        val chunkSize = 100L
        val debounceDelay = 300L
        var lastEventTime = 0L
        var eventCount = 0L
        var latestPlayer: Player? = null
        var latestPickaxe: Pickaxe? = null
        val blockTypeCounts = mutableMapOf<String, Long>()

        while (true) {
            try {
                val event = withTimeoutOrNull(debounceDelay + 50L) {
                    channel.receive()
                }

                if (event != null) {
                    eventCount++
                    latestPlayer = event.first
                    latestPickaxe = event.second
                    val blockType = event.third
                    blockTypeCounts[blockType] = blockTypeCounts.getOrDefault(blockType, 0L) + 1
                    lastEventTime = System.currentTimeMillis()

                    // Process chunk if we hit the chunk size
                    if (eventCount >= chunkSize) {
                        processChunk(playerUUID, latestPlayer!!, latestPickaxe!!, eventCount, blockTypeCounts)
                        eventCount = 0L
                        blockTypeCounts.clear()
                    }
                } else if (eventCount > 0 && (System.currentTimeMillis() - lastEventTime) >= debounceDelay) {
                    // Process remaining blocks when mining stops
                    processChunk(playerUUID, latestPlayer!!, latestPickaxe!!, eventCount, blockTypeCounts)
                    eventCount = 0L
                    blockTypeCounts.clear()
                }
            } catch (e: Exception) {
                println("[ERROR] Batch processor error for player $playerUUID: ${e.message}")
                delay(1000)
            }
        }
    }

    /**
     * Processes a chunk of mining data
     */
    private suspend fun processChunk(
        playerUUID: UUID,
        currentPlayer: Player,
        currentPickaxe: Pickaxe,
        blockCount: Long,
        blockTypeCounts: Map<String, Long>
    ) {
        val (updatedPickaxe, leveledUp) = currentPickaxe.mineBatchAsync(blockCount)
        val experienceGained = currentPickaxe.calculateBoostedExp(blockCount)

        // Update blocks mined counter
        incrementBlocksMined(playerUUID, blockCount.toInt())

        // Separate regular blocks from enchant blocks
        var regularBlocksCount = 0L
        var enchantBlocksCount = 0L

        // Add to backpack for each block type, except special markers
        for ((blockType, count) in blockTypeCounts) {
            if (blockType == "ENCHANT_ACTIVATED") {
                enchantBlocksCount += count
            } else if (blockType != "JACKHAMMER") {
                regularBlocksCount += count

                // Apply backpack booster if present
                val backpackBooster = currentPickaxe.activeBoostersOf<Backpack>().maxByOrNull { it.multiplier }
                val finalCount = backpackBooster?.applyBooster(count) ?: count
                PlayerData.addToBackpack(playerUUID, blockType, finalCount.toInt())
            }
        }

        // Update mining summary with separate block counts
        updateMiningSummary(playerUUID, regularBlocksCount, enchantBlocksCount, BigDecimal.ZERO, experienceGained)

        // Update UI on main thread
        withContext(server.minecraftDispatcher) {
            val pickaxeUpdated = safelyUpdatePickaxe(currentPlayer, updatedPickaxe)

            // Only update player level and experience if the pickaxe was successfully updated
            // AND the player is still holding the SAME pickaxe (same UID) that was being mined with
            val currentHandPickaxe = Pickaxe.fromItemStack(currentPlayer.itemInMainHand)
            if (pickaxeUpdated && currentHandPickaxe != null && currentHandPickaxe.uid == updatedPickaxe.uid) {
                val actualProgress = updatedPickaxe.expProgress()
                currentPlayer.level = updatedPickaxe.level
                val animationSteps = 10
                val startExp = currentPlayer.exp
                val targetExp = actualProgress
                val step = (targetExp - startExp) / animationSteps
                for (i in 1..animationSteps) {
                    currentPlayer.exp = (startExp + step * i).coerceIn(0.0f, 1.0f)
                    delay(30)
                }
                currentPlayer.exp = targetExp
                if (leveledUp) {
                    showLevelUpNotification(currentPlayer, updatedPickaxe.level)
                }
            }
        }

        playerMiningCounts[playerUUID]?.set(0)
    }

    // ==================== NOTIFICATION SYSTEM ====================
    private fun showLevelUpNotification(player: Player, level: Int) {
        // Avoid notification spam
        val lastNotification = playerLastLevelUp[player.uuid]
        if (lastNotification != null && lastNotification.first == level &&
            (System.currentTimeMillis() - lastNotification.second) < LEVEL_UP_COOLDOWN_MS) {
            return
        }
        playerLastLevelUp[player.uuid] = Pair(level, System.currentTimeMillis())

        // Chat message
        val chatMessage = YamlFactory.getMessage("commands.pickaxe.levelup.chat", mapOf("level" to level.toString()))
        player.sendMessage(Component.text(chatMessage))

        // Title
        val titleText = YamlFactory.getMessage("commands.pickaxe.levelup.title")
        val subtitleText = YamlFactory.getMessage("commands.pickaxe.levelup.subtitle", mapOf("level" to level.toString()))
        val title = Title.title(Component.text(titleText), Component.text(subtitleText))
        player.showTitle(title)

        // Sound
        val soundKey = YamlFactory.getMessage("commands.pickaxe.levelup.sound")
        player.playSound(Sound.sound(Key.key(soundKey), Sound.Source.PLAYER, 1.0f, 1.0f))
    }

    // ==================== MINING SUMMARY SYSTEM ====================

    /**
     * Gets the configured summary interval in seconds
     */
    private fun getSummaryIntervalSeconds(): Long {
        val config = gameConfig ?: emptyMap<String, Any>()
        return YamlFactory.getValue(config, "mining.summaryIntervalSeconds", 30L)
    }

    /**
     * Starts mining summary tracking for a player
     */
    fun startMiningSummary(player: Player) {
        val playerUUID = player.uuid

        // Initialize or reset the mining summary
        playerMiningSummaries[playerUUID] = MiningSummary()

        // Cancel existing timer if any
        summaryTimers[playerUUID]?.cancel()

        // Start new timer
        val intervalSeconds = getSummaryIntervalSeconds()
        summaryTimers[playerUUID] = server.launch {
            delay(intervalSeconds * 1000L)
            sendMiningSummary(player)
        }
    }

    /**
     * Updates mining summary statistics for a player
     */
    fun updateMiningSummary(playerUUID: UUID, blocksFromNormalMining: Long, blocksFromEnchants: Long, tokensGained: BigDecimal, experienceGained: Long) {
        val currentSummary = playerMiningSummaries[playerUUID] ?: return

        val updatedSummary = currentSummary.copy(
            blocksMined = currentSummary.blocksMined + blocksFromNormalMining,
            enchantBlocks = currentSummary.enchantBlocks + blocksFromEnchants,
            tokensGained = currentSummary.tokensGained.add(tokensGained),
            experienceGained = currentSummary.experienceGained + experienceGained
        )

        playerMiningSummaries[playerUUID] = updatedSummary
    }

    /**
     * Sends the mining summary to a player and resets the tracking
     */
    private suspend fun sendMiningSummary(player: Player) {
        val playerUUID = player.uuid
        val summary = playerMiningSummaries[playerUUID] ?: return

        // Only send summary if player actually mined something
        val totalBlocks = summary.blocksMined + summary.enchantBlocks
        if (totalBlocks > 0) {
            val duration = (System.currentTimeMillis() - summary.startTime) / 1000.0
            val blocksPerSecond = if (duration > 0) summary.blocksMined / duration else 0.0

            withContext(server.minecraftDispatcher) {
                // Create hover text for blocks breakdown
                val hoverText = Component.text()
                    .append(Component.text("§7Breakdown:"))
                    .append(Component.text("\n§e⛏ Raw Blocks: §f${String.format("%,d", summary.blocksMined)}"))
                    .append(Component.text("\n§d✨ Enchants: §f${String.format("%,d", summary.enchantBlocks)}"))
                    .build()

                // Create the blocks mined component with hover event
                val blocksMinedComponent = Component.text("§e⛏ Blocks Mined: §f${String.format("%,d", totalBlocks)}")
                    .hoverEvent(hoverText)

                // Create summary message with combined block count
                val message = Component.text()
                    .append(Component.text("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"))
                    .append(Component.text("\n§6§lMINING SUMMARY §7(${String.format("%.1f", duration)}s)"))
                    .append(Component.text("\n§7"))
                    .append(Component.text("\n").append(blocksMinedComponent))
                    .append(Component.text("\n§6🪙 Tokens Gained: §f${String.format("%,d", summary.tokensGained.toLong())}"))
                    .append(Component.text("\n§b⭐ Experience: §f${String.format("%,d", summary.experienceGained)}"))
                    .append(Component.text("\n§a📊 Rate: §f${String.format("%.1f", blocksPerSecond)} blocks/sec"))
                    .append(Component.text("\n§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"))
                    .build()

                player.sendMessage(message)

                // Play sound
                player.playSound(Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.PLAYER, 0.7f, 1.2f))
            }
        }

        // Reset summary and restart timer
        startMiningSummary(player)
    }

    /**
     * Stops mining summary tracking for a player
     */
    fun stopMiningSummary(playerUUID: UUID) {
        summaryTimers.remove(playerUUID)?.cancel()
        playerMiningSummaries.remove(playerUUID)
    }

    // ==================== TOKEN REWARD SYSTEM ====================
    /**
     * Processes token rewards for mining blocks
     * Calculates chance based on pickaxe level and awards tokens if successful
     * TokenGreed enchant only affects the amount of tokens, not the chance
     */
    private suspend fun processTokenReward(player: Player, pickaxe: Pickaxe) {
        val tokenConfig = getTokenConfig()
        val baseChance = tokenConfig.findChance
        val chanceIncrease = tokenConfig.chanceIncrease
        val pickaxeLevel = pickaxe.level

        // Check for TokenGreed enchant
        val tokenGreedEnchant = pickaxe.enchants.find { it.name == "TokenGreed" }

        // Calculate total chance: baseChance + (chanceIncrease * pickaxeLevel)
        val totalChance = baseChance + (chanceIncrease * pickaxeLevel)

        val roll = Math.random()

        if (roll < totalChance) {
            val baseTokens = tokenConfig.baseTokens
            val bonusTokens = (1..5).random()
            var tokensToAward = (baseTokens + bonusTokens).toDouble()

            // Apply TokenGreed amount increase if present (scales with level)
            if (tokenGreedEnchant != null) {
                val basePercentage = tokenConfig.greedBasePercentage
                val percentageIncrease = tokenConfig.greedPercentageIncrease
                val multiplier = 1.0 + (basePercentage + (tokenGreedEnchant.level - 1) * percentageIncrease)
                tokensToAward *= multiplier
            }

            val finalTokens = BigDecimal.valueOf(tokensToAward.toLong())

            // Award tokens to the player
            PlayerData.addTokens(player.uuid, finalTokens)

            // Update mining summary with tokens gained
            updateMiningSummary(player.uuid, 0L, 0L, finalTokens, 0L)

            // Send notification to player
            val message = Component.text("§6+${finalTokens.toLong()} Tokens! §7(${String.format("%.2f", totalChance * 100)}% chance)")
            player.sendActionBar(message)
        }
    }

    // ==================== CLEANUP ====================
    suspend fun cleanupPlayerBatching(playerUUID: UUID) {
        batchMutex.withLock {
            miningBatchChannels.remove(playerUUID)?.close()
            playerMiningCounts.remove(playerUUID)
            playerLastLevelUp.remove(playerUUID)
        }
        println("[DEBUG] Cleaned up batching for player: $playerUUID")
    }
}

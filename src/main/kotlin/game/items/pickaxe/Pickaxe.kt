package twizzy.tech.game.items.pickaxe

import net.kyori.adventure.text.Component
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.utils.mojang.MojangUtils
import twizzy.tech.game.items.Item
import twizzy.tech.gameEngine
import java.util.UUID
import java.time.Instant
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import twizzy.tech.player.PlayerData
import kotlin.math.pow
import kotlin.math.ln
import java.util.concurrent.ConcurrentHashMap
import java.math.BigDecimal
import twizzy.tech.game.items.pickaxe.Enchant
import twizzy.tech.game.items.pickaxe.enchants.Jackhammer
import twizzy.tech.game.items.pickaxe.enchants.Speed

/**
 * Represents a pickaxe tool item optimized for async operations
 */
class Pickaxe(
    val blocksMined: Long,
    var level: Int,
    val experience: Long,
    val owner: String,
    val boosters: List<Booster> = emptyList(),
    val enchants: List<Enchant> = emptyList(), // New: enchants
    name: String = "pickaxe",
    displayName: String = "Pickaxe",
    displayItem: Material = Material.DIAMOND_PICKAXE,
    isGlowing: Boolean = true,
    unbreakable: Boolean = false,
    durability: Int? = 5000,
    uid: UUID = UUID.randomUUID(),
) : Item(
    name = name,
    displayName = displayName,
    displayItem = displayItem,
    isGlowing = isGlowing,
    unbreakable = unbreakable,
    durability = durability,
    uid = uid
) {
    companion object {
        // Tags
        private val TAG_BLOCKS_MINED = Tag.Long("blocks_mined")
        private val TAG_PICKAXE_LEVEL = Tag.Integer("pickaxe_level")
        private val TAG_PICKAXE_EXPERIENCE = Tag.Long("pickaxe_experience")
        private val TAG_PICKAXE_OWNER = Tag.String("pickaxe_owner")
        private val TAG_PICKAXE_BOOSTERS = Tag.String("pickaxe_boosters")
        private val TAG_PICKAXE_ENCHANTS = Tag.String("pickaxe_enchants") // New tag

        private val gson = Gson()

        // Thread-safe caches
        private val levelCache = ConcurrentHashMap<Triple<Long, Long, Double>, Int>()
        private val expCache = ConcurrentHashMap<Pair<Int, Triple<Long, Double, Int>>, Long>()
        private const val MAX_CACHE_SIZE = 1000
        private const val MAX_SAFE_EXP = Long.MAX_VALUE / 2
        private const val APPROXIMATION_THRESHOLD = 1000L
        private const val YIELD_INTERVAL = 200

        private data class BoosterData(val type: String, val multiplier: Double, val expiry: Long?)
        private data class EnchantData(val type: String, val level: Int, val enabled: Boolean = true)

        /**
         * Reflection-based enchant registry
         * Automatically discovers all enchant classes in the enchants package
         */
        object EnchantRegistry {
            private val enchantClasses = mutableMapOf<String, Class<out Enchant>>()
            private var initialized = false

            fun initialize() {
                if (initialized) return

                try {
                    // Get all classes in the enchants package
                    val packageName = "twizzy.tech.game.items.pickaxe.enchants"
                    val classLoader = Thread.currentThread().contextClassLoader
                    val path = packageName.replace('.', '/')
                    val resources = classLoader.getResources(path)

                    resources.asSequence().forEach { url ->
                        if (url.protocol == "file") {
                            val file = java.io.File(url.toURI())
                            if (file.exists() && file.isDirectory) {
                                file.listFiles { _, name -> name.endsWith(".class") }?.forEach { classFile ->
                                    val className = classFile.nameWithoutExtension
                                    try {
                                        val fullClassName = "$packageName.$className"
                                        val clazz = Class.forName(fullClassName)

                                        // Check if it's an Enchant subclass and not abstract
                                        if (Enchant::class.java.isAssignableFrom(clazz) &&
                                            !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) {
                                            @Suppress("UNCHECKED_CAST")
                                            val enchantClass = clazz as Class<out Enchant>
                                            enchantClasses[className.lowercase()] = enchantClass
                                            println("[EnchantRegistry] Registered enchant: $className")
                                        }
                                    } catch (e: Exception) {
                                        println("[EnchantRegistry] Failed to load class $className: ${e.message}")
                                    }
                                }
                            }
                        } else if (url.protocol == "jar") {
                            // Handle JAR files (for production)
                            val jarConnection = url.openConnection() as java.net.JarURLConnection
                            val jarFile = jarConnection.jarFile
                            val entries = jarFile.entries()

                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                val entryName = entry.name

                                if (entryName.startsWith(path) && entryName.endsWith(".class") && !entry.isDirectory) {
                                    val className = entryName.substring(path.length + 1, entryName.length - 6)
                                    if (!className.contains('/') && !className.contains('$')) {
                                        try {
                                            val fullClassName = "$packageName.$className"
                                            val clazz = Class.forName(fullClassName)

                                            if (Enchant::class.java.isAssignableFrom(clazz) &&
                                                !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)) {
                                                @Suppress("UNCHECKED_CAST")
                                                val enchantClass = clazz as Class<out Enchant>
                                                enchantClasses[className.lowercase()] = enchantClass
                                                println("[EnchantRegistry] Registered enchant: $className")
                                            }
                                        } catch (e: Exception) {
                                            println("[EnchantRegistry] Failed to load class $className: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    initialized = true

                } catch (e: Exception) {
                    println("[EnchantRegistry] Failed to initialize: ${e.message}")
                }
            }

            fun createEnchant(type: String, level: Int): Enchant? {
                if (!initialized) initialize()

                val enchantClass = enchantClasses[type.lowercase()] ?: return null

                return try {
                    // Look for constructor that takes an Int (level)
                    val constructor = enchantClass.getConstructor(Int::class.javaPrimitiveType)
                    constructor.newInstance(level)
                } catch (e: Exception) {
                    println("[EnchantRegistry] Failed to create enchant $type: ${e.message}")
                    null
                }
            }

            fun getTypeName(enchant: Enchant): String {
                if (!initialized) initialize()

                val className = enchant::class.java.simpleName
                return className.lowercase()
            }

            fun getAllRegisteredTypes(): Set<String> {
                if (!initialized) initialize()
                return enchantClasses.keys.toSet()
            }
        }

        fun fromItemStack(itemStack: ItemStack): Pickaxe? {
            val baseItem = Item.Companion.fromItemStack(itemStack) ?: return null
            val blocksMined = itemStack.getTag(TAG_BLOCKS_MINED) ?: 0L
            val level = itemStack.getTag(TAG_PICKAXE_LEVEL) ?: 1
            val experience = itemStack.getTag(TAG_PICKAXE_EXPERIENCE) ?: 0L
            val owner = itemStack.getTag(TAG_PICKAXE_OWNER) ?: return null

            val boostersJson = itemStack.getTag(TAG_PICKAXE_BOOSTERS)
            val boosters = boostersJson?.let { deserializeBoosters(it) } ?: emptyList()

            val enchantsJson = itemStack.getTag(TAG_PICKAXE_ENCHANTS)
            val enchants = enchantsJson?.let { deserializeEnchants(it) } ?: emptyList()

            val baseDisplayName = baseItem.displayName.replace(Regex(" §7\\[Level \\d+]$"), "")

            return Pickaxe(
                blocksMined = blocksMined,
                level = level,
                experience = experience,
                owner = owner,
                boosters = boosters,
                enchants = enchants,
                name = baseItem.name,
                displayName = baseDisplayName,
                displayItem = baseItem.displayItem,
                isGlowing = baseItem.isGlowing,
                unbreakable = baseItem.unbreakable,
                durability = baseItem.durability,
                uid = baseItem.uid,
            )
        }

        private fun deserializeBoosters(json: String): List<Booster> {
            return try {
                val type = object : TypeToken<List<BoosterData>>() {}.type
                val boosterDataList: List<BoosterData> = gson.fromJson(json, type)
                boosterDataList.mapNotNull { data ->
                    val expiry = data.expiry?.let { Instant.ofEpochSecond(it) }
                    when (data.type) {
                        "experience" -> twizzy.tech.game.items.pickaxe.boosters.Experience(data.multiplier, expiry)
                        "backpack" -> twizzy.tech.game.items.pickaxe.boosters.Backpack(data.multiplier, expiry)
                        else -> null
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun serializeBoosters(boosters: List<Booster>): String {
            val boosterDataList = boosters.map { booster ->
                val type = when (booster) {
                    is twizzy.tech.game.items.pickaxe.boosters.Experience -> "experience"
                    is twizzy.tech.game.items.pickaxe.boosters.Backpack -> "backpack"
                    else -> "unknown"
                }
                BoosterData(type, booster.multiplier, booster.expiry?.epochSecond)
            }
            return gson.toJson(boosterDataList)
        }

        private fun deserializeEnchants(json: String): List<Enchant> {
            return try {
                val type = object : TypeToken<List<EnchantData>>() {}.type
                val enchantDataList: List<EnchantData> = gson.fromJson(json, type)
                enchantDataList.mapNotNull { data ->
                    val enchant = EnchantRegistry.createEnchant(data.type, data.level)
                    enchant?.apply { enabled = data.enabled }
                }
            } catch (e: Exception) {
                println("[Pickaxe] Failed to deserialize enchants: ${e.message}")
                emptyList()
            }
        }

        private fun serializeEnchants(enchants: List<Enchant>): String {
            val enchantDataList = enchants.map { enchant ->
                val type = EnchantRegistry.getTypeName(enchant)
                EnchantData(type, enchant.level, enchant.enabled)
            }
            return gson.toJson(enchantDataList)
        }
    }

    // ==================== CORE ASYNC FUNCTIONS ====================

    /**
     * Gets all active boosters (non-expired)
     */
    fun activeBoosters(): List<Booster> = boosters.filter { it.isActive() }

    /**
     * Gets active boosters of a specific type
     */
    inline fun <reified T : Booster> activeBoostersOf(): List<T> = activeBoosters().filterIsInstance<T>()

    // ==================== BATCH MINING (Optimized & Async) ====================

    /**
     * Batch mining function - optimized for processing multiple blocks in a single operation.
     * This is the only entry point for batch mining. It is suspend and safe for async use.
     * It returns a new Pickaxe and a Boolean indicating if a level-up occurred.
     */
    suspend fun mineBatchAsync(blocks: Long): Pair<Pickaxe, Boolean> = withContext(Dispatchers.Default) {
        val newBlocksMined = blocksMined + blocks
        val boostedExp = calculateBoostedExp(blocks)
        val newExperience = addExp(experience, boostedExp)
        val newLevel = calculateLevelOptimized(newExperience)
        val leveledUp = newLevel > level
        val updatedPickaxe = copy(
            blocksMined = newBlocksMined,
            level = newLevel,
            experience = newExperience
        )
        Pair(updatedPickaxe, leveledUp)
    }

    /**
     * Get experience progress as percentage
     */
    suspend fun expProgress(): Float = withContext(Dispatchers.Default) {
        val config = getConfig()
        if (level >= config.maxLevel) return@withContext 1.0f

        val currentLevelExp = getExpForLevel(level)
        val nextLevelExp = getExpForLevel(level + 1)
        val progressInLevel = experience - currentLevelExp
        val totalExpForLevel = nextLevelExp - currentLevelExp

        if (totalExpForLevel <= 0L) 1.0f
        else (progressInLevel.toFloat() / totalExpForLevel.toFloat()).coerceIn(0.0f, 1.0f)
    }

    /**
     * Get experience needed for next level
     */
    suspend fun expToNext(): Long = withContext(Dispatchers.Default) {
        val config = getConfig()
        if (level >= config.maxLevel) return@withContext 0L
        val nextLevelExp = getExpForLevel(level + 1)
        (nextLevelExp - experience).coerceAtLeast(0L)
    }

    // ==================== PRIVATE HELPER FUNCTIONS ====================

    data class Config(val baseExp: Long, val increment: Double, val maxLevel: Int, val maxMultiplier: Double)

    fun getConfig(): Config = Config(
        baseExp = gameEngine.getPickaxeConfig().levelExperience.toLong(),
        increment = gameEngine.getPickaxeConfig().experienceIncrement,
        maxLevel = gameEngine.getPickaxeConfig().maxLevel,
        maxMultiplier = gameEngine.getPickaxeConfig().maxBoosterMultiplier
    )

    fun calculateBoostedExp(baseExp: Long): Long {
        val experienceBoosters = boosters.filter { it.isActive() }.filterIsInstance<twizzy.tech.game.items.pickaxe.boosters.Experience>()
        if (experienceBoosters.isEmpty()) return baseExp

        val maxMultiplier = gameEngine.getPickaxeConfig().maxBoosterMultiplier
        return experienceBoosters.fold(baseExp) { exp, booster ->
            val cappedMultiplier = booster.multiplier.coerceAtMost(maxMultiplier)
            try {
                BigDecimal.valueOf(exp).multiply(BigDecimal.valueOf(cappedMultiplier)).toLong()
                    .let { if (it < 0) Long.MAX_VALUE else it }
            } catch (_: ArithmeticException) {
                Long.MAX_VALUE
            }
        }
    }

    /**
     * Optimized booster calculation - simplified for performance
     */
    private fun calculateBoostedExpOptimized(baseExp: Long): Long {
        val experienceBoosters = boosters.filter { it.isActive() }.filterIsInstance<twizzy.tech.game.items.pickaxe.boosters.Experience>()
        if (experienceBoosters.isEmpty()) return baseExp

        val maxMultiplier = gameEngine.getPickaxeConfig().maxBoosterMultiplier
        var result = baseExp

        for (booster in experienceBoosters) {
            val cappedMultiplier = booster.multiplier.coerceAtMost(maxMultiplier)
            val newResult = (result.toDouble() * cappedMultiplier).toLong()
            result = if (newResult < 0 || newResult < result) Long.MAX_VALUE else newResult
        }

        return result
    }

    private fun addExp(currentExp: Long, additionalExp: Long): Long {
        return try {
            val result = BigDecimal.valueOf(currentExp).add(BigDecimal.valueOf(additionalExp))
            val maxSafeValue = (Long.MAX_VALUE * 0.9).toLong()
            if (result.compareTo(BigDecimal.valueOf(maxSafeValue)) > 0) maxSafeValue else result.toLong()
        } catch (_: Exception) {
            val sum = currentExp + additionalExp
            if (sum < currentExp) (Long.MAX_VALUE * 0.9).toLong() else sum
        }
    }

    /**
     * Optimized experience addition - faster overflow handling
     */
    private fun addExpOptimized(currentExp: Long, additionalExp: Long): Long {
        val sum = currentExp + additionalExp
        return if (sum < currentExp) (Long.MAX_VALUE * 0.9).toLong() else sum
    }

    private suspend fun calculateLevel(exp: Long): Int {
        val config = getConfig()
        val cappedExp = exp.coerceAtMost(MAX_SAFE_EXP)
        val cacheKey = Triple(cappedExp, config.baseExp, config.increment)

        levelCache[cacheKey]?.let { return it.coerceAtMost(config.maxLevel) }

        val calculatedLevel = when {
            config.increment == 0.0 -> ((cappedExp / config.baseExp).toInt() + 1).coerceAtMost(config.maxLevel)
            cappedExp > config.baseExp * APPROXIMATION_THRESHOLD -> {
                yield()
                calculateHighLevelApprox(cappedExp, config)
            }
            else -> calculateLevelIterative(cappedExp, config)
        }

        if (levelCache.size >= MAX_CACHE_SIZE) levelCache.clear()
        levelCache[cacheKey] = calculatedLevel
        return calculatedLevel
    }

    /**
     * Optimized level calculation - uses cached config and simpler logic
     */
    private fun calculateLevelOptimized(exp: Long): Int {
        val config = getConfig()
        val cappedExp = exp.coerceAtMost(MAX_SAFE_EXP)

        // Quick cache lookup
        val cacheKey = Triple(cappedExp, config.baseExp, config.increment)
        levelCache[cacheKey]?.let { return it.coerceAtMost(config.maxLevel) }

        val calculatedLevel = when {
            config.increment == 0.0 -> ((cappedExp / config.baseExp).toInt() + 1).coerceAtMost(config.maxLevel)
            else -> calculateLevelIterativeOptimized(cappedExp, config)
        }

        // Simple cache management
        if (levelCache.size >= MAX_CACHE_SIZE) levelCache.clear()
        levelCache[cacheKey] = calculatedLevel
        return calculatedLevel
    }

    private fun calculateHighLevelApprox(exp: Long, config: Config): Int {
        val r = 1.0 + (config.increment / 100.0)
        val expRatio = exp.toDouble() * (r - 1.0) / config.baseExp.toDouble()
        return if (expRatio > 0) {
            (ln(1.0 + expRatio) / ln(r)).toInt() + 1
        } else {
            1
        }.coerceAtMost(config.maxLevel)
    }

    private suspend fun calculateLevelIterative(exp: Long, config: Config): Int {
        var currentLevel = 1
        var totalExpNeeded = 0L
        var iterationCount = 0
        val r = 1.0 + (config.increment / 100.0)

        while (iterationCount < 1500 && currentLevel < config.maxLevel) {
            if (iterationCount % YIELD_INTERVAL == 0) yield()

            val expForNextLevel = (config.baseExp * r.pow(currentLevel - 1.0)).toLong()
            if (expForNextLevel <= 0 || expForNextLevel > Long.MAX_VALUE / 1000) break
            if (totalExpNeeded + expForNextLevel > exp) break

            totalExpNeeded += expForNextLevel
            currentLevel++
            iterationCount++
        }

        return currentLevel.coerceAtMost(config.maxLevel)
    }

    /**
     * Optimized iterative calculation - no yielding for small calculations
     */
    private fun calculateLevelIterativeOptimized(exp: Long, config: Config): Int {
        var currentLevel = 1
        var totalExpNeeded = 0L
        val r = 1.0 + (config.increment / 100.0)

        while (currentLevel < config.maxLevel && totalExpNeeded <= exp) {
            val expForNextLevel = (config.baseExp * r.pow(currentLevel - 1.0)).toLong()
            if (expForNextLevel <= 0 || totalExpNeeded + expForNextLevel > exp) break

            totalExpNeeded += expForNextLevel
            currentLevel++

            // Early exit for performance
            if (currentLevel > 1000) break
        }

        return currentLevel.coerceAtMost(config.maxLevel)
    }

    suspend fun getExpForLevel(targetLevel: Int): Long {
        val config = getConfig()
        if (targetLevel <= 1) return 0L
        if (targetLevel > config.maxLevel) return MAX_SAFE_EXP

        val cacheKey = Pair(targetLevel, Triple(config.baseExp, config.increment, config.maxLevel))
        expCache[cacheKey]?.let { return it }

        val result = when {
            config.increment == 0.0 -> (targetLevel - 1).toLong() * config.baseExp
            targetLevel > 100 -> {
                val r = 1.0 + (config.increment / 100.0)
                val sum = config.baseExp.toDouble() * (r.pow(targetLevel - 1.0) - 1.0) / (r - 1.0)
                sum.toLong().coerceAtMost(MAX_SAFE_EXP)
            }
            else -> calculateExpIterative(targetLevel, config)
        }

        if (expCache.size >= MAX_CACHE_SIZE) expCache.clear()
        expCache[cacheKey] = result
        return result
    }

    private suspend fun calculateExpIterative(targetLevel: Int, config: Config): Long {
        var totalExp = 0L
        val r = 1.0 + (config.increment / 100.0)

        for (lvl in 1 until targetLevel) {
            if (lvl % YIELD_INTERVAL == 0) yield()
            val levelExp = (config.baseExp * r.pow(lvl - 1.0)).toLong()
            if (levelExp <= 0 || totalExp > MAX_SAFE_EXP - levelExp) return MAX_SAFE_EXP
            totalExp += levelExp
        }

        return totalExp
    }

    // ==================== UTILITY FUNCTIONS ====================

    override fun toItemStack(): ItemStack {
        val baseItemStack = super.toItemStack()

        val ownerUsername = try {
            MojangUtils.fromUuid(owner)?.get("name")?.asString ?: owner
        } catch (_: Exception) {
            owner
        }

        val formattedBlocksMined = String.format("%,d", blocksMined)
        val lore = mutableListOf<Component>(
            Component.text("§7Blocks Mined: §e$formattedBlocksMined"),
            Component.text("§7Owner: §f$ownerUsername")
        )

        val activeBoosters = boosters.filter { it.isActive() }
        if (activeBoosters.isNotEmpty()) {
            lore.add(Component.text(""))
            lore.add(Component.text("§6Active Boosters:"))
            activeBoosters.forEach { booster ->
                lore.add(Component.text("§8• §f${booster.getDisplayString()}"))
            }
        }

        // Add enchants to lore - only show enabled enchants, with Effect enchants first
        val enabledEnchants = enchants.filter { it.isEnabled() }
        if (enabledEnchants.isNotEmpty()) {
            lore.add(Component.text(""))
            lore.add(Component.text("§dEnchants:"))

            // Group enchants by type, with Effect enchants first
            val groupedEnchants = enabledEnchants.groupBy { it.getEnchantType() }
            val orderedTypes = listOf("Effect", "Multiplier", "Block") +
                               groupedEnchants.keys.filter { it !in listOf("Effect", "Multiplier", "Block") }

            orderedTypes.forEach { enchantType ->
                groupedEnchants[enchantType]?.forEach { enchant ->
                    when (enchantType) {
                        "Effect" -> {
                            lore.add(Component.text("§8• §f${enchant.getDisplayString()}"))
                        }
                        "Multiplier" -> {
                            val percent = String.format("%.2f", (enchant.getBasePercentage() + (enchant.level - 1) * enchant.getPercentageIncrease()) * 100)
                            lore.add(Component.text("§8• §f${enchant.getDisplayString()} §7(§a$percent%§7 more)"))
                        }
                        else -> {
                            val chance = String.format("%.2f", enchant.getActivationChance())
                            lore.add(Component.text("§8• §f${enchant.getDisplayString()} §7(§e$chance%§7 chance)"))
                        }
                    }
                }
            }
        }

        val displayNameWithLevel = "$displayName §7[Level $level]"

        return baseItemStack.builder()
            .set(TAG_BLOCKS_MINED, blocksMined)
            .set(TAG_PICKAXE_LEVEL, level)
            .set(TAG_PICKAXE_EXPERIENCE, experience)
            .set(TAG_PICKAXE_OWNER, owner)
            .set(TAG_PICKAXE_BOOSTERS, serializeBoosters(boosters))
            .set(TAG_PICKAXE_ENCHANTS, serializeEnchants(enchants)) // Save enchants
            .customName(Component.text(displayNameWithLevel))
            .glowing()
            .hideExtraTooltip()
            .lore(lore)
            .build()
    }

    fun copy(
        blocksMined: Long = this.blocksMined,
        level: Int = this.level,
        experience: Long = this.experience,
        owner: String = this.owner,
        boosters: List<Booster> = this.boosters,
        enchants: List<Enchant> = this.enchants, // New
        name: String = this.name,
        displayName: String = this.displayName,
        displayItem: Material = this.displayItem,
        isGlowing: Boolean = this.isGlowing,
        unbreakable: Boolean = this.unbreakable,
        durability: Int? = this.durability,
        newOwner: Boolean = false,
    ): Pickaxe = Pickaxe(
        blocksMined, level, experience, owner, boosters, enchants, name, displayName,
        displayItem, isGlowing, unbreakable, durability,
        if (newOwner) UUID.randomUUID() else this.uid
    )

    fun addEnchant(enchant: Enchant): Pickaxe = copy(enchants = enchants + enchant)

    /**
     * Adds a booster to this pickaxe
     * @param booster The booster to add
     * @return A new Pickaxe instance with the booster added
     */
    fun addBooster(booster: Booster): Pickaxe = copy(boosters = boosters + booster)

    /**
     * Gets a booster by type
     * @param type The class type of the booster to find
     * @return The first booster of the specified type, or null if not found
     */
    inline fun <reified T : Booster> getBoosterByType(): T? = boosters.filterIsInstance<T>().firstOrNull()

    /**
     * Checks if the pickaxe has a booster of the specified type
     * @param type The class type of the booster to check for
     * @return True if a booster of the specified type exists
     */
    inline fun <reified T : Booster> hasBoosterType(): Boolean = boosters.any { it is T }

    /**
     * Removes expired boosters from the pickaxe
     * @return A new Pickaxe instance with expired boosters removed
     */
    fun removeExpiredBoosters(): Pickaxe {
        val activeBoosters = boosters.filter { it.isActive() }
        return if (activeBoosters.size != boosters.size) {
            copy(boosters = activeBoosters)
        } else {
            this
        }
    }

    fun getEnchantByName(name: String): Enchant? = enchants.find { it.name.equals(name, ignoreCase = true) }
    fun hasEnchant(name: String): Boolean = getEnchantByName(name) != null

    /**
     * Toggles the enabled state of an enchant by name
     * @param enchantName The name of the enchant to toggle
     * @return A new Pickaxe instance with the enchant's enabled state toggled, or the same instance if not found
     */
    fun toggleEnchant(enchantName: String): Pickaxe {
        val enchantIndex = enchants.indexOfFirst { it.name.equals(enchantName, ignoreCase = true) }
        if (enchantIndex == -1) return this

        val updatedEnchants = enchants.toMutableList()
        val enchant = updatedEnchants[enchantIndex]
        enchant.toggle()

        return copy(enchants = updatedEnchants)
    }

    /**
     * Enables an enchant by name
     * @param enchantName The name of the enchant to enable
     * @return A new Pickaxe instance with the enchant enabled, or the same instance if not found
     */
    fun enableEnchant(enchantName: String): Pickaxe {
        val enchantIndex = enchants.indexOfFirst { it.name.equals(enchantName, ignoreCase = true) }
        if (enchantIndex == -1) return this

        val updatedEnchants = enchants.toMutableList()
        val enchant = updatedEnchants[enchantIndex]
        enchant.enable()

        return copy(enchants = updatedEnchants)
    }

    /**
     * Disables an enchant by name
     * @param enchantName The name of the enchant to disable
     * @return A new Pickaxe instance with the enchant disabled, or the same instance if not found
     */
    fun disableEnchant(enchantName: String): Pickaxe {
        val enchantIndex = enchants.indexOfFirst { it.name.equals(enchantName, ignoreCase = true) }
        if (enchantIndex == -1) return this

        val updatedEnchants = enchants.toMutableList()
        val enchant = updatedEnchants[enchantIndex]
        enchant.disable()

        return copy(enchants = updatedEnchants)
    }

    /**
     * Gets all enabled enchants
     */
    fun getEnabledEnchants(): List<Enchant> = enchants.filter { it.isEnabled() }

    /**
     * Gets all disabled enchants
     */
    fun getDisabledEnchants(): List<Enchant> = enchants.filter { !it.isEnabled() }
}

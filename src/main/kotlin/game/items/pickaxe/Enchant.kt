package twizzy.tech.game.items.pickaxe

import twizzy.tech.util.YamlFactory
import java.math.BigDecimal
import kotlin.math.pow

/**
 * Base class for all pickaxe enchants
 * Provides a standardized interface for enchant configuration and behavior
 */
abstract class Enchant(
    val name: String,
    var level: Int,
    var enabled: Boolean = true  // New: enabled/disabled state
) {
    // ==================== CONFIGURATION PROPERTIES ====================

    /**
     * Gets the maximum level for this enchant
     */
    abstract fun getMaxLevel(): Int

    /**
     * Gets the base price for level 1 of this enchant
     */
    abstract fun getBasePrice(): BigDecimal

    /**
     * Gets the price increase percentage for this enchant
     */
    abstract fun getPriceIncrease(): Double

    /**
     * Gets the base activation chance for this enchant
     */
    abstract fun getBasePercentage(): Double

    /**
     * Gets the chance increase per level for this enchant
     */
    abstract fun getPercentageIncrease(): Double

    /**
     * Gets the minimum pickaxe level required for this enchant
     */
    abstract fun getRequiredPickaxeLevel(): Int

    /**
     * Gets the display item material for this enchant
     */
    open fun getDisplayItem(): String = "BOOK"

    // ==================== CALCULATED PROPERTIES ====================

    /**
     * Calculates the price to upgrade to the next level
     */
    open fun getUpgradePrice(): BigDecimal {
        if (level >= getMaxLevel()) return BigDecimal.ZERO
        val multiplier = (1 + getPriceIncrease() / 100).pow(level - 1)
        return getBasePrice().multiply(BigDecimal.valueOf(multiplier)).setScale(2, java.math.RoundingMode.HALF_UP)
    }

    /**
     * Calculates the activation chance for this enchant at its current level
     */
    open fun getActivationChance(): Double =
        getBasePercentage() + (level - 1) * getPercentageIncrease()

    /**
     * Checks if the enchant can be upgraded
     */
    fun canUpgrade(): Boolean = level < getMaxLevel()

    /**
     * Checks if this enchant can be applied to a pickaxe based on the pickaxe's level
     */
    fun canApplyToPickaxe(pickaxeLevel: Int): Boolean = pickaxeLevel >= getRequiredPickaxeLevel()

    /**
     * Checks if the enchant is enabled and can be activated
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Enables this enchant
     */
    fun enable(): Enchant {
        enabled = true
        return this
    }

    /**
     * Disables this enchant
     */
    fun disable(): Enchant {
        enabled = false
        return this
    }

    /**
     * Toggles the enabled state of this enchant
     */
    fun toggle(): Enchant {
        enabled = !enabled
        return this
    }

    /**
     * Returns a display string for this enchant, with disabled indicator if needed
     */
    open fun getDisplayString(): String {
        val baseString = "$name §7[Level $level/${getMaxLevel()}]"
        return if (!enabled) "$baseString §c(Disabled)" else baseString
    }

    // ==================== ENCHANT TYPE SYSTEM ====================

    /**
     * Determines if this is a token enchant (default: true)
     */
    open fun isTokenEnchant(): Boolean = true

    /**
     * Determines if this is a soul enchant
     */
    open fun isSoulEnchant(): Boolean = !isTokenEnchant()

    /**
     * Gets the enchant type category
     */
    abstract fun getEnchantType(): String

    /**
     * Gets the currency type for this enchant
     */
    fun getCurrency(): String = if (isTokenEnchant()) "Tokens" else "Souls"

    // ==================== EQUALITY AND HASHING ====================

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Enchant) return false
        return name == other.name && level == other.level && enabled == other.enabled
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + level
        result = 31 * result + enabled.hashCode()
        return result
    }

    override fun toString(): String = "Enchant(name='$name', level=$level, type=${getEnchantType()}, enabled=$enabled)"

    companion object {
        /**
         * Loads all enchant configurations through the engine
         */
        fun loadAllConfigs(engine: twizzy.tech.game.Engine) {
            engine.loadEnchantConfigs()
        }
    }

    /**
     * Effect enchants provide passive effects and don't activate on block break
     * These enchants are always active (e.g., Speed, Haste)
     */
    abstract class EffectEnchant(name: String, level: Int) : Enchant(name, level) {
        override fun getEnchantType(): String = "Effect"

        /**
         * Effect enchants typically don't have activation chances since they're always active
         */
        override fun getActivationChance(): Double = 100.0

        /**
         * Effect enchants show simpler display strings without activation chance
         */
        override fun getDisplayString(): String = "$name §7$level"
    }

    /**
     * Block enchants activate when breaking blocks and have special effects
     * These enchants have activation chances and trigger on block break events
     */
    abstract class BlockEnchant(name: String, level: Int) : Enchant(name, level) {
        override fun getEnchantType(): String = "Block"
    }

    /**
     * Block enchants activate when breaking blocks and have special effects
     * These enchants have activation chances and trigger on block break events
     */
    abstract class MultiplierEnchant(name: String, level: Int) : Enchant(name, level) {
        override fun getEnchantType(): String = "Multiplier"
    }

    /**
     * Standardized configuration class for enchants
     * Handles loading and storing enchant configuration values
     */
    data class EnchantConfig(
        val maxLevel: Int = 5,
        val basePrice: BigDecimal = BigDecimal(1000),
        val priceIncrease: Double = 5.0,
        val basePercentage: Double = 1.0,
        val percentageIncrease: Double = 0.1,
        val pickaxeLevel: Int = 1,
        val item: String = "BOOK",
        val display: String = "" // New: display name with color codes
    ) {
        companion object {
            /**
             * Loads enchant configuration from YAML config
             * @param config The main configuration map
             * @param enchantName The name of the enchant (case-sensitive)
             * @param basePath The base path in the config (e.g., "pickaxe.enchants.token")
             * @param defaults Default values to use if not found in config
             */
            fun loadFromConfig(
                config: Map<String, Any>,
                enchantName: String,
                basePath: String,
                defaults: EnchantConfig = EnchantConfig()
            ): EnchantConfig {
                val path = "$basePath.$enchantName"

                val result = EnchantConfig(
                    maxLevel = YamlFactory.getValue(config, "$path.maxLevel", defaults.maxLevel),
                    basePrice = BigDecimal.valueOf(YamlFactory.getValue(config, "$path.basePrice", defaults.basePrice.toDouble())),
                    priceIncrease = YamlFactory.getValue(config, "$path.priceIncrease", defaults.priceIncrease),
                    basePercentage = YamlFactory.getValue(config, "$path.basePercentage", defaults.basePercentage),
                    percentageIncrease = YamlFactory.getValue(config, "$path.percentageIncrease", defaults.percentageIncrease),
                    pickaxeLevel = YamlFactory.getValue(config, "$path.pickaxeLevel", defaults.pickaxeLevel),
                    item = YamlFactory.getValue(config, "$path.item", defaults.item),
                    display = YamlFactory.getValue(config, "$path.display", defaults.display) // Load display name
                )

                return result
            }
        }
    }

    /**
     * Configurable Effect Enchant that uses standardized configuration
     */
    abstract class ConfigurableEffectEnchant(
        name: String,
        level: Int,
        private val config: EnchantConfig
    ) : EffectEnchant(name, level) {

        override fun getMaxLevel(): Int = config.maxLevel
        override fun getBasePrice(): BigDecimal = config.basePrice
        override fun getPriceIncrease(): Double = config.priceIncrease
        override fun getBasePercentage(): Double = config.basePercentage
        override fun getPercentageIncrease(): Double = config.percentageIncrease
        override fun getRequiredPickaxeLevel(): Int = config.pickaxeLevel
        override fun getDisplayItem(): String = config.item

        /**
         * Use configured display name with color codes for Effect enchants
         */
        override fun getDisplayString(): String {
            val displayName = if (config.display.isNotEmpty()) config.display else name
            return "$displayName §7$level"
        }
    }

    /**
     * Configurable Multiplier Enchant that uses standardized configuration
     */
    abstract class ConfigurableMultiplierEnchant(
        name: String,
        level: Int,
        private val config: EnchantConfig
    ) : MultiplierEnchant(name, level) {

        override fun getMaxLevel(): Int = config.maxLevel
        override fun getBasePrice(): BigDecimal = config.basePrice
        override fun getPriceIncrease(): Double = config.priceIncrease
        override fun getBasePercentage(): Double = config.basePercentage
        override fun getPercentageIncrease(): Double = config.percentageIncrease
        override fun getRequiredPickaxeLevel(): Int = config.pickaxeLevel
        override fun getDisplayItem(): String = config.item

        /**
         * Use configured display name with color codes for Multiplier enchants
         */
        override fun getDisplayString(): String {
            val displayName = if (config.display.isNotEmpty()) config.display else name
            val baseString = "$displayName §7[Level $level/${getMaxLevel()}]"
            return if (!enabled) "$baseString §c(Disabled)" else baseString
        }
    }


    /**
     * Configurable Block Enchant that uses standardized configuration
     */
    abstract class ConfigurableBlockEnchant(
        name: String,
        level: Int,
        private val config: EnchantConfig
    ) : BlockEnchant(name, level) {

        override fun getMaxLevel(): Int = config.maxLevel
        override fun getBasePrice(): BigDecimal = config.basePrice
        override fun getPriceIncrease(): Double = config.priceIncrease
        override fun getBasePercentage(): Double = config.basePercentage
        override fun getPercentageIncrease(): Double = config.percentageIncrease
        override fun getRequiredPickaxeLevel(): Int = config.pickaxeLevel
        override fun getDisplayItem(): String = config.item

        /**
         * Use configured display name with color codes for Block enchants
         */
        override fun getDisplayString(): String {
            val displayName = if (config.display.isNotEmpty()) config.display else name
            val baseString = "$displayName §7[Level $level/${getMaxLevel()}]"
            return if (!enabled) "$baseString §c(Disabled)" else baseString
        }
    }
}
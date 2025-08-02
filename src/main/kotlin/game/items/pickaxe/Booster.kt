package twizzy.tech.game.items.pickaxe

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for all pickaxe boosters
 * @param multiplier The multiplier this booster applies (e.g., 2.0 for double)
 * @param expiry The time when this booster expires (null for permanent)
 * @param name The display name of this booster
 */
abstract class Booster(
    val multiplier: Double,
    val expiry: Instant?,
    val name: String
) {
    /**
     * Checks if this booster is still active (not expired)
     */
    fun isActive(): Boolean {
        return expiry?.isAfter(Instant.now()) ?: true
    }

    /**
     * Gets the remaining duration in seconds, or null if permanent
     */
    fun getRemainingDuration(): Long? {
        return expiry?.let { it.epochSecond - Instant.now().epochSecond }
    }

    /**
     * Creates a formatted display string for this booster
     */
    fun getDisplayString(): String {
        val multiplierText = if (multiplier == multiplier.toInt().toDouble()) {
            "${multiplier.toInt()}x"
        } else {
            "${String.format("%.1f", multiplier)}x"
        }

        val timeText = expiry?.let {
            val remaining = getRemainingDuration()
            if (remaining != null && remaining > 0) {
                " (${formatDuration(remaining)})"
            } else {
                " (Expired)"
            }
        } ?: " (Permanent)"

        return "$name $multiplierText$timeText"
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Booster) return false
        return name == other.name &&
                multiplier == other.multiplier &&
                expiry == other.expiry
    }

    override fun hashCode(): Int {
        return name.hashCode() * 31 + multiplier.hashCode() * 31 + (expiry?.hashCode() ?: 0)
    }

    companion object {
        /**
         * Booster Registry for managing all available booster types
         */
        object BoosterRegistry {
            private val registeredTypes = ConcurrentHashMap<String, Class<out Booster>>()
            private var initialized = false

            /**
             * Initialize the booster registry with all available booster types
             */
            fun initialize() {
                if (initialized) return

                // Register all booster types
                registerBooster("Backpack", twizzy.tech.game.items.pickaxe.boosters.Backpack::class.java)
                registerBooster("Experience", twizzy.tech.game.items.pickaxe.boosters.Experience::class.java)

                initialized = true
                println("[BoosterRegistry] Initialized with ${registeredTypes.size} booster types")
            }

            /**
             * Register a booster type
             */
            private fun registerBooster(name: String, boosterClass: Class<out Booster>) {
                registeredTypes[name] = boosterClass
                println("[BoosterRegistry] Registered booster: $name")
            }

            /**
             * Get all registered booster type names
             */
            fun getAllRegisteredTypes(): Set<String> {
                initialize()
                return registeredTypes.keys.toSet()
            }

            /**
             * Get the class for a booster type
             */
            fun getBoosterClass(typeName: String): Class<out Booster>? {
                initialize()
                return registeredTypes[typeName]
            }

            /**
             * Create a booster instance of the specified type
             * @param typeName The name of the booster type
             * @param multiplier The multiplier for the booster
             * @param durationSeconds Duration in seconds (null for permanent)
             * @return A new booster instance or null if the type is not found
             */
            fun createBooster(typeName: String, multiplier: Double, durationSeconds: Long? = null): Booster? {
                val boosterClass = getBoosterClass(typeName) ?: return null

                val expiry = durationSeconds?.let { Instant.now().plusSeconds(it) }

                return try {
                    // Try to find a constructor that takes (Double, Instant?)
                    val constructor = boosterClass.getDeclaredConstructor(Double::class.java, Instant::class.java)
                    constructor.newInstance(multiplier, expiry)
                } catch (e: Exception) {
                    try {
                        // Fallback: try constructor with (Double, Instant?, String)
                        val constructor = boosterClass.getDeclaredConstructor(
                            Double::class.java,
                            Instant::class.java,
                            String::class.java
                        )
                        // Use the class simple name as the booster name
                        constructor.newInstance(multiplier, expiry, boosterClass.simpleName)
                    } catch (e2: Exception) {
                        println("[BoosterRegistry] Failed to create booster $typeName: ${e2.message}")
                        null
                    }
                }
            }

            /**
             * Create a permanent booster
             */
            fun createPermanentBooster(typeName: String, multiplier: Double): Booster? {
                return createBooster(typeName, multiplier, null)
            }

            /**
             * Create a temporary booster
             */
            fun createTemporaryBooster(typeName: String, multiplier: Double, durationSeconds: Long): Booster? {
                return createBooster(typeName, multiplier, durationSeconds)
            }

            /**
             * Get the type name for a booster instance
             */
            fun getTypeName(booster: Booster): String {
                return booster::class.java.simpleName
            }

            /**
             * Check if a booster type is registered
             */
            fun isRegistered(typeName: String): Boolean {
                initialize()
                return registeredTypes.containsKey(typeName)
            }

            /**
             * Get all available booster types with their display information
             */
            fun getAllBoosterInfo(): List<BoosterInfo> {
                initialize()
                return registeredTypes.map { (name, clazz) ->
                    BoosterInfo(
                        typeName = name,
                        displayName = name,
                        className = clazz.simpleName,
                        description = getBoosterDescription(name)
                    )
                }
            }

            /**
             * Get description for a booster type
             */
            private fun getBoosterDescription(typeName: String): String {
                return when (typeName) {
                    "Backpack" -> "Multiplies blocks that go into your backpack"
                    "Experience" -> "Multiplies experience gained from mining"
                    else -> "Unknown booster type"
                }
            }
        }
    }

    /**
     * Data class for booster information
     */
    data class BoosterInfo(
        val typeName: String,
        val displayName: String,
        val className: String,
        val description: String
    )
}

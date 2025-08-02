package twizzy.tech.game.items.pickaxe.boosters

import twizzy.tech.game.items.pickaxe.Booster
import java.time.Instant

/**
 * Backpack booster that multiplies the amount of blocks that go into the player's backpack
 * @param multiplier The backpack multiplier (e.g., 2.0 for double blocks)
 * @param expiry The time when this booster expires (null for permanent)
 */
class Backpack(
    multiplier: Double,
    expiry: Instant? = null
) : Booster(multiplier, expiry, "Backpack Booster") {

    /**
     * Applies the backpack multiplier to the given block amount
     * @param baseAmount The base amount of blocks to multiply
     * @return The boosted block amount (rounded to nearest integer)
     */
    fun applyBooster(baseAmount: Int): Int {
        if (!isActive()) return baseAmount
        return (baseAmount * multiplier).toInt()
    }

    /**
     * Applies the backpack multiplier to the given block amount (Long version)
     * @param baseAmount The base amount of blocks to multiply
     * @return The boosted block amount
     */
    fun applyBooster(baseAmount: Long): Long {
        if (!isActive()) return baseAmount
        return (baseAmount * multiplier).toLong()
    }

    companion object {
        /**
         * Creates a temporary backpack booster that lasts for the specified duration
         * @param multiplier The backpack multiplier
         * @param durationSeconds How long the booster should last in seconds
         * @return A new Backpack booster instance
         */
        fun temporary(multiplier: Double, durationSeconds: Long): Backpack {
            val expiry = Instant.now().plusSeconds(durationSeconds)
            return Backpack(multiplier, expiry)
        }

        /**
         * Creates a permanent backpack booster
         * @param multiplier The backpack multiplier
         * @return A new permanent Backpack booster instance
         */
        fun permanent(multiplier: Double): Backpack {
            return Backpack(multiplier, null)
        }

        /**
         * Common preset boosters for convenience
         */
        fun doubleBlocks(durationSeconds: Long) = temporary(2.0, durationSeconds)
        fun tripleBlocks(durationSeconds: Long) = temporary(3.0, durationSeconds)
        fun quadrupleBlocks(durationSeconds: Long) = temporary(4.0, durationSeconds)
        fun fiveTimesBlocks(durationSeconds: Long) = temporary(5.0, durationSeconds)

        fun permanentDoubleBlocks() = permanent(2.0)
        fun permanentTripleBlocks() = permanent(3.0)
    }
}
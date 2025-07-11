package twizzy.tech.player

import java.util.*

/**
 * Represents a player profile cached in memory.
 * Stores essential data from the Redis cache: UUID, permissions, ranks, and friends.
 */
class Profile {
    var uuid: UUID? = null
    var permissions: MutableList<String> = mutableListOf()
    var ranks: MutableList<String> = mutableListOf()
    var friends: MutableList<UUID> = mutableListOf()
    private var effectivePermissions: MutableSet<String> = mutableSetOf()

    // Cache the highest rank and prefix for faster access
    private var cachedHighestRank: Ranks.Rank? = null
    private var cachedHighestRankPrefix: String = ""

    companion object {
        // In-memory cache of profiles
        private val profiles: MutableMap<UUID, Profile> = mutableMapOf()

        /**
         * Retrieves a profile from the memory cache.
         * @param uuid The UUID of the player
         * @return The Profile if found, null otherwise
         */
        fun getProfile(uuid: UUID): Profile? {
            return profiles[uuid]
        }

        /**
         * Creates and caches a new profile from Redis data.
         * This is the main method used by LettuceCache to store profile data in memory.
         * @param uuid The UUID of the player
         * @param profileData The profile data from Redis
         * @return The newly created profile
         */
        fun cacheProfile(uuid: UUID, profileData: Map<String, Any?>): Profile {
            return createFromRedisData(uuid, profileData)
        }

        /**
         * Creates and caches a new profile from Redis data.
         * @param uuid The UUID of the player
         * @param profileData The profile data from Redis
         * @return The newly created profile
         */
        fun createFromRedisData(uuid: UUID, profileData: Map<String, Any?>): Profile {
            val profile = Profile()
            profile.uuid = uuid

            // Extract permissions
            @Suppress("UNCHECKED_CAST")
            val permissions = profileData["permissions"] as? List<String>
            permissions?.let { profile.permissions.addAll(it) }

            // Extract ranks
            @Suppress("UNCHECKED_CAST")
            val ranks = profileData["ranks"] as? List<String>
            ranks?.let { profile.ranks.addAll(it) }

            // Extract friends
            @Suppress("UNCHECKED_CAST")
            val friends = profileData["friends"] as? List<String>
            friends?.forEach { friendId ->
                try {
                    profile.friends.add(UUID.fromString(friendId))
                } catch (e: IllegalArgumentException) {
                    println("Invalid friend UUID format: $friendId")
                }
            }

            // Cache the profile
            profiles[uuid] = profile

            // Update effective permissions cache
            profile.updateEffectivePermissions(Ranks.getInstance().getAllRanks())
            return profile
        }

        /**
         * Removes a profile from the cache.
         * @param uuid The UUID of the player to remove
         */
        fun removeProfile(uuid: UUID) {
            profiles.remove(uuid)
        }
    }

    /**
     * Updates the effective permissions cache for this profile by combining:
     * - Direct permissions from the profile
     * - Permissions from all ranks the player has (including inherited permissions)
     *
     * This method uses the Rank class's getAllPermissions method for more efficient permission inheritance.
     *
     * @param rankMap Map of all available ranks for resolving inheritance
     */
    fun updateEffectivePermissions(rankMap: Map<String, Ranks.Rank>) {
        // Start with the player's direct permissions
        val allPermissions = mutableSetOf<String>()

        // Process player's direct permissions - extract clean permission nodes
        permissions.forEach { permString ->
            // Extract just the permission part (before the first | if it exists)
            val cleanPerm = if (permString.contains("|")) {
                permString.substring(0, permString.indexOf('|'))
            } else {
                permString
            }
            allPermissions.add(cleanPerm.lowercase())
        }

        // Add permissions from all ranks (including inherited permissions)
        for (rankId in ranks) {
            rankMap[rankId.lowercase()]?.let { rank ->
                // Add this rank's permissions
                allPermissions.addAll(rank.permissions)
            }
        }

        // Update the effective permissions cache
        effectivePermissions = allPermissions

        // Update cached highest rank and prefix while we're already processing ranks
        updateCachedHighestRank(rankMap)
    }

    /**
     * Updates the cached highest rank and prefix
     * @param rankMap Map of all available ranks
     */
    private fun updateCachedHighestRank(rankMap: Map<String, Ranks.Rank>) {
        cachedHighestRank = if (ranks.isNotEmpty()) {
            ranks.mapNotNull { rankId -> rankMap[rankId.lowercase()] }
                .maxByOrNull { it.weight }
        } else null

        cachedHighestRankPrefix = cachedHighestRank?.prefix ?: ""
    }

    /**
     * Fast permission check using the effective permissions cache.
     * Make sure to call updateEffectivePermissions() first to ensure the cache is up to date.
     *
     * @param permission The permission to check
     * @return true if the player has the permission, false otherwise
     */
    fun hasEffectivePermission(permission: String): Boolean {
        val lowercasePermission = permission.lowercase()

        // Direct match
        if (effectivePermissions.contains(lowercasePermission)) {
            return true
        }

        // Wildcard matching
        val permParts = lowercasePermission.split(".")
        for (i in permParts.indices) {
            val wildcardBase = permParts.subList(0, i + 1).joinToString(".")
            val wildcard = "$wildcardBase.*"

            if (effectivePermissions.contains(wildcard)) {
                return true
            }
        }

        // Global wildcard
        return effectivePermissions.contains("*")
    }

    /**
     * Gets all effective permissions for this player (direct + from ranks + inherited)
     * @return An unmodifiable set of all effective permissions
     */
    fun getEffectivePermissions(): Set<String> {
        return Collections.unmodifiableSet(effectivePermissions)
    }

    /**
     * Clears the effective permissions cache
     */
    fun clearEffectivePermissionsCache() {
        effectivePermissions.clear()
        cachedHighestRank = null
        cachedHighestRankPrefix = ""
    }

    /**
     * Gets the highest weighted rank for this player
     * Uses cached value if available, otherwise computes it
     * @param rankMap Map of all available ranks
     * @return The highest weighted rank, or null if the player has no ranks
     */
    fun getHighestRank(rankMap: Map<String, Ranks.Rank>? = null): Ranks.Rank? {
        // Return cached value if available
        if (cachedHighestRank != null) {
            return cachedHighestRank
        }

        // If no cached value and no rankMap provided, can't compute
        if (rankMap == null || ranks.isEmpty()) {
            return null
        }

        // Compute highest rank
        cachedHighestRank = ranks
            .mapNotNull { rankId -> rankMap[rankId.lowercase()] }
            .maxByOrNull { it.weight }

        return cachedHighestRank
    }

    /**
     * Gets the prefix of the highest weighted rank for this player
     * Uses cached value if available, otherwise computes it
     * @param rankMap Map of all available ranks
     * @return The prefix of the highest weighted rank, or an empty string if the player has no ranks
     */
    fun getHighestRankPrefix(rankMap: Map<String, Ranks.Rank>? = null): String {
        // Return cached value if available
        if (cachedHighestRankPrefix.isNotEmpty()) {
            return cachedHighestRankPrefix
        }

        // Get the highest rank and its prefix
        val highestRank = getHighestRank(rankMap)
        cachedHighestRankPrefix = highestRank?.prefix ?: ""

        return cachedHighestRankPrefix
    }
}
package twizzy.tech.player

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import twizzy.tech.util.MongoStream
import java.util.concurrent.ConcurrentHashMap


class Ranks private constructor(private val mongoStream: MongoStream) {
    // In-memory cache of ranks, keyed by rank ID
    private val cachedRanks = ConcurrentHashMap<String, Rank>()

    companion object {
        private var instance: Ranks? = null

        /**
         * Gets or creates a singleton instance of Ranks.
         * @return The Ranks instance.
         */
        fun getInstance(): Ranks {
            if (instance == null) {
                instance = Ranks(MongoStream.getInstance())
            }
            return instance!!
        }
    }

    data class Rank(
        val id: String,
        val name: String,
        val prefix: String,
        val weight: Int,
        val permissions: List<String> = listOf(),
        val inherits: List<String> = listOf(),
        val color: String? = null
    )

    /**
     * Initializes the RankManager by loading all ranks from MongoDB
     */
    suspend fun init() {
        try {
            // First, check if the ranks collection exists
            val collections = mongoStream.getMythlinkDatabase().listCollectionNames().asFlow().toList()
            val collectionExists = collections.any { it == "ranks" }
            if (!collectionExists) {
                println("Ranks collection does not exist in the database. It will be created.",)
            }

            // Clear existing cache
            cachedRanks.clear()

            // Load all ranks from MongoDB
            val ranksMap = mongoStream.getAllRanks()

            // Store all ranks in the cache
            ranksMap.forEach { (id, rank) ->
                cachedRanks[id] = rank
                // Also store lowercase version for case-insensitive lookups
                if (id.lowercase() != id) {
                    cachedRanks[id.lowercase()] = rank
                }
            }

            // Log the names of the ranks that were loaded
            val rankNames = ranksMap.values.joinToString { it.name }
            println("[PrisonCore/ranks] Retrieved ranks: $rankNames")
        } catch (e: Exception) {
            println(
                Component.text(
                    "Failed to initialize ranks: ${e.message}",
                    NamedTextColor.RED
                )
            )
        }
    }

    /**
     * Gets a rank by its ID
     * @param id The rank ID
     * @return The rank, or null if not found
     */
    fun getRank(id: String): Rank? {
        // Try exact match first
        val rank = cachedRanks[id]
        if (rank != null) return rank

        // Try lowercase as fallback
        return cachedRanks[id.lowercase()]
    }

    /**
     * Gets all cached ranks
     * @return Map of rank IDs to ranks
     */
    fun getAllRanks(): Map<String, Rank> {
        return cachedRanks
    }

    /**
     * Finds a rank by ID with case-insensitive matching
     * @param rankId The rank ID to search for
     * @return The rank if found, null otherwise
     */
    fun findRankCaseInsensitive(rankId: String): Rank? {
        // First try exact match
        val exactMatch = cachedRanks[rankId]
        if (exactMatch != null) {
            return exactMatch
        }

        // Try lowercase match
        val lowercaseMatch = cachedRanks[rankId.lowercase()]
        if (lowercaseMatch != null) {
            return lowercaseMatch
        }

        // If no exact match, try case-insensitive match by checking each key
        for ((id, rank) in cachedRanks) {
            if (id.equals(rankId, ignoreCase = true)) {
                return rank
            }
        }

        return null
    }
}

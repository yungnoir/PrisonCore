package twizzy.tech.listeners

import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerChatEvent
import twizzy.tech.player.Profile
import twizzy.tech.player.Ranks
import twizzy.tech.util.LettuceCache

class ChatHandler(minecraftServer: MinecraftServer) {
    // Use singleton instance of Ranks
    private val ranks = Ranks.getInstance()

    init {
        val lettuce = LettuceCache.getInstance()

        // Initialize chat-related listeners here
        MinecraftServer.getGlobalEventHandler().addListener(PlayerChatEvent::class.java) { event ->
            val player = event.player
            val message = event.rawMessage

            // Get player profile
            val profile = Profile.getProfile(player.uuid)

            if (profile != null) {
            }

            // Get the rank prefix
            val rankPrefix = getRankPrefix(profile)

            event.formattedMessage = Component.text()
                .append(Component.text(rankPrefix + player.username))
                .append(Component.text(": ${event.rawMessage}"))
                .build()
        }
    }

    /**
     * Gets the highest weighted rank's prefix for a player
     * @param profile The player's profile
     * @return The prefix string, empty string if no ranks found
     */
    private fun getRankPrefix(profile: Profile?): String {
        if (profile == null || profile.ranks.isEmpty()) {
            return ""
        }

        var highestRank: Ranks.Rank? = null
        var highestWeight = -1

        // Find the highest weighted rank
        for (rankIdFull in profile.ranks) {
            // Parse the rank ID from the format stored in Redis (e.g., "Owner|snowbunnykilla|1751595201355|null|")
            val rankId = parseRankId(rankIdFull)

            // Try to find the rank in the cache with case-insensitive matching
            val rank = ranks.findRankCaseInsensitive(rankId)

            if (rank != null) {
                if (rank.weight > highestWeight) {
                    highestWeight = rank.weight
                    highestRank = rank
                }
            }
        }

        // Return the prefix or empty string if no rank found
        val prefix = highestRank?.prefix?.replace("&", "§") ?: ""
        return prefix
    }


    /**
     * Parses the rank ID from the format stored in Redis
     * Format appears to be: "RankName|username|timestamp|null|"
     * @param fullRankId The full rank ID string from Redis
     * @return The actual rank ID
     */
    private fun parseRankId(fullRankId: String): String {
        // The rank ID is the first part before the first pipe character
        val parts = fullRankId.split("|")
        return parts[0]
    }
}
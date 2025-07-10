package twizzy.tech.util

import com.google.gson.Gson
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import twizzy.tech.player.Profile
import java.time.Duration
import java.util.UUID

/**
 * A Redis cache implementation using Lettuce.
 * This class provides methods to connect to a Redis server and perform basic operations.
 * It's designed to work with the profile cache maintained by the MythLink Velocity plugin.
 */
class LettuceCache {
    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null
    private var commands: RedisCommands<String, String>? = null
    private val gson = Gson()

    companion object {
        private var instance: LettuceCache? = null

        /**
         * Gets or creates a singleton instance of LettuceCache.
         * @return The LettuceCache instance.
         */
        fun getInstance(): LettuceCache {
            if (instance == null) {
                instance = LettuceCache()
            }
            return instance!!
        }
    }

    /**
     * Initializes the Redis connection using the configuration from database.yml.
     * @return True if the connection was established successfully, false otherwise.
     */
    fun init(): Boolean {
        try {
            val configFile = YamlFactory.createConfigIfNotExists(
                "database.yml",
                "database.yml"
            )

            val config = YamlFactory.loadConfig(configFile)
            val host = YamlFactory.getValue(config, "redis.host", "localhost")
            val port = YamlFactory.getValue(config, "redis.port", 6379)
            val username = YamlFactory.getValue(config, "redis.username", "default")
            val password = YamlFactory.getValue(config, "redis.password", "")

            val redisUri = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(Duration.ofSeconds(10))

            // Only set password if it's not empty
            if (password.isNotEmpty()) {
                redisUri.withPassword(password.toCharArray())
            }

            val uri = redisUri.build()

            client = RedisClient.create(uri)
            connection = client?.connect()
            commands = connection?.sync()

            // Verify connection with PING
            val pingResult = commands?.ping()
            if (pingResult == "PONG") {
                println("[PrisonCore/lettuce] Successfully connected to Redis server")
            } else {
                println("[PrisonCore/lettuce] Redis connection established but received unexpected response: $pingResult")
            }

            return commands != null
        } catch (e: RedisConnectionException) {
            println("[PrisonCore/lettuce] Failed to connect to Redis. Please check if Redis server is running.")
            e.printStackTrace()
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Closes the Redis connection and resources.
     */
    fun shutdown() {
        connection?.close()
        client?.shutdown()
    }

    /**
     * Retrieves a player profile from Redis that was cached by the MythLink Velocity plugin
     * @param uuid The UUID of the player
     * @return A Pair containing the Profile object and metadata about the retrieval, or null Profile if not found
     */
    fun getProfile(uuid: UUID): Pair<Profile?, Map<String, Any?>> {
        try {
            val key = "profile:$uuid"

            // Get the profile data from Redis
            val jsonData = commands?.get(key)

            if (jsonData == null) {
                return Pair(null, mapOf(
                    "found" to false,
                    "reason" to "not_in_redis"
                ))
            }

            // Get the TTL of the key to include in metadata
            val ttl = commands?.ttl(key) ?: -1

            // Deserialize the JSON data into a Map
            @Suppress("UNCHECKED_CAST")
            val profileData = gson.fromJson(jsonData, Map::class.java) as? Map<String, Any?>

            if (profileData == null) {
                println("[PrisonCore/lettuce] Retrieved corrupted profile data from Redis for $uuid")
                return Pair(null, mapOf(
                    "found" to false,
                    "reason" to "corrupted_data",
                    "ttl" to ttl
                ))
            }

            // Integrate with Profile class for caching in memory
            val profile = Profile.cacheProfile(uuid, profileData)

            return Pair(profile, mapOf(
                "found" to true,
                "ttl" to ttl
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(null, mapOf(
                "found" to false,
                "reason" to "exception",
                "message" to e.message
            ))
        }
    }

    /**
     * Sets a key-value pair in Redis.
     * @param key The key to set.
     * @param value The value to set.
     * @return True if set successfully, false otherwise.
     */
    fun set(key: String, value: String): Boolean {
        return try {
            commands?.set(key, value)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sets a key-value pair with an expiration time.
     * @param key The key to set.
     * @param value The value to set.
     * @param expirySeconds The expiration time in seconds.
     * @return True if set successfully, false otherwise.
     */
    fun setEx(key: String, value: String, expirySeconds: Long): Boolean {
        return try {
            commands?.setex(key, expirySeconds, value)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Gets a value by its key.
     * @param key The key to get the value for.
     * @return The value, or null if not found or on error.
     */
    fun get(key: String): String? {
        return try {
            commands?.get(key)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a key from Redis.
     * @param key The key to delete.
     * @return True if deleted successfully, false otherwise.
     */
    fun delete(key: String): Boolean {
        return try {
            commands?.del(key)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Checks if a key exists in Redis.
     * @param key The key to check.
     * @return True if the key exists, false otherwise.
     */
    fun exists(key: String): Boolean {
        return try {
            commands?.exists(key) == 1L
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
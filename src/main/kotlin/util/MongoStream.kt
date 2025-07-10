package twizzy.tech.util

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoTimeoutException
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoDatabase
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bson.Document
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import com.mongodb.client.model.Filters
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.reactive.asFlow
import twizzy.tech.game.ActivityTracker
import twizzy.tech.player.PlayerData
import twizzy.tech.player.Ranks
import java.util.UUID

class MongoStream private constructor() {
    private var client: MongoClient? = null
    // Database name constants
    private val DB_MYTHLINK = "mythlink"
    private val DB_PRISON = "prison"

    // Collection names
    private val RANKS_COLLECTION = "ranks"
    private val PLAYERS_COLLECTION = "players"
    private val ACTIVITY_COLLECTION = "activity"

    // Store database references
    private var mythlinkDatabase: MongoDatabase? = null
    private var prisonDatabase: MongoDatabase? = null

    companion object {
        private var instance: MongoStream? = null

        /**
         * Gets or creates a singleton instance of MongoStream.
         * @return The MongoStream instance.
         */
        fun getInstance(): MongoStream {
            if (instance == null) {
                instance = MongoStream()
            }
            return instance!!
        }
    }

    /**
     * Initializes the connection to both databases
     */
    fun init() {
        try {
            // Connect to both databases
            mythlinkDatabase = connect(DB_MYTHLINK)
            prisonDatabase = connect(DB_PRISON)

            // Create necessary indexes for rank collection if needed
            try {
                mythlinkDatabase!!.getCollection(RANKS_COLLECTION).createIndex(org.bson.Document("_id", 1))
                prisonDatabase!!.getCollection(PLAYERS_COLLECTION).createIndex(org.bson.Document("_id", 1))
            } catch (e: Exception) {
                println("[PrisonCore/reactive] Could not verify or create indexes: ${e.message}")
            }

            // Verify connection to both databases
            val pingMythlink = mythlinkDatabase!!.runCommand(Document("ping", 1))
            val pingPrison = prisonDatabase!!.runCommand(Document("ping", 1))

            if (pingMythlink != null && pingPrison != null) {
                println("[PrisonCore/reactive] Successfully verified connection to all databases.")
            }
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Failed to initialize databases: ${e.message}")
            throw e
        }
    }

    /**
     * Connects to MongoDB using credentials from database.yml
     * @param databaseName The name of the database to connect to
     * @return The MongoDB database instance
     */
    private fun connect(databaseName: String): MongoDatabase {
        // Use cached instance if available
        when (databaseName) {
            DB_MYTHLINK -> if (mythlinkDatabase != null) return mythlinkDatabase!!
            DB_PRISON -> if (prisonDatabase != null) return prisonDatabase!!
        }

        try {
            // Create the client if it doesn't exist yet
            if (client == null) {
                val config = loadDatabaseConfig()
                val mongoConfig = config["mongodb"] as Map<String, Any>?
                    ?: throw RuntimeException("MongoDB configuration not found in database.yml")

                val host = mongoConfig["host"] as String? ?: "localhost"
                val port = mongoConfig["port"] as Int? ?: 27017
                val username = mongoConfig["username"] as String?
                val password = mongoConfig["password"] as String?
                val connectionStringBuilder = StringBuilder("mongodb://")

                // Add authentication if provided
                if (username != null && password != null) {
                    connectionStringBuilder.append("$username:$password@")
                    println("[PrisonCore/reactive] Using authentication with username: $username")
                }

                // Add host and port
                connectionStringBuilder.append("$host:$port")

                // Create connection string
                val connectionString = ConnectionString(connectionStringBuilder.toString())

                // Build client settings
                val settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .build()

                // Create client
                client = MongoClients.create(settings)

                // Validate connection by accessing server information
                try {
                    // Simple command to verify connection
                    client!!.getDatabase("admin").runCommand(Document("ping", 1))
                } catch (e: Exception) {
                    println("[PrisonCore/reactive] Failed to ping MongoDB server. Connection may be unstable.")
                }
            }

            // Get database
            val db = client!!.getDatabase(databaseName)
            // Store in the appropriate field
            when (databaseName) {
                DB_MYTHLINK -> mythlinkDatabase = db
                DB_PRISON -> prisonDatabase = db
            }
            return db
        } catch (e: MongoTimeoutException) {
            println("[PrisonCore/reactive] Timed out connecting to MongoDB. Please check if MongoDB server is running.")
            throw e
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Failed to connect to MongoDB")
            throw e
        }
    }

    /**
     * Loads the database configuration from plugins/MythLink/database.yml
     */
    private fun loadDatabaseConfig(): Map<String, Any> {
        val configFile = File("database.yml")

        if (!configFile.exists()) {
            throw FileNotFoundException("Database configuration file not found. Make sure database.yml is properly set up.")
        }

        val yaml = Yaml()
        return yaml.load(FileInputStream(configFile)) as Map<String, Any>
    }

    /**
     * Gets the mythlink database instance
     */
    fun getMythlinkDatabase(): MongoDatabase {
        if (mythlinkDatabase == null) mythlinkDatabase = connect(DB_MYTHLINK)
        return mythlinkDatabase!!
    }

    /**
     * Gets the prison database instance
     */
    fun getPrisonDatabase(): MongoDatabase {
        if (prisonDatabase == null) prisonDatabase = connect(DB_PRISON)
        return prisonDatabase!!
    }

    /**
     * Gets the players collection from the prison database
     */
    fun getPlayersCollection() = getPrisonDatabase().getCollection(PLAYERS_COLLECTION)

    /**
     * Gets the ranks collection from the mythlink database
     */
    fun getRanksCollection() = getMythlinkDatabase().getCollection(RANKS_COLLECTION)

    /**
     * Gets the activity collection from the prison database
     */
    fun getActivityCollection() = getPrisonDatabase().getCollection(ACTIVITY_COLLECTION)

    /**
     * Fetches player data from the database or creates a new document if not found
     * @param uuid Player's UUID
     * @return The player data document
     */
    suspend fun getPlayerData(uuid: UUID): Document {
        val collection = getPlayersCollection()
        val filter = Filters.eq("_id", uuid.toString())

        val document = collection.find(filter).awaitFirstOrNull()

        return document ?: Document("_id", uuid.toString())
            .append("balance", 0.0)
            .append("blocksMined", 0)
    }

    /**
     * Fetches player data from the database
     * @param uuid Player's UUID
     * @return The player data document
     */
    suspend fun findPlayerData(uuid: UUID): Document? {
        val collection = getPlayersCollection()
        val filter = Filters.eq("_id", uuid.toString())

        val document = collection.find(filter).awaitFirstOrNull()

        return document
    }

    /**
     * Saves player data to the database
     * @param playerData The player data to save
     */
    suspend fun savePlayerData(playerData: PlayerData) {
        try {
            val collection = getPlayersCollection()
            val document = playerData.toDocument()
            val filter = Filters.eq("_id", playerData.uuid.toString())

            // Using upsert to create the document if it doesn't exist
            collection.replaceOne(filter, document, com.mongodb.client.model.ReplaceOptions().upsert(true)).awaitFirstOrNull()
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Failed to save player data: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Fetches a rank from the database by its ID
     * @param rankId The unique ID of the rank to fetch
     * @return The Rank object if found, null otherwise
     */
    suspend fun getRank(rankId: String): Ranks.Rank? {
        try {
            val db = connect(DB_MYTHLINK)
            val collection = db.getCollection(RANKS_COLLECTION)

            val document = collection.find(Filters.eq("_id", rankId)).awaitFirstOrNull() ?: return null

            return documentToRank(document)
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Failed to fetch rank with ID $rankId: ${e.message}")
            return null
        }
    }

    /**
     * Fetches all ranks from the database
     * @return Map of rank IDs to Rank objects
     */
    suspend fun getAllRanks(): Map<String, Ranks.Rank> {
        try {
            val db = connect(DB_MYTHLINK)
            val collection = db.getCollection(RANKS_COLLECTION)

            // Check if the collection exists and has documents
            val count = collection.countDocuments().awaitFirst()
            println("[PrisonCore/reactive] Found $count documents in ranks collection")

            if (count.toInt() == 0) {
                println("[PrisonCore/reactive] No ranks found in database.")
                return emptyMap()
            }

            val ranks = mutableMapOf<String, Ranks.Rank>()

            try {
                collection.find().asFlow().collect { document ->
                    val rank = documentToRank(document)
                    ranks[rank.id] = rank
                }
            } catch (e: Exception) {
                println("[PrisonCore/reactive] Error while collecting ranks: ${e.message}")
                e.printStackTrace()
            }
            return ranks
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Failed to fetch ranks: ${e.message}")
            e.printStackTrace()
            return emptyMap()
        }
    }

    /**
     * Saves a daily activity log to the database
     * @param date The date of the activity logs in format yyyy-MM-dd
     * @param dailyActivity The daily activity data to save
     */
    suspend fun saveActivityLog(date: String, dailyActivity: ActivityTracker.DailyActivity) {
        try {
            val collection = getActivityCollection()

            // Convert DailyActivity to Document
            val logs = dailyActivity.logs.map { log ->
                Document()
                    .append("tag", log.tag)
                    .append("message", log.message)
                    .append("timestamp", log.timestamp)
            }

            val document = Document("_id", date)
                .append("logs", logs)
                .append("nextLogNumber", dailyActivity.nextLogNumber)

            val filter = Filters.eq("_id", date)

            // Using upsert to create the document if it doesn't exist
            collection.replaceOne(filter, document, com.mongodb.client.model.ReplaceOptions().upsert(true)).awaitFirstOrNull()
            println("[PrisonCore/reactive] Saved activity log for date $date")
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Failed to save activity log: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Fetches activity logs for a specific date
     * @param date The date in format yyyy-MM-dd
     * @return The DailyActivity object if found, or a new empty one
     */
    suspend fun getActivityLog(date: String): ActivityTracker.DailyActivity {
        try {
            val collection = getActivityCollection()
            val document = collection.find(Filters.eq("_id", date)).awaitFirstOrNull()
                ?: return ActivityTracker.DailyActivity()

            // Convert Document to DailyActivity
            val nextLogNumber = document.getInteger("nextLogNumber", 1)

            val logs = mutableListOf<ActivityTracker.ActivityLog>()
            val logsDocument = document.get("logs") as? List<*>

            logsDocument?.filterIsInstance<Document>()?.forEach { logDoc ->
                val tag = logDoc.getString("tag") ?: ""
                val message = logDoc.getString("message") ?: ""
                val timestamp = logDoc.getLong("timestamp") ?: 0L

                logs.add(ActivityTracker.ActivityLog(tag, message, timestamp))
            }

            return ActivityTracker.DailyActivity(logs, nextLogNumber)
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Failed to fetch activity log for date $date: ${e.message}")
            return ActivityTracker.DailyActivity()
        }
    }

    /**
     * Fetches all activity logs from the database
     * @return Map of dates to DailyActivity objects
     */
    suspend fun getAllActivityLogs(): Map<String, ActivityTracker.DailyActivity> {
        try {
            val collection = getActivityCollection()
            val activityMap = mutableMapOf<String, ActivityTracker.DailyActivity>()

            collection.find().asFlow().collect { document ->
                val date = document.getString("_id")
                if (date != null) {
                    val nextLogNumber = document.getInteger("nextLogNumber", 1)

                    val logs = mutableListOf<ActivityTracker.ActivityLog>()
                    val logsDocument = document.get("logs") as? List<*>

                    logsDocument?.filterIsInstance<Document>()?.forEach { logDoc ->
                        val tag = logDoc.getString("tag") ?: ""
                        val message = logDoc.getString("message") ?: ""
                        val timestamp = logDoc.getLong("timestamp") ?: 0L

                        logs.add(ActivityTracker.ActivityLog(tag, message, timestamp))
                    }

                    activityMap[date] = ActivityTracker.DailyActivity(logs, nextLogNumber)
                }
            }

            return activityMap
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Failed to fetch all activity logs: ${e.message}")
            return emptyMap()
        }
    }

    /**
     * Closes the MongoDB connection
     */
    fun close() {
        client?.close()
        client = null
        mythlinkDatabase = null
        prisonDatabase = null
    }

    /**
     * Converts a MongoDB Document to a Rank object
     * Handles null or missing fields to prevent NullPointerException
     */
    @Suppress("UNCHECKED_CAST")
    private fun documentToRank(document: Document): Ranks.Rank {
        try {
            // Use null-safe access and provide default values for all fields
            val id = document.getString("_id") ?: "unknown"
            val name = document.getString("name") ?: id
            val prefix = document.getString("prefix") ?: "&7"
            val weight = document.getInteger("weight") ?: 0

            // Safely handle collections that might be null
            val permissionsRaw = document.get("permissions")
            val permissions = if (permissionsRaw is List<*>) {
                permissionsRaw.filterIsInstance<String>()
            } else {
                listOf<String>()
            }

            val inheritsRaw = document.get("inherits")
            val inherits = if (inheritsRaw is List<*>) {
                inheritsRaw.filterIsInstance<String>()
            } else {
                listOf<String>()
            }

            val color = document.getString("color")

            return Ranks.Rank(
                id = id,
                name = name,
                prefix = prefix,
                weight = weight,
                permissions = permissions,
                inherits = inherits,
                color = color
            )
        } catch (e: Exception) {
            println("[PrisonCore/reactive] Error converting document to rank: ${e.message}")
            e.printStackTrace()

            // Return a default rank as fallback to prevent further errors
            return Ranks.Rank(
                id = "error",
                name = "Error",
                prefix = "&c[Error] ",
                weight = -1,
                permissions = listOf(),
                inherits = listOf(),
                color = "RED"
            )
        }
    }
}

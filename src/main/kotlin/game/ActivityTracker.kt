package twizzy.tech.game

import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

/**
 * Tracks activities across the server and stores them for later retrieval
 * Currently stores in memory, but designed to be persisted to MongoDB
 */
class ActivityTracker {

    /**
     * Represents all activity for a single day
     */
    data class DailyActivity(
        val logs: MutableList<ActivityLog> = mutableListOf(),
        var nextLogNumber: Int = 1
    )

    /**
     * Represents a single activity log entry
     */
    data class ActivityLog(
        val tag: String,
        val message: String,
        val timestamp: Long
    )


    companion object {
        private var instance: ActivityTracker? = null

        /**
         * Gets or creates a singleton instance of ActivityTracker
         * @return The ActivityTracker instance
         */
        fun getInstance(): ActivityTracker {
            if (instance == null) {
                instance = ActivityTracker()
            }
            return instance!!
        }
    }

    // Map of date to daily activity logs
    private val activityLogs = HashMap<String, DailyActivity>()

    // Date formatter for getting today's key
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd")

    // Time formatter for log messages
    private val timeFormatter = SimpleDateFormat("hh:mm:ss a")

    /**
     * Logs an economy activity
     * @param adminName The name of the admin who performed the action
     * @param receiverName The name of the player who received the action
     * @param amount The amount involved in the transaction
     * @param action The economy action performed (add, take, set)
     */
    fun logEconomyActivity(adminName: String, receiverName: String, amount: String, action: String) {
        val today = dateFormatter.format(Date())
        val currentTime = timeFormatter.format(Date())

        // Get or create today's activity log
        val dailyActivity = activityLogs.getOrPut(today) { DailyActivity() }

        // Compose message with proper grammar and log number (gray) and time (dark gray)
        val logNumber = dailyActivity.nextLogNumber
        val gray = "§7"
        val red = "§c"
        val yellow = "§e"
        val green = "§a"
        val darkGray = "§8"
        val reset = "§r"

        // Build the message as a formatted string with color codes
        val message = when (action.lowercase()) {
            "add" -> "${gray}#$logNumber ${red}$adminName$reset given ${green}$$amount$reset to ${yellow}$receiverName$reset ${darkGray}($currentTime)"
            "take" -> "${gray}#$logNumber ${red}$adminName$reset taken ${green}$$amount$reset from ${yellow}$receiverName$reset ${darkGray}($currentTime)"
            "set" -> "${gray}#$logNumber ${red}$adminName$reset set ${yellow}$receiverName$reset's balance to ${green}$$amount$reset ${darkGray}($currentTime)"
            else -> "${gray}#$logNumber ${red}$adminName$reset performed '$action' with ${yellow}$receiverName$reset ${darkGray}($currentTime)"
        }

        // Create activity entry
        val logEntry = ActivityLog(
            tag = "ADMIN",
            message = message,
            timestamp = System.currentTimeMillis()
        )

        // Add to daily activity and increment log number
        dailyActivity.logs.add(logEntry)
        dailyActivity.nextLogNumber++
    }


    /**
     * Gets all logs for today
     * @return List of today's activity logs
     */
    fun getTodayLogs(): List<ActivityLog> {
        val today = dateFormatter.format(Date())
        return activityLogs[today]?.logs ?: emptyList()
    }

    /**
     * Gets all logs for a specific date
     * @param date The date in format yyyy-MM-dd
     * @return List of activity logs for the specified date
     */
    fun getLogsForDate(date: String): List<ActivityLog> {
        return activityLogs[date]?.logs ?: emptyList()
    }

    /**
     * Saves all activity logs to MongoDB (to be implemented)
     */
    fun saveToDatabase() {
        // Future implementation will save to MongoDB
        // Will use MongoStream to access the "activity" collection in "prison" database
    }
}
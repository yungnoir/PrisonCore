package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.game.ActivityTracker
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.regex.Pattern

@Command("activity")
@CommandPermission("admin.activity")
@Description("View the activity logger")
class Activity {

    // Get ActivityTracker instance
    private val activityTracker = ActivityTracker.getInstance()

    // Date formatter for validating date format
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd")

    @Command("activity")
    fun activityUsage(actor: Player) {
        actor.sendMessage("§aActivity Command Help:")
        actor.sendMessage("§7/activity <player> [duration] - View activity of a player")
        actor.sendMessage("§7/activity log [date] - View activity log for today or a specific date (format: yyyy-MM-dd)")
    }

    @Subcommand("<target>")
    fun activityPlayer(actor: Player, target: String) {
        // For future implementation - player activity tracking
        actor.sendMessage("§cPlayer activity tracking not implemented yet.")
    }

    @Subcommand("<target> <duration>")
    fun activityPlayerDuration(actor: Player, target: String, duration: String) {
        // For future implementation - player activity with duration
        actor.sendMessage("§cPlayer activity tracking not implemented yet.")
    }

    @Subcommand("log")
    fun activityLog(actor: Player) {
        // Display today's activity logs
        val logs = activityTracker.getTodayLogs()

        if (logs.isEmpty()) {
            actor.sendMessage("§cNo activity logs found for today.")
            return
        }

        displayLogs(actor, logs, "Today's")
    }

    @Subcommand("log <date>")
    fun activityLogDate(actor: Player, date: String) {
        // Validate date format
        try {
            dateFormatter.parse(date)
        } catch (e: ParseException) {
            actor.sendMessage("§cInvalid date format. Please use yyyy-MM-dd format (e.g. 2025-07-09).")
            return
        }

        // Display activity logs for the specified date
        val logs = activityTracker.getLogsForDate(date)

        if (logs.isEmpty()) {
            actor.sendMessage("§cNo activity logs found for date $date.")
            return
        }

        displayLogs(actor, logs, "Activity logs for $date", date = date)
    }

    /**
     * Helper method to display logs with pagination
     * @param actor The player to show logs to
     * @param logs The list of logs to display
     * @param title The title to show above logs
     * @param page The page number to display (defaults to first page)
     */
    private fun displayLogs(actor: Player, logs: List<ActivityTracker.ActivityLog>, title: String, page: Int = 1, date: String? = null) {
        val logsPerPage = 5
        val totalPages = (logs.size + logsPerPage - 1) / logsPerPage // Ceiling division

        // Validate page number
        if (page < 1 || page > totalPages) {
            actor.sendMessage("§cPage number must be between 1 and $totalPages.")
            return
        }

        // Calculate logs for current page
        val startIndex = (page - 1) * logsPerPage
        val endIndex = minOf(startIndex + logsPerPage, logs.size)
        val currentLogs = logs.subList(startIndex, endIndex)

        // Send header
        actor.sendMessage("§a§l$title Activity Logs §7(Page $page/$totalPages)")

        // Pattern to find timestamp in parentheses at the end of the message
        val timePattern = Pattern.compile("\\(([^)]+)\\)$")

        // Display logs for current page with hover events for timestamps
        currentLogs.forEach { log ->
            val message = log.message
            val matcher = timePattern.matcher(message)

            if (matcher.find()) {
                // Found a timestamp in parentheses
                val timestamp = matcher.group(1)
                val messageWithoutTime = message.substring(0, matcher.start()).trim()

                // Create a component for the message without the timestamp
                val component = Component.text(messageWithoutTime)

                // Add a space and the hover component for the timestamp
                val fullComponent = component
                    .append(Component.space())
                    .hoverEvent(
                        HoverEvent.showText(
                            Component.text(timestamp)
                                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        )
                    )

                actor.sendMessage(fullComponent)
            } else {
                // No timestamp found, just send the message as is
                actor.sendMessage(message)
            }
        }

        // Navigation help
        if (totalPages > 1) {
            actor.sendMessage("§7Use §f/activity log ${if (title.startsWith("Today")) "" else "${date ?: ""} "}page:<number> §7to view other pages")
        }
    }
}
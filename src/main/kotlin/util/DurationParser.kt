package twizzy.tech.util


/**
 * Utility class for parsing string duration expressions into seconds.
 * Supports formats like:
 * - 1s (1 second)
 * - 2m (2 minutes)
 * - 3h (3 hours)
 * - 4d (4 days)
 * - 5w (5 weeks)
 * - 6mo (6 months)
 * - 7y (7 years)
 * Also supports combinations like "1d 12h 30m 45s"
 */
class DurationParser {
    companion object {
        // Define time unit multipliers in seconds
        private const val SECOND = 1L
        private const val MINUTE = SECOND * 60
        private const val HOUR = MINUTE * 60
        private const val DAY = HOUR * 24
        private const val WEEK = DAY * 7
        private const val MONTH = DAY * 30  // Approximating a month as 30 days
        private const val YEAR = DAY * 365  // Approximating a year as 365 days

        /**
         * Parse a duration string into seconds
         * @param input The duration string (e.g., "1d 12h 30m")
         * @return The duration in seconds, or null if the input is invalid
         */
        fun parse(input: String): Long? {
            if (input.isBlank()) return null

            var totalSeconds = 0L
            val pattern = "(\\d+)\\s*([smhdwoy]+)".toRegex()
            val matches = pattern.findAll(input)

            if (matches.count() == 0) return null

            for (match in matches) {
                val (valueStr, unit) = match.destructured
                val value = valueStr.toLongOrNull() ?: continue

                val unitSeconds = when (unit.lowercase()) {
                    "s" -> SECOND
                    "m" -> MINUTE
                    "h" -> HOUR
                    "d" -> DAY
                    "w" -> WEEK
                    "mo" -> MONTH
                    "y" -> YEAR
                    else -> continue
                }

                totalSeconds += value * unitSeconds
            }

            return totalSeconds
        }

        /**
         * Formats a duration in seconds to a human-readable string
         * @param seconds The duration in seconds
         * @param showAllUnits Whether to show all units or just the most significant ones
         * @return A formatted string representation of the duration
         */
        fun format(seconds: Long, showAllUnits: Boolean = false): String {
            if (seconds <= 0) return "0s"

            var remaining = seconds
            val parts = mutableListOf<String>()

            val units = mapOf(
                YEAR to "y",
                MONTH to "mo",
                WEEK to "w",
                DAY to "d",
                HOUR to "h",
                MINUTE to "m",
                SECOND to "s"
            )

            for ((unitSeconds, unitSymbol) in units) {
                val value = remaining / unitSeconds
                if (value > 0 || (parts.isNotEmpty() && showAllUnits)) {
                    parts.add("$value$unitSymbol")
                    remaining %= unitSeconds
                }

                // If we don't need to show all units, stop after finding the first non-zero unit
                if (parts.isNotEmpty() && !showAllUnits) {
                    break
                }
            }

            return parts.joinToString(" ")
        }
    }
}
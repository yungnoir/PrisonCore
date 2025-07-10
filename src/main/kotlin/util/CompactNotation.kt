package twizzy.tech.util

class CompactNotation {
    companion object {
        private val suffixes = arrayOf(
            "", "K", "M", "B", "T", "Q", "QQ", "S", "SS", "O", "N", "D", "DD"
        )
        private val suffixToPower = mapOf(
            "" to 0,
            "K" to 3,
            "M" to 6,
            "B" to 9,
            "T" to 12,
            "Q" to 15,
            "QQ" to 18,
            "S" to 21,
            "SS" to 24,
            "O" to 27,
            "N" to 30,
            "D" to 33,
            "DD" to 36
        )

        /**
         * Formats a number into a compact representation with suffix (K, M, B, etc.)
         *
         * @param number The number to format
         * @param decimals The number of decimal places (1 or 2)
         * @return A string representation of the number in compact form
         */
        fun format(number: Number, decimals: Int = 2): String {
            val value = number.toDouble()
            if (value < 1000) return value.toInt().toString()

            // Determine the suffix index (K, M, B, etc.)
            val exp = (Math.log10(value) / 3).toInt()
            val suffix = if (exp < suffixes.size) suffixes[exp] else "^${exp * 3}"

            // Scale the number down
            val scaledValue = value / Math.pow(10.0, exp * 3.0)

            return when (decimals) {
                0 -> String.format("%.0f%s", scaledValue, suffix)
                1 -> String.format("%.1f%s", scaledValue, suffix)
                else -> String.format("%.1f%s", scaledValue, suffix) // Default to 1 decimal if invalid value
            }.replace(".0", "") // Remove trailing .0 when whole numbers
        }

        /**
         * Parses a compact number string (e.g., "10M", "2.5K", "1QQ") into a Double value.
         * Supports suffixes: K, M, B, T, Q, QQ, S, SS, O, N, D, DD (case-insensitive).
         * Throws IllegalArgumentException if the format is invalid.
         */
        fun parse(input: String): Double {
            val trimmed = input.trim().uppercase()
            val regex = Regex("([0-9]+(?:\\.[0-9]+)?)([A-Z]{0,2})")
            val match = regex.matchEntire(trimmed)
                ?: throw IllegalArgumentException("Invalid number format: $input")
            val (numberPart, suffix) = match.destructured
            val number = numberPart.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid number: $numberPart")
            val power = suffixToPower[suffix] ?: throw IllegalArgumentException("Unknown suffix: $suffix")
            return number * Math.pow(10.0, power.toDouble())
        }
    }
}
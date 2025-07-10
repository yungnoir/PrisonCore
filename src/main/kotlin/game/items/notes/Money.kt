package twizzy.tech.game.items.notes

import twizzy.tech.game.items.Item
import net.minestom.server.item.Material
import net.minestom.server.item.ItemStack
import net.kyori.adventure.text.Component
import net.minestom.server.tag.Tag
import twizzy.tech.util.CompactNotation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Represents a money note item with value, issuer, and issue date
 */
class Money(
    val value: Double,
    val issuer: String,
    val issueDate: LocalDateTime,
    name: String = "money_note",
    displayName: String = "Money Note",
    displayItem: Material = Material.PAPER,
    isGlowing: Boolean = true,
    unbreakable: Boolean = true,
    uid: UUID = UUID.randomUUID()
) : Item(
    name = name,
    displayName = displayName,
    displayItem = displayItem,
    isGlowing = isGlowing,
    unbreakable = unbreakable,
    uid = uid
) {
    companion object {
        private val TAG_MONEY_VALUE = Tag.Double("money_value")
        private val TAG_MONEY_ISSUER = Tag.String("money_issuer")
        private val TAG_MONEY_ISSUE_DATE = Tag.String("money_issue_date")
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        /**
         * Creates a Money object from an ItemStack if it represents a valid money note
         * @param itemStack The ItemStack to convert
         * @return The created Money object, or null if conversion failed
         */
        fun fromItemStack(itemStack: ItemStack): Money? {
            // First check if this is a valid Item
            val baseItem = Item.fromItemStack(itemStack) ?: return null

            // Then check if it has our money-specific tags
            val value = itemStack.getTag(TAG_MONEY_VALUE) ?: return null
            val issuer = itemStack.getTag(TAG_MONEY_ISSUER) ?: return null
            val issueDateStr = itemStack.getTag(TAG_MONEY_ISSUE_DATE) ?: return null

            // Parse the issue date
            val issueDate = try {
                LocalDateTime.parse(issueDateStr, DATE_FORMATTER)
            } catch (e: Exception) {
                return null
            }

            return Money(
                value = value,
                issuer = issuer,
                issueDate = issueDate,
                name = baseItem.name,
                displayName = baseItem.displayName,
                displayItem = baseItem.displayItem,
                isGlowing = baseItem.isGlowing,
                unbreakable = baseItem.unbreakable,
                uid = baseItem.uid
            )
        }
    }

    /**
     * Creates a formatted money note ItemStack with lore containing value and issuer information
     */
    override fun toItemStack(): ItemStack {
        // Get the base ItemStack from parent class
        val baseItemStack = super.toItemStack()

        // Format currency value for display
        val formattedValue = "$${CompactNotation.format(value)}"

        // Format the issue date to include time in 12-hour format with AM/PM
        val formattedDate = issueDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a"))

        // Add lore with money information
        val lore = listOf(
            Component.text("§7Value: §6$formattedValue"),
            Component.text("§7Issuer: §f$issuer"),
            Component.text("§7Date: §f$formattedDate")
        )

        // Build a new ItemStack with our custom money data
        return baseItemStack.builder()
            .set(TAG_MONEY_VALUE, value)
            .set(TAG_MONEY_ISSUER, issuer)
            .set(TAG_MONEY_ISSUE_DATE, issueDate.format(DATE_FORMATTER))
            .lore(lore)
            .build()
    }

    /**
     * Creates a new Money note with the specified value
     * @param value The monetary value of the note
     * @param issuer The entity/player who issued the note
     * @return A new Money note instance
     */
    fun withValue(value: Double): Money {
        return Money(
            value = value,
            issuer = this.issuer,
            issueDate = this.issueDate,
            name = this.name,
            displayName = "${formattedValue(value)} Note",
            displayItem = this.displayItem,
            isGlowing = this.isGlowing,
            unbreakable = this.unbreakable,
            uid = UUID.randomUUID() // New note gets new UID
        )
    }

    /**
     * Formats a monetary value for display in the item name
     */
    private fun formattedValue(value: Double): String {
        return "$${CompactNotation.format(value)}"
    }
}
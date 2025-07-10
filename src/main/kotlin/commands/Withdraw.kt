package twizzy.tech.commands

import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import twizzy.tech.game.items.notes.Money
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.item.ItemStack
import java.math.BigDecimal
import java.time.LocalDateTime

class Withdraw {

    @Command("withdraw <amount>")
    suspend fun withdraw(actor: Player, amount: String) {
        try {
            // Parse the amount using CompactNotation
            val parsedAmount = CompactNotation.parse(amount)

            // Convert to BigDecimal for comparison with player balance
            val withdrawAmount = BigDecimal.valueOf(parsedAmount)

            // Check if the amount is valid
            if (parsedAmount <= 0) {
                actor.sendMessage(Component.text("Please enter a positive amount to withdraw", NamedTextColor.RED))
                return
            }

            // Get player UUID
            val uuid = actor.uuid

            // Get player data and check balance
            val playerData = PlayerData.getFromCache(uuid)
            if (playerData == null) {
                actor.sendMessage(Component.text("Failed to retrieve your player data", NamedTextColor.RED))
                return
            }

            // Check if player has enough balance
            if (playerData.balance < withdrawAmount) {
                actor.sendMessage(
                    Component.text("You don't have enough money. Your balance: $", NamedTextColor.RED)
                        .append(Component.text(CompactNotation.format(playerData.balance.toDouble()), NamedTextColor.GOLD))
                )
                return
            }

            // Create the Money note item
            val moneyNote = Money(
                value = parsedAmount,
                issuer = actor.username,
                issueDate = LocalDateTime.now()
            )

            // Convert to ItemStack
            val moneyItem = moneyNote.toItemStack()

            // Try to give the item to the player
            if (!canAddItemToInventory(actor, moneyItem)) {
                actor.sendMessage(Component.text("Your inventory is full!", NamedTextColor.RED))
                return
            }

            // Deduct from player's balance
            PlayerData.addBalance(uuid, withdrawAmount.negate())

            // Give the Money note to the player
            actor.inventory.addItemStack(moneyItem)

            // Send success message
            actor.sendMessage(
                Component.text("Successfully withdrew ", NamedTextColor.GREEN)
                    .append(Component.text("$${CompactNotation.format(parsedAmount)}", NamedTextColor.GOLD))
            )

        } catch (e: IllegalArgumentException) {
            actor.sendMessage(Component.text("Invalid amount format. Example formats: 1000, 1K, 1.5M", NamedTextColor.RED))
        } catch (e: Exception) {
            actor.sendMessage(Component.text("An error occurred while processing your withdrawal", NamedTextColor.RED))
            e.printStackTrace()
        }
    }

    /**
     * Checks if a player can add an item to their inventory
     * @param player The player
     * @param item The item to add
     * @return true if the item can be added, false otherwise
     */
    private fun canAddItemToInventory(player: Player, item: ItemStack): Boolean {
        // Check if the player has an empty slot
        for (i in 0 until player.inventory.size) {
            val stack = player.inventory.getItemStack(i)
            if (stack.isAir) {
                return true
            }
        }
        return false
    }
}
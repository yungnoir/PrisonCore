package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import twizzy.tech.game.items.notes.Money
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation
import twizzy.tech.util.YamlFactory
import java.math.BigDecimal
import java.time.LocalDateTime

class Withdraw {

    @Command("withdraw <amount>")
    @Description("Withdraw money from your balance into a check.")
    suspend fun withdraw(actor: Player, amount: String) {
        try {
            // Parse the amount using CompactNotation
            val parsedAmount = CompactNotation.parse(amount)

            // Convert to BigDecimal for comparison with player balance
            val withdrawAmount = BigDecimal.valueOf(parsedAmount)

            // Check if the amount is valid
            if (parsedAmount <= 0) {
                val message = YamlFactory.getMessage("commands.withdraw.positive_only")
                actor.sendMessage(Component.text(message))
                return
            }

            // Get player UUID
            val uuid = actor.uuid

            // Get player data and check balance
            val playerData = PlayerData.getFromCache(uuid)
            if (playerData == null) {
                val message = YamlFactory.getMessage("commands.withdraw.data_error")
                actor.sendMessage(Component.text(message))
                return
            }

            // Check if player has enough balance
            if (playerData.balance < withdrawAmount) {
                val message = YamlFactory.getMessage(
                    "commands.withdraw.insufficient",
                    mapOf("balance" to CompactNotation.format(playerData.balance.toDouble()))
                )
                actor.sendMessage(Component.text(message))
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
                val message = YamlFactory.getMessage("commands.withdraw.inventory_full")
                actor.sendMessage(Component.text(message))
                return
            }

            // Deduct from player's balance
            PlayerData.addBalance(uuid, withdrawAmount.negate())

            // Give the Money note to the player
            actor.inventory.addItemStack(moneyItem)

            // Send success message
            val message = YamlFactory.getMessage(
                "commands.withdraw.success",
                mapOf("amount" to CompactNotation.format(parsedAmount))
            )
            actor.sendMessage(Component.text(message))

        } catch (e: IllegalArgumentException) {
            val message = YamlFactory.getMessage("commands.withdraw.invalid_amount")
            actor.sendMessage(Component.text(message))
        } catch (e: Exception) {
            val message = YamlFactory.getMessage("commands.withdraw.error")
            actor.sendMessage(Component.text(message))
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
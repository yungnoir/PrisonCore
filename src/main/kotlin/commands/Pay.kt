package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation
import java.math.BigDecimal

class Pay {

    @Command("pay <target> <amount>")
    suspend fun pay(actor: Player, target: String, amount: String) {
        // Parse amount to BigDecimal, supporting compact notation
        val amountValue = try {
            val decimal = if (amount.matches(Regex("^[0-9]+(\\.[0-9]+)?[a-zA-Z]{0,2}$"))) {
                // Try to parse as compact notation (e.g., 10K, 2.5M)
                BigDecimal.valueOf(CompactNotation.parse(amount))
            } else {
                // Try to parse as regular number
                BigDecimal(amount)
            }

            if (decimal <= BigDecimal.ZERO) {
                actor.sendMessage(Component.text("Amount must be greater than 0").color(NamedTextColor.RED))
                return
            }
            decimal
        } catch (e: Exception) {
            actor.sendMessage(Component.text("Invalid amount: $amount").color(NamedTextColor.RED))
            return
        }

        // Get sender's player data and check balance
        val senderUuid = actor.uuid
        val senderData = PlayerData.getFromCache(senderUuid)

        if (senderData == null) {
            actor.sendMessage(Component.text("Failed to retrieve your player data").color(NamedTextColor.RED))
            return
        }

        if (senderData.balance < amountValue) {
            actor.sendMessage(Component.text("You don't have enough balance to transfer ${CompactNotation.format(amountValue)}").color(NamedTextColor.RED))
            return
        }

        // Get target player data
        val targetData = PlayerData.findFromCache(target)

        if (targetData == null) {
            actor.sendMessage(Component.text("Player '$target' not found or hasn't played before").color(NamedTextColor.RED))
            return
        }

        // Don't allow paying yourself
        if (targetData.uuid == senderUuid) {
            actor.sendMessage(Component.text("You cannot pay yourself").color(NamedTextColor.RED))
            return
        }

        // Perform the transaction
        senderData.balance = senderData.balance.subtract(amountValue)
        targetData.balance = targetData.balance.add(amountValue)

        // Notify both players about the successful transaction
        actor.sendMessage(
            Component.text("You sent ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(CompactNotation.format(amountValue)).color(NamedTextColor.GOLD))
                .append(Component.text(" to ").color(NamedTextColor.GREEN))
                .append(Component.text(target).color(NamedTextColor.GOLD))
        )

        // Try to notify the target player if they're online
        val targetPlayer = actor.instance?.players?.find { it.username.equals(target, ignoreCase = true) }
        targetPlayer?.sendMessage(
            Component.text("You received ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(CompactNotation.format(amountValue)).color(NamedTextColor.GOLD))
                .append(Component.text(" from ").color(NamedTextColor.GREEN))
                .append(Component.text(actor.username).color(NamedTextColor.GOLD))
        )
    }
}
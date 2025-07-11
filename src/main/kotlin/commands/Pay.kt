package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation
import twizzy.tech.util.YamlFactory
import java.math.BigDecimal

class Pay {

    @Command("pay <target> <amount>")
    @Description("Transfer money to another player")
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
                val message = YamlFactory.getMessage("commands.pay.positive_only")
                actor.sendMessage(Component.text(message))
                return
            }
            decimal
        } catch (e: Exception) {
            val message = YamlFactory.getMessage(
                "commands.pay.invalid_amount",
                mapOf("amount" to amount)
            )
            actor.sendMessage(Component.text(message))
            return
        }

        // Get sender's player data and check balance
        val senderUuid = actor.uuid
        val senderData = PlayerData.getFromCache(senderUuid)

        if (senderData == null) {
            val message = YamlFactory.getMessage("commands.pay.data_error")
            actor.sendMessage(Component.text(message))
            return
        }

        if (senderData.balance < amountValue) {
            val message = YamlFactory.getMessage(
                "commands.pay.insufficient",
                mapOf("amount" to CompactNotation.format(amountValue))
            )
            actor.sendMessage(Component.text(message))
            return
        }

        // Get target player data
        val targetData = PlayerData.findFromCache(target)

        if (targetData == null) {
            val message = YamlFactory.getMessage(
                "commands.pay.not_found",
                mapOf("target" to target)
            )
            actor.sendMessage(Component.text(message))
            return
        }

        // Don't allow paying yourself
        if (targetData.uuid == senderUuid) {
            val message = YamlFactory.getMessage("commands.pay.self_pay")
            actor.sendMessage(Component.text(message))
            return
        }

        // Perform the transaction
        senderData.balance = senderData.balance.subtract(amountValue)
        targetData.balance = targetData.balance.add(amountValue)

        // Notify both players about the successful transaction
        val senderMessage = YamlFactory.getMessage(
            "commands.pay.success",
            mapOf("amount" to CompactNotation.format(amountValue), "target" to target)
        )
        actor.sendMessage(Component.text(senderMessage))

        // Try to notify the target player if they're online
        val targetPlayer = actor.instance?.players?.find { it.username.equals(target, ignoreCase = true) }
        targetPlayer?.let {
            val receivedMessage = YamlFactory.getMessage(
                "commands.pay.received",
                mapOf("amount" to CompactNotation.format(amountValue), "sender" to actor.username)
            )
            it.sendMessage(Component.text(receivedMessage))
        }
    }
}
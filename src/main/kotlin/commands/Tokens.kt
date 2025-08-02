package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation
import twizzy.tech.util.YamlFactory
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.*
import kotlinx.coroutines.runBlocking
import revxrsal.commands.annotation.Optional

@Command("tokens")
class Tokens {

    @Command("tokens")
    fun tokensUsage(actor: Player) {
        val helpMessages = YamlFactory.getCommandHelp("tokens")
        helpMessages.forEach { message ->
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("balance")
    suspend fun balanceToken(actor: Player, @Optional player: String?) {
        val numberFormat = NumberFormat.getInstance(Locale.US)
        if (player.isNullOrBlank()) {
            val data = PlayerData.getFromCache(actor.uuid)
            if (data == null) {
                actor.sendMessage(Component.text("Could not load your data.")); return
            }
            val compact = CompactNotation.format(data.tokens)
            val full = numberFormat.format(data.tokens)
            val message = YamlFactory.getMessage("commands.tokens.own", mapOf("amount" to compact))
            val component = Component.text(message)
                .hoverEvent(HoverEvent.showText(Component.text(full)))
            actor.sendMessage(component)
        } else {
            val targetData = PlayerData.findFromCache(player)
            if (targetData == null) {
                actor.sendMessage(Component.text("Player not found: $player")); return
            }
            val compact = CompactNotation.format(targetData.tokens)
            val full = numberFormat.format(targetData.tokens)
            val message = YamlFactory.getMessage("commands.tokens.other", mapOf("player" to player, "amount" to compact))
            val component = Component.text(message)
                .hoverEvent(HoverEvent.showText(Component.text(full)))
            actor.sendMessage(component)
        }
    }

    @Subcommand("pay <player> <amount>")
    suspend fun payToken(actor: Player, @Optional player: String, @Optional amount: String) {
        if (player.isNullOrBlank() || amount.isNullOrBlank()) {
            val usage = YamlFactory.getMessage("commands.tokens.pay-usage")
            actor.sendMessage(Component.text(usage))
            return
        }

        val amountValue = try {
            BigDecimal.valueOf(CompactNotation.parse(amount)).takeIf { it > BigDecimal.ZERO } ?: run {
                actor.sendMessage(Component.text("Invalid amount: $amount")); return
            }
        } catch (e: Exception) {
            actor.sendMessage(Component.text("Invalid amount: $amount")); return
        }
        val senderData = PlayerData.getFromCache(actor.uuid)
        if (senderData == null) {
            actor.sendMessage(Component.text("Could not load your data.")); return
        }
        if (senderData.tokens < amountValue) {
            actor.sendMessage(Component.text("Insufficient tokens.")); return
        }
        val targetData = PlayerData.findFromCache(player)
        if (targetData == null) {
            actor.sendMessage(Component.text("Player not found: $player")); return
        }
        if (targetData.uuid == senderData.uuid) {
            actor.sendMessage(Component.text("You cannot pay yourself.")); return
        }
        senderData.tokens = senderData.tokens.subtract(amountValue)
        targetData.tokens = targetData.tokens.add(amountValue)
        actor.sendMessage(Component.text("Paid ${CompactNotation.format(amountValue)} tokens to $player"))
        val targetPlayer = actor.instance?.players?.find { it.username.equals(player, true) }
        targetPlayer?.sendMessage(Component.text("You received ${CompactNotation.format(amountValue)} tokens from ${actor.username}!"))
    }
}
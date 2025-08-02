package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation
import twizzy.tech.util.YamlFactory
import java.text.NumberFormat
import java.util.*

class Balance {
    @Command("balance", "bal")
    @Description("Check your or another player's balance")
    suspend fun checkBalance(actor: Player, @Optional player: String) {
        val numberFormat = NumberFormat.getInstance(Locale.US)
        if (player.isNullOrBlank()) {
            val balance = PlayerData.getBalance(actor.username)
            val compact = CompactNotation.format(balance ?: 0.0)
            val full = balance?.let { numberFormat.format(it) } ?: "0"

            val message = YamlFactory.getMessage(
                "commands.balance.own",
                mapOf("amount" to compact)
            )
            val component = Component.text(message)
                .hoverEvent(HoverEvent.showText(Component.text("$${full}")))
            actor.sendMessage(component)
        } else {
            val balance = PlayerData.getBalance(player)
            if (balance == null) {
                val message = YamlFactory.getMessage("commands.balance.not_found")
                actor.sendMessage(Component.text(message))
                return
            }
            val compact = CompactNotation.format(balance)
            val full = numberFormat.format(balance)

            val message = YamlFactory.getMessage(
                "commands.balance.other",
                mapOf("player" to player, "amount" to compact)
            )
            val component = Component.text(message)
                .hoverEvent(HoverEvent.showText(Component.text("$${full}")))
            actor.sendMessage(component)
        }
    }

    @Command("balance top", "baltop")
    @Description("Show the top balances on the server")
    suspend fun balanceTop(actor: Player, @Optional page: Int?) {
        Leaderboard().printLeaderboard(actor, "Balance", page ?: 1)
    }
}
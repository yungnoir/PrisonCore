package twizzy.tech.commands

import java.text.NumberFormat
import java.util.Locale
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent

class Balance {
    @Command("balance")
    suspend fun checkBalance(actor: Player, @Optional target: String) {
        val numberFormat = NumberFormat.getInstance(Locale.US)
        if (target.isNullOrBlank()) {
            val balance = PlayerData.getBalance(actor.username)
            val compact = CompactNotation.format(balance ?: 0.0)
            val full = balance?.let { numberFormat.format(it) } ?: "0"
            val component = Component.text("§eBalance: §a$$compact")
                .hoverEvent(HoverEvent.showText(Component.text("$${full}")))
            actor.sendMessage(component)
        } else {
            val balance = PlayerData.getBalance(target)
            if (balance == null) {
                actor.sendMessage("§cPlayer not found or has no balance.")
                return
            }
            val compact = CompactNotation.format(balance)
            val full = numberFormat.format(balance)
            val component = Component.text("§e${target}'s Balance: §a$$compact")
                .hoverEvent(HoverEvent.showText(Component.text("$${full}")))
            actor.sendMessage(component)
        }
    }
}
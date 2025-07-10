package twizzy.tech.commands

import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import twizzy.tech.game.ActivityTracker
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation

@Command("economy", "eco")
class Economy {

    // Get the ActivityTracker instance
    private val activityTracker = ActivityTracker.getInstance()

    @Command("economy", "eco")
    fun economyUsage(actor: Player) {
        actor.sendMessage("§aEconomy Help:")
        actor.sendMessage("§7/economy <player> add <amount> - Add money to a player (admin)")
        actor.sendMessage("§7/economy <player> take <amount> - Take money from a player (admin)")
        actor.sendMessage("§7/economy <player> set <amount> - Set a player's balance (admin)")
    }

    @Subcommand("<target> add <amount>")
    suspend fun add(actor: Player, target: Player, amount: String) {
        val parsedAmount = try { CompactNotation.parse(amount) } catch (e: Exception) {
            actor.sendMessage("§cInvalid amount: $amount")
            return
        }
        if (parsedAmount <= 0) {
            actor.sendMessage("§cAmount must be positive.")
            return
        }
        PlayerData.addBalance(target.uuid, parsedAmount.toBigDecimal())
        actor.sendMessage("§aAdded §f${CompactNotation.format(parsedAmount)} §ato ${target.username}'s balance.")
        target.sendMessage("§aYour balance was increased by §f${CompactNotation.format(parsedAmount)}§a.")

        // Log the activity
        activityTracker.logEconomyActivity(actor.username, target.username, CompactNotation.format(parsedAmount), "add")
    }

    @Subcommand("<target> take <amount>")
    suspend fun take(actor: Player, target: Player, amount: String) {
        val parsedAmount = try { CompactNotation.parse(amount) } catch (e: Exception) {
            actor.sendMessage("§cInvalid amount: $amount")
            return
        }
        if (parsedAmount <= 0) {
            actor.sendMessage("§cAmount must be positive.")
            return
        }
        val current = PlayerData.getBalance(target.username) ?: 0.0
        PlayerData.addBalance(target.uuid, -parsedAmount.toBigDecimal())
        actor.sendMessage("§aTook §f${CompactNotation.format(parsedAmount)} §afrom ${target.username}.")
        target.sendMessage("§c${CompactNotation.format(parsedAmount)} was taken from your balance.")

        // Log the activity
        activityTracker.logEconomyActivity(actor.username, target.username, CompactNotation.format(parsedAmount), "take")
    }

    @Subcommand("<target> set <amount>")
    suspend fun set(actor: Player, target: Player, amount: String) {
        val parsedAmount = try { CompactNotation.parse(amount) } catch (e: Exception) {
            actor.sendMessage("§cInvalid amount: $amount")
            return
        }
        if (parsedAmount < 0) {
            actor.sendMessage("§cAmount cannot be negative.")
            return
        }
        PlayerData.setBalance(target.uuid, parsedAmount.toBigDecimal())
        actor.sendMessage("§aSet ${target.username}'s balance to §f${CompactNotation.format(parsedAmount)}§a.")
        target.sendMessage("§aYour balance has been set to §f${CompactNotation.format(parsedAmount)}§a.")

        // Log the activity
        activityTracker.logEconomyActivity(actor.username, target.username, CompactNotation.format(parsedAmount), "set")
    }
}
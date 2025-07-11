package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.game.ActivityTracker
import twizzy.tech.player.PlayerData
import twizzy.tech.util.CompactNotation
import twizzy.tech.util.YamlFactory

@Command("economy", "eco")
@CommandPermission("admin.economy")
@Description("Manage a player's balance")
class Economy {

    // Get the ActivityTracker instance
    private val activityTracker = ActivityTracker.getInstance()

    @Command("economy", "eco")
    fun economyUsage(actor: Player) {
        val helpMessages = YamlFactory.getCommandHelp("economy")
        helpMessages.forEach { message ->
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("<target> add <amount>")
    suspend fun add(actor: Player, target: Player, amount: String) {
        val parsedAmount = try {
            CompactNotation.parse(amount)
        } catch (e: Exception) {
            val message = YamlFactory.getMessage(
                "commands.economy.invalid_amount",
                mapOf("amount" to amount)
            )
            actor.sendMessage(Component.text(message))
            return
        }

        if (parsedAmount <= 0) {
            val message = YamlFactory.getMessage("commands.economy.positive_only")
            actor.sendMessage(Component.text(message))
            return
        }

        PlayerData.addBalance(target.uuid, parsedAmount.toBigDecimal())

        val actorMessage = YamlFactory.getMessage(
            "commands.economy.add.success",
            mapOf("amount" to CompactNotation.format(parsedAmount), "target" to target.username)
        )
        actor.sendMessage(Component.text(actorMessage))

        val targetMessage = YamlFactory.getMessage(
            "commands.economy.add.notify",
            mapOf("amount" to CompactNotation.format(parsedAmount))
        )
        target.sendMessage(Component.text(targetMessage))

        // Log the activity
        activityTracker.logEconomyActivity(actor.username, target.username, CompactNotation.format(parsedAmount), "add")
    }

    @Subcommand("<target> take <amount>")
    suspend fun take(actor: Player, target: Player, amount: String) {
        val parsedAmount = try {
            CompactNotation.parse(amount)
        } catch (e: Exception) {
            val message = YamlFactory.getMessage(
                "commands.economy.invalid_amount",
                mapOf("amount" to amount)
            )
            actor.sendMessage(Component.text(message))
            return
        }

        if (parsedAmount <= 0) {
            val message = YamlFactory.getMessage("commands.economy.positive_only")
            actor.sendMessage(Component.text(message))
            return
        }

        PlayerData.addBalance(target.uuid, -parsedAmount.toBigDecimal())

        val actorMessage = YamlFactory.getMessage(
            "commands.economy.take.success",
            mapOf("amount" to CompactNotation.format(parsedAmount), "target" to target.username)
        )
        actor.sendMessage(Component.text(actorMessage))

        val targetMessage = YamlFactory.getMessage(
            "commands.economy.take.notify",
            mapOf("amount" to CompactNotation.format(parsedAmount))
        )
        target.sendMessage(Component.text(targetMessage))

        // Log the activity
        activityTracker.logEconomyActivity(actor.username, target.username, CompactNotation.format(parsedAmount), "take")
    }

    @Subcommand("<target> set <amount>")
    suspend fun set(actor: Player, target: Player, amount: String) {
        val parsedAmount = try {
            CompactNotation.parse(amount)
        } catch (e: Exception) {
            val message = YamlFactory.getMessage(
                "commands.economy.invalid_amount",
                mapOf("amount" to amount)
            )
            actor.sendMessage(Component.text(message))
            return
        }

        if (parsedAmount < 0) {
            val message = YamlFactory.getMessage("commands.economy.non_negative")
            actor.sendMessage(Component.text(message))
            return
        }

        PlayerData.setBalance(target.uuid, parsedAmount.toBigDecimal())

        val actorMessage = YamlFactory.getMessage(
            "commands.economy.set.success",
            mapOf("amount" to CompactNotation.format(parsedAmount), "target" to target.username)
        )
        actor.sendMessage(Component.text(actorMessage))

        val targetMessage = YamlFactory.getMessage(
            "commands.economy.set.notify",
            mapOf("amount" to CompactNotation.format(parsedAmount))
        )
        target.sendMessage(Component.text(targetMessage))

        // Log the activity
        activityTracker.logEconomyActivity(actor.username, target.username, CompactNotation.format(parsedAmount), "set")
    }
}
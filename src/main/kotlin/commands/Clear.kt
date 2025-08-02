package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.util.YamlFactory

class Clear {

    @Command("clear <player>")
    @CommandPermission("command.clear")
    @Description("Clear your or another player's inventory")
    fun clearInventory(actor: Player, @Optional player: Player?) {
        if (player == null) {
            actor.inventory.clear()
            val message = YamlFactory.getMessage("commands.clear.success")
            actor.sendMessage(Component.text(message))
        } else {
            player.inventory.clear()

            val actorMessage = YamlFactory.getMessage(
                "commands.clear.success_other",
                mapOf("player" to player.username)
            )
            actor.sendMessage(Component.text(actorMessage))

            val targetMessage = YamlFactory.getMessage(
                "commands.clear.success_notify",
                mapOf("player" to actor.username)
            )
            player.sendMessage(Component.text(targetMessage))
        }
    }
}
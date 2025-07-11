package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.util.YamlFactory

class Clear {

    @Command("clear <target>")
    @CommandPermission("command.clear")
    @Description("Clear your or another player's inventory")
    fun clearInventory(actor: Player, @Optional target: Player?) {
        if (target == null) {
            actor.inventory.clear()
            val message = YamlFactory.getMessage("commands.clear.success")
            actor.sendMessage(Component.text(message))
        } else {
            target.inventory.clear()

            val actorMessage = YamlFactory.getMessage(
                "commands.clear.success_other",
                mapOf("target" to target.username)
            )
            actor.sendMessage(Component.text(actorMessage))

            val targetMessage = YamlFactory.getMessage(
                "commands.clear.success_notify",
                mapOf("player" to actor.username)
            )
            target.sendMessage(Component.text(targetMessage))
        }
    }
}
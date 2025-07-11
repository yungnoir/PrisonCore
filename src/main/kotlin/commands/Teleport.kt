package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.util.YamlFactory

@CommandPermission("command.teleport")
class Teleport {

    @Command("teleport <target>", "tp <target>")
    @Description("Teleport to another player")
    fun teleport(actor: Player, target: Player) {
        actor.teleport(target.position)
        if (actor.instance != target.instance) {
            actor.instance = target.instance
        }

        val message = YamlFactory.getMessage(
            "commands.teleport.success",
            mapOf("target" to target.username)
        )
        actor.sendMessage(Component.text(message))
    }

    @Command("tphere <target>")
    @Description("Teleport another player to you")
    fun teleportHere(actor: Player, target: Player) {
        target.teleport(actor.position)
        if (actor.instance != target.instance) {
            target.instance = actor.instance
        }

        val message = YamlFactory.getMessage(
            "commands.teleport.success_here",
            mapOf("target" to target.username)
        )
        actor.sendMessage(Component.text(message))

        val targetMessage = YamlFactory.getMessage(
            "commands.teleport.teleported_by",
            mapOf("player" to actor.username)
        )
        target.sendMessage(Component.text(targetMessage))
    }

    @Command("teleport <target> <player>", "tp <target> <player>")
    fun teleportTo(actor: Player, target: Player, player: Player) {
        target.teleport(player.position)
        if (target.instance != player.instance) {
            target.instance = player.instance
        }

        val message = YamlFactory.getMessage(
            "commands.teleport.success_other",
            mapOf("target" to target.username, "destination" to player.username)
        )
        actor.sendMessage(Component.text(message))

        val targetMessage = YamlFactory.getMessage(
            "commands.teleport.teleported_to_you",
            mapOf("player" to actor.username)
        )
        target.sendMessage(Component.text(targetMessage))
    }

}
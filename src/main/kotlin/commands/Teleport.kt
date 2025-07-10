package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command

class Teleport {

    @Command("teleport <target>", "tp <target>")
    fun teleport(actor: Player, target: Player) {
        if (actor.instance != target.instance) {
            actor.instance = target.instance
        }
        actor.teleport(target.position)

        actor.sendMessage(Component.text("You have teleported to ${target.username}."))
    }

    @Command("tphere <target>")
    fun teleportHere(actor: Player, target: Player) {
        if (actor.instance != target.instance) {
            target.instance = actor.instance
        }
        target.teleport(actor.position)

        actor.sendMessage(Component.text("You teleported ${target.username} your location."))
    }

    @Command("teleport <target> <player>", "tp <target> <player>")
    fun teleportTo(actor: Player, target: Player, player: Player) {
        if (target.instance != player.instance) {
            target.instance = player.instance
        }
        target.teleport(player.position)

        actor.sendMessage(Component.text("You teleported ${target.username} to ${player.username}."))
    }

}
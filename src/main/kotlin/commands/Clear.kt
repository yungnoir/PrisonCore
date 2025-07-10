package twizzy.tech.commands

import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.minestom.annotation.CommandPermission

class Clear {

    @Command("clear <target>")
    @CommandPermission("command.clear")
    fun clearInventory(actor: Player, @Optional target: Player?) {
        if (target == null) {
            actor.inventory.clear()
            actor.sendMessage("§aYour inventory has been cleared.")
        } else {
            target.inventory.clear()
            actor.sendMessage("§a${target.username}'s inventory has been cleared.")
            target.sendMessage("§aYour inventory has been cleared by ${actor.username}.")
        }
    }
}
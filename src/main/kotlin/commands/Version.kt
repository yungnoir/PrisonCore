package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import twizzy.tech.author
import twizzy.tech.version

class Version {

    @Command("version", "ver")
    @Description("Display the current version of the server")
    fun version(actor: Player) {
        actor.sendMessage(Component.text("The server is running PrisonCore v$version by $author.", NamedTextColor.GREEN)
            .hoverEvent(HoverEvent.showText(Component.text("https://twizzy.tech")))
            .clickEvent(ClickEvent.openUrl("https://twizzy.tech")))
    }
}
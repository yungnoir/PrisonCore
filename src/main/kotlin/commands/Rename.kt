package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.game.items.pickaxe.Pickaxe
import twizzy.tech.gameEngine
import twizzy.tech.util.YamlFactory

class Rename {

    @Command("rename <name>")
    @CommandPermission("command.rename")
    @Description("Rename the item in your hand")
    fun rename(actor: Player, @Optional name: String?) {
        if (name.isNullOrBlank()) {
            val usage = YamlFactory.getMessage("commands.rename.usage")
            actor.sendMessage(Component.text(usage))
            return
        }
        // Convert color codes like &a, &c, etc. to proper Adventure Components
        val coloredName = LegacyComponentSerializer.legacyAmpersand().deserialize(name)

        // Check if the item is a custom pickaxe
        val currentItem = actor.itemInMainHand
        val pickaxe = Pickaxe.fromItemStack(currentItem)

        if (pickaxe != null) {
            // It's a custom pickaxe - create a new pickaxe with the updated display name
            val renamedPickaxe = pickaxe.copy(
                displayName = name.replace("&", "§") // Keep the original string with color codes
            )

            // Replace the item with the renamed pickaxe
            actor.itemInMainHand = renamedPickaxe.toItemStack()
        } else {
            // It's a regular item - use the original rename logic
            actor.itemInMainHand = currentItem.withCustomName(coloredName)
        }
        val success = twizzy.tech.util.YamlFactory.getMessage("commands.rename.success", mapOf("new_name" to name))
        actor.sendMessage(Component.text(success))
    }
}
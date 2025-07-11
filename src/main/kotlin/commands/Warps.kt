package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import twizzy.tech.util.Worlds
import twizzy.tech.util.YamlFactory

class Warps(private val worlds: Worlds) {

    @Command("spawn")
    @Description("Teleport to the spawn world")
    suspend fun teleportSpawn(actor: Player, @Optional target: Player?) {
        try {
            val instance = worlds.getSpawnWorld()
            actor.teleport(worlds.getSpawnPoint())
            if (actor.instance != instance) {
                actor.instance = instance
            }

            val message = YamlFactory.getMessage("commands.warps.spawn.success")
            actor.sendMessage(Component.text(message))
        } catch (e: Exception) {
            val message = YamlFactory.getMessage(
                "commands.warps.spawn.failed",
                mapOf("error" to e.message.orEmpty())
            )
            actor.sendMessage(Component.text(message))
        }
    }


}
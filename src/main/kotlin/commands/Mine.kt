package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import twizzy.tech.util.InstanceMap

@Command("mine")
class Mine(private val instanceMap: InstanceMap) {

    @Command("mine")
    fun mineUsage(actor: Player){
        actor.sendMessage("/mine go <player> - Teleport to your mine or another player's mine")
    }

    @Subcommand("go <player>")
    suspend fun goToMine(actor: Player, @Optional player: Player?) {
        if (player == null) {
            val instance = instanceMap.getInstance(actor)
            val spawnPos = instanceMap.getSpawn(actor)

            if (instance != null && spawnPos != null) {
                if (actor.instance == instance) {
                    actor.teleport(Pos(spawnPos))
                } else {
                    actor.setInstance(instance, spawnPos)
                }
            }

            actor.sendMessage(
                Component.text("Teleported to your mine!")
                    .color(NamedTextColor.GREEN)
            )
        } else {
            // Teleport to another player's mine
            val targetInstance = instanceMap.getInstance(player)
            val targetSpawn = instanceMap.getSpawn(player)

            if (targetInstance != null && targetSpawn != null) {

                if (actor.instance == targetInstance) {
                    actor.teleport(Pos(targetSpawn))
                } else {
                    actor.setInstance(targetInstance, targetSpawn)
                }

                actor.sendMessage(
                    Component.text("Teleported to ${player.username}'s mine!")
                        .color(NamedTextColor.GREEN)
                )
            } else {
                actor.sendMessage(
                    Component.text("${player.username} does not have a mine instance.")
                        .color(NamedTextColor.RED)
                )
            }
        }
    }
}
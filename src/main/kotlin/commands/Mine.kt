package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import twizzy.tech.util.InstanceMap
import twizzy.tech.util.YamlFactory

@Command("mine")
class Mine(private val instanceMap: InstanceMap) {

    @Command("mine")
    @Description("Manage your personal mine")
    fun mineUsage(actor: Player) {
        val helpMessages = YamlFactory.getCommandHelp("mine")
        helpMessages.forEach { message ->
            actor.sendMessage(Component.text(message))
        }
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

            val message = YamlFactory.getMessage("commands.mine.success")
            actor.sendMessage(Component.text(message))
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

                val message = YamlFactory.getMessage(
                    "commands.mine.success_other",
                    mapOf("player" to player.username)
                )
                actor.sendMessage(Component.text(message))
            } else {
                val message = YamlFactory.getMessage(
                    "commands.mine.no_instance",
                    mapOf("player" to player.username)
                )
                actor.sendMessage(Component.text(message))
            }
        }
    }
}
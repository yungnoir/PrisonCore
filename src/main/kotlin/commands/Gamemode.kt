package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.util.YamlFactory

class Gamemode {

    @Command("gamemode")
    @CommandPermission("command.gamemode")
    @Description("Change your or another player's game mode")
    fun gameMode(actor: Player, gamemode: GameMode) {
        setGameMode(actor, gamemode)
    }

    @Command("gmc")
    @CommandPermission("command.gamemode.creative")
    fun gameModeCreative(actor: Player) {
        setGameMode(actor, GameMode.CREATIVE)
    }

    @Command("gms")
    @CommandPermission("command.gamemode.survival")
    fun gameModeSurvival(actor: Player) {
        setGameMode(actor, GameMode.SURVIVAL)
    }

    private fun setGameMode(actor: Player, gamemode: GameMode) {
        actor.gameMode = gamemode
        actor.isAllowFlying = true

        val message = YamlFactory.getMessage(
            "commands.gamemode.success",
            mapOf("mode" to gamemode.name.lowercase().replaceFirstChar { it.uppercase() })
        )
        actor.sendMessage(Component.text(message))
    }
}
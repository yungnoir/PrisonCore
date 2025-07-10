package twizzy.tech.commands

import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.minestom.annotation.CommandPermission

class Gamemode {

    @Command("gamemode")
    @CommandPermission("command.gamemode")
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
        actor.sendMessage("Your current game mode is ${gamemode.name.lowercase().replaceFirstChar { it.uppercase() }}.")
    }
}
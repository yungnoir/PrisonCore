package twizzy.tech.commands

import kotlinx.coroutines.delay
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import twizzy.tech.util.Worlds

class Warps(private val worlds: Worlds) {


    @Command("spawn")
    suspend fun teleportSpawn(actor: Player, @Optional target: Player?) {
        val instance = worlds.getSpawnWorld()
        actor.teleport(worlds.getSpawnPoint())
        if (actor.instance != instance) {
            actor.instance = instance
        }
        actor.sendMessage("You have been teleported to spawn.")
    }


}
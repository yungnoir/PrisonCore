package twizzy.tech.listeners

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import twizzy.tech.game.PlayerData

class ConnectionHandler(minecraftServer: MinecraftServer) {

    init {
        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, AsyncPlayerConfigurationEvent::class.java) { event ->
            val player = event.player
            println(PlayerData(player.uuid).balance)
        }

        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, PlayerDisconnectEvent::class.java) { event ->
            val player = event.player
            PlayerData(player.uuid).savePlayerData()
        }
    }
}
package twizzy.tech.commands

import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import twizzy.tech.game.PlayerData

class Balance {


    @Command("balance")
    suspend fun checkBalance(actor: Player) {
        val balance = PlayerData(actor.uuid).balance
        actor.sendMessage("§eBalance: §a$$balance")
    }
}
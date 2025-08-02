package twizzy.tech.commands

import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import twizzy.tech.player.PlayerData
import twizzy.tech.gameEngine
import java.math.BigDecimal

@Command("rankup")
class Rankup {

    @Command("rankup")
    suspend fun rankup(actor: Player) {
        val uuid = actor.uuid
        val playerData = PlayerData.getFromCache(uuid)
        if (playerData == null) {
            actor.sendMessage("§cCould not load your player data.")
            return
        }
        val currentRank = playerData.rank
        val maxRank = gameEngine.getRankupConfig().maxRank
        if (currentRank >= maxRank) {
            actor.sendMessage("§eYou are already at the maximum rank!")
            return
        }
        val nextRank = currentRank + 1
        val price = gameEngine.getRankupPriceForStep(currentRank)
        val priceFormatted = "%,.2f".format(price)
        if (playerData.balance < BigDecimal.valueOf(price)) {
            actor.sendMessage("§cYou need §6$priceFormatted§c to rank up! Your balance: §6${"%,.2f".format(playerData.balance)}")
            return
        }
        // Deduct balance and increment rank
        playerData.balance = playerData.balance.subtract(BigDecimal.valueOf(price))
        playerData.rank = nextRank
        // Optionally: Save to DB here if needed
        val nextPrice = if (nextRank >= maxRank) null else gameEngine.getRankupPriceForStep(nextRank)
        val nextPriceMsg = if (nextPrice != null) "§7Next rankup price: §6${"%,.2f".format(nextPrice)}" else "§aYou are at the max rank!"
        actor.sendMessage("§aRanked up to §eRank $nextRank§a for §6$priceFormatted§a!\n$nextPriceMsg")
    }


}
package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.player.PlayerData
import twizzy.tech.util.Uniview
import java.math.BigDecimal

class Backpack {

    @Command("backpack", "bp")
    @CommandPermission("command.backpack")
    @Description("View your backpack contents")
    suspend fun openBackpack(actor: Player) {
        val playerData = PlayerData.getFromCache(actor.uuid) ?: run {
            actor.sendMessage(Component.text("Unable to load your backpack data.", NamedTextColor.RED))
            return
        }

        val gui = Uniview.createInterface("test", 3).setTitle("Advanced GUI")

        // Add sell button (reference needed for updates)
        gui.addItem(2, 5, createSellButton(playerData), data = "buttonSellBackpack") { player, slot, clickType, data ->
            val sellValue = twizzy.tech.gameEngine.calculateTotalSellValue(playerData.backpack)
            if (sellValue > BigDecimal.ZERO) {
                playerData.balance = playerData.balance.add(sellValue)
                playerData.backpack.clear()
                player.sendMessage(Component.text("Sold your backpack for $${"%,.2f".format(sellValue)}!", NamedTextColor.GREEN))
            } else {
                player.sendMessage(Component.text("No valuable items to sell.", NamedTextColor.GRAY))
            }

            // Update button to show new values
            gui.items[slot] = gui.items[slot]?.copy(item = createSellButton(playerData)) as Uniview.Interface.GuiItem
        }

        gui.show(actor)
    }

    // Helper function to create/update the sell button
    private fun createSellButton(playerData: PlayerData): ItemStack {
        val totalItems = playerData.backpack.values.sum()
        val sellValue = twizzy.tech.gameEngine.calculateTotalSellValue(playerData.backpack)

        return ItemStack.builder(Material.EMERALD)
            .customName(Component.text("§a§lSell Inventory"))
            .lore(listOf(
                Component.text("§7Blocks: §a${String.format("%,d", totalItems)}"),
                Component.text("§7Value: §e$${"%,.2f".format(sellValue)}")
            ))
            .glowing(sellValue > BigDecimal.ZERO)
            .amount(1)
            .build()
    }

    @Command("backpack sell", "bp sell", "bpsell")
    @CommandPermission("command.backpack.sell")
    @Description("Sell all items in your backpack")
    suspend fun sellBackpack(actor: Player) {
        // Use the new PlayerData.sellBackpack function
        val result = PlayerData.sellBackpack(actor.uuid)

        if (!result.success) {
            actor.sendMessage(Component.text(result.errorMessage ?: "Unknown error occurred.", NamedTextColor.RED))
            // Log the exception if there was one
            result.exception?.let { e ->
                println("Error selling backpack for ${actor.uuid}: ${e.message}")
                e.printStackTrace()
            }
            return
        }

        // Send success message with detailed breakdown using CompactNotation
        actor.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
        actor.sendMessage(Component.text("                              BACKPACK SOLD!", NamedTextColor.GOLD))
        actor.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))

        actor.sendMessage(
            Component.text("  Items Sold: ", NamedTextColor.GRAY)
                .append(Component.text(twizzy.tech.util.CompactNotation.format(result.itemsSold), NamedTextColor.WHITE))
        )

        actor.sendMessage(
            Component.text("  Total Value: ", NamedTextColor.GRAY)
                .append(Component.text("$${twizzy.tech.util.CompactNotation.format(result.totalValue)}", NamedTextColor.GREEN))
        )

        actor.sendMessage(
            Component.text("  New Balance: ", NamedTextColor.GRAY)
                .append(Component.text("$${twizzy.tech.util.CompactNotation.format(result.newBalance!!)}", NamedTextColor.YELLOW))
        )

        actor.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
    }

    /**
     * Formats the Minecraft item ID into a readable display name
     * @param itemId The Minecraft item ID (e.g., "minecraft:stone")
     * @return Formatted display name (e.g., "Stone")
     */
    private fun formatItemName(itemId: String): String {
        // Remove the "minecraft:" prefix if present
        val cleanId = itemId.removePrefix("minecraft:")

        // Split by underscores and capitalize each word
        return cleanId.split("_").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    @Command("test")
    fun testCommand(actor: Player) {
        Uniview.createInterface("test", 3)
            .setTitle("Test GUI")
            .addItem(0, 1, ItemStack.of(Material.STONE, 64), data = "specialStone") { player, slot, clickType, data ->
                player.sendMessage(Component.text("Clicked slot $slot with type $clickType and data $data"))
            }
            .show(actor)
    }
}
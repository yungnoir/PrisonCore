package twizzy.tech.commands

import net.minestom.server.entity.Player
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.Material
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.game.items.Item
import java.util.UUID

@CommandPermission("command.give")
class Give {
    // A simple registry of predefined custom items
    private val customItems = mutableMapOf<String, Item>()

    init {
        // Register some example items
        registerItem(Item("diamond_pickaxe", "§bDiamond Pickaxe", Material.DIAMOND_PICKAXE, isGlowing = true, durability = 3225))
        registerItem(Item("gold_sword", "§6Golden Sword of Light", Material.GOLDEN_SWORD, isGlowing = true, durability = 150))
        registerItem(Item("emerald", "§aLucky Emerald", Material.EMERALD, isGlowing = true))
        registerItem(Item("obsidian", "§5Hardened Obsidian", Material.OBSIDIAN, durability = 2000))
    }

    private fun registerItem(item: Item) {
        customItems[item.name.lowercase()] = item
    }

    @Command("give")
    fun giveCommand(actor: Player, target: Player, itemName: String, @Optional amount: Int = 1) {
        if (amount <= 0) {
            actor.sendMessage("§cAmount must be greater than 0")
            return
        }

        // Find the custom item by name (case-insensitive)
        val itemTemplate = customItems[itemName.lowercase()]

        if (itemTemplate == null) {
            // Only allow predefined custom items, don't use default Minecraft items
            actor.sendMessage("§cUnknown custom item: §f$itemName")
            actor.sendMessage("§7Available items: §f${customItems.keys.joinToString(", ")}")
            return
        }

        // Give the custom item to the player
        giveItemToPlayer(target, itemTemplate, amount)

        // Send confirmation messages
        actor.sendMessage("§aGave §f$amount §aof §f${itemTemplate.displayName} §ato §f${target.username}")
        target.sendMessage("§aYou received §f$amount §aof §f${itemTemplate.displayName}")
    }

    private fun giveItemToPlayer(player: Player, itemTemplate: Item, amount: Int) {
        // For each item in the amount, create a new instance with a unique UID
        repeat(amount) {
            // Create a new item instance with unique ID for each item
            val newItem = Item(
                name = itemTemplate.name,
                displayName = itemTemplate.displayName,
                displayItem = itemTemplate.displayItem,
                unbreakable = itemTemplate.unbreakable,
                isGlowing = itemTemplate.isGlowing,
                durability = itemTemplate.durability,
                uid = UUID.randomUUID() // Each given item gets a unique ID
            )

            // Convert to ItemStack and give to player
            val itemStack = newItem.toItemStack()
            player.inventory.addItemStack(itemStack)
        }
    }

    @Command("iteminfo")
    fun itemInfoCommand(player: Player) {
        val itemInHand = player.itemInMainHand
        if (itemInHand.isAir) {
            player.sendMessage("§cYou must hold an item to get information about it")
            return
        }

        val item = Item.fromItemStack(itemInHand)
        if (item == null) {
            player.sendMessage("§cThis is not a custom item")
            return
        }

        player.sendMessage("§6--- Item Info ---")
        player.sendMessage("§7Name: §f${item.name}")
        player.sendMessage("§7Display Name: §f${item.displayName}")
        player.sendMessage("§7Material: §f${item.displayItem}")
        player.sendMessage("§7Durability: §f${itemInHand.get(ItemComponent.DAMAGE)}/${item.durability}")
        player.sendMessage("§7UID: §f${item.uid}")
    }
}

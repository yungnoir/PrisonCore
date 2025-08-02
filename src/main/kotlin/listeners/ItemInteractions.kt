package twizzy.tech.listeners

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.click.ClickType
import net.minestom.server.item.ItemStack
import net.minestom.server.listener.PlayerHeldListener
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import twizzy.tech.game.RegionManager
import twizzy.tech.game.Engine
import twizzy.tech.game.items.notes.Money
import twizzy.tech.game.items.pickaxe.Pickaxe
import twizzy.tech.game.items.pickaxe.boosters.Experience
import twizzy.tech.player.PlayerData
import twizzy.tech.util.Worlds
import java.math.BigDecimal
import java.time.Duration

class ItemInteractions(private val minecraftServer: MinecraftServer, private val regionManager: RegionManager, private val worlds: Worlds) {

    init {
        // Create a node that listens specifically for player-related events
        val playerNode = EventNode.type("players", EventFilter.PLAYER).setPriority(50)

        // Register the item drop listener to handle item drops
        playerNode.addSuspendingListener(minecraftServer, ItemDropEvent::class.java) { event ->

            // Get the player's eye level position
            val playerEyeLevelPosition = event.player.position.add(0.0, event.player.eyeHeight, 0.0)

            // Create an ItemEntity for the dropped item
            val itemEntity = ItemEntity(event.itemStack)

            // Set the item entity's position to be at the player's eye level
            itemEntity.setInstance(event.player.instance, playerEyeLevelPosition)

            // Apply a controlled velocity to make it drop smoothly
            itemEntity.velocity = event.player.position.add(0.0, -0.10, 0.0).direction().mul(5.0)

            // Set the pickup delay to prevent immediate pickup
            itemEntity.setPickupDelay(Duration.ofMillis(500))
        }

        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, PickupItemEvent::class.java) { event ->
            var itemStack = event.itemStack

            val player = event.livingEntity as Player
            player.inventory.addItemStack(itemStack)
        }

        // INVENTORY CLICK INTERACTIONS - Handle item-on-item interactions
        MinecraftServer.getGlobalEventHandler().addListener(InventoryPreClickEvent::class.java) { event ->
            val player = event.player
            val clickedItem = event.clickedItem
            val cursorItem = event.cursorItem

            // Check if this is an item-on-item interaction (player has item on cursor and clicks another item)
            if (!cursorItem.isAir && !clickedItem.isAir && event.clickType == ClickType.LEFT_CLICK) {
                if (shouldHandleInteraction(cursorItem, clickedItem)) {
                    event.isCancelled = true
                    handleItemOnItemInteraction(player, cursorItem, clickedItem, event)
                }
            }
        }

        // ITEM DETECTION
        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, PlayerChangeHeldSlotEvent::class.java) { event ->

            val player = event.player

            val pickaxe = Pickaxe.fromItemStack(player.itemInMainHand)

            if (pickaxe != null) {
                // Sync player experience bar with pickaxe experience progress
                player.exp = pickaxe.expProgress()
                player.level = pickaxe.level

                // Check for Speed enchant and apply speed effect
                val speedEnchant = pickaxe.getEnchantByName("Speed")
                if (speedEnchant != null) {
                    // Remove existing speed effect if present
                    player.removeEffect(PotionEffect.SPEED)
                    // Apply speed effect with amplifier = enchant level - 1, duration -1 (infinite)
                    player.addEffect(Potion(PotionEffect.SPEED, speedEnchant.level -1, -1))
                } else {
                    // Remove speed effect if not present on pickaxe
                    player.removeEffect(PotionEffect.SPEED)
                }

                // Check for Speed enchant and apply speed effect
                val hasteEnchant = pickaxe.getEnchantByName("Haste")
                if (hasteEnchant != null) {
                    // Remove existing speed effect if present
                    player.removeEffect(PotionEffect.HASTE)
                    // Apply speed effect with amplifier = enchant level - 1, duration -1 (infinite)
                    player.addEffect(Potion(PotionEffect.HASTE, hasteEnchant.level -1, -1))
                } else {
                    // Remove speed effect if not present on pickaxe
                    player.removeEffect(PotionEffect.HASTE)
                }
            } else {
                // Reset player's XP and level when not holding a pickaxe
                player.exp = 0.0f
                player.level = 0
                // Remove speed effect if not holding a pickaxe
                player.removeEffect(PotionEffect.SPEED)
            }

        }


        // MONEY NOTE INTERACTIONS
        MinecraftServer.getGlobalEventHandler().addSuspendingListener(minecraftServer, PlayerUseItemEvent::class.java) { event ->
            val player = event.player
            val itemStack = event.itemStack

            // Try to parse the item as a Money note
            val moneyNote = Money.fromItemStack(itemStack)

            // If this is a valid money note, process it
            if (moneyNote != null) {
                // Cancel the default interaction
                event.isCancelled = true

                // Get the note value and add it to player's balance
                val noteValue = moneyNote.value
                val bigDecimalValue = BigDecimal.valueOf(noteValue)

                // Add to the player's balance
                val success = PlayerData.addBalance(player.uuid, bigDecimalValue)

                if (success) {
                    // Remove the money note from player's hand (consume it)
                    val currentItem = player.getItemInMainHand()

                    // If there's only one note, replace with air
                    // Otherwise, decrease the amount by 1
                    if (currentItem.amount() == 1) {
                        player.setItemInMainHand(ItemStack.AIR)
                    } else {
                        player.setItemInMainHand(currentItem.withAmount(currentItem.amount() - 1))
                    }

                    // Send success message
                    val formattedValue = "$${twizzy.tech.util.CompactNotation.format(noteValue)}"
                    player.sendMessage(
                        Component.text("You redeemed a note worth ", NamedTextColor.GREEN)
                            .append(Component.text(formattedValue, NamedTextColor.GOLD))
                            .append(Component.text("!", NamedTextColor.GREEN))
                    )

                    // Play success sound
                    player.playSound(
                        Sound.sound(
                            Key.key("minecraft:entity.player.levelup"),
                            Sound.Source.PLAYER,
                            1.0f,
                            1.0f
                        )
                    )
                } else {
                    // Show error if balance update failed
                    player.sendMessage(
                        Component.text("Failed to redeem money note. Please try again.",
                        NamedTextColor.RED)
                    )
                }
            }
        }

        // Register the event handler to the global event handler
        MinecraftServer.getGlobalEventHandler().addChild(playerNode)
    }

    /**
     * Determines if we should handle this item-on-item interaction
     */
    private fun shouldHandleInteraction(cursorItem: ItemStack, targetItem: ItemStack): Boolean {
        // Check if cursor item is an Experience booster and target is a pickaxe
        val experienceBooster = Experience.fromItemStack(cursorItem)
        if (experienceBooster != null) {
            val pickaxe = Pickaxe.fromItemStack(targetItem)
            if (pickaxe != null) {
                return true
            }
        }

        // Add more interaction type checks here
        // Return true if any interaction should be handled

        return false
    }

    /**
     * Handles when a player clicks one item on another item in their inventory
     * @param player The player performing the interaction
     * @param cursorItem The item the player is holding/dragging
     * @param targetItem The item being clicked on
     * @param event The original inventory pre-click event
     */
    private fun handleItemOnItemInteraction(
        player: Player,
        cursorItem: ItemStack,
        targetItem: ItemStack,
        event: InventoryPreClickEvent
    ) {
        val experienceBooster = Experience.fromItemStack(cursorItem)
        if (experienceBooster != null) {
            val pickaxe = Pickaxe.fromItemStack(targetItem)
            if (pickaxe != null) {
                handleBoosterOnPickaxe(player, experienceBooster, pickaxe, event)
                return
            }
        }

        // Add more item-on-item interaction handlers here
        // For example: enchant books on pickaxes, gems on weapons, etc.

        // If no specific interaction was handled, you can either:
        // 1. Allow the default behavior (don't cancel)
        // 2. Cancel and show a message
        // For now, we'll just log the interaction for debugging
        println("[ItemInteraction] ${player.username} used ${cursorItem.material()} on ${targetItem.material()}")
    }

    /**
     * Handles applying an experience booster to a pickaxe
     */
    private fun handleBoosterOnPickaxe(
        player: Player,
        booster: Experience,
        pickaxe: Pickaxe,
        event: InventoryPreClickEvent
    ) {
        if (!booster.isActive()) {
            player.sendMessage(Component.text("§cThis experience booster has expired!", NamedTextColor.RED))
            return
        }
        val updatedPickaxe = pickaxe.addBooster(booster)
        val newCursorItem = if (event.cursorItem.amount() > 1) {
            event.cursorItem.withAmount(event.cursorItem.amount() - 1)
        } else {
            ItemStack.AIR
        }
        event.inventory.setItemStack(event.slot, updatedPickaxe.toItemStack())
        event.player.inventory.cursorItem = newCursorItem
        val multiplierText = if (booster.multiplier == booster.multiplier.toInt().toDouble()) {
            "${booster.multiplier.toInt()}x"
        } else {
            String.format("%.1fx", booster.multiplier)
        }
        val durationText = booster.getRemainingDuration()?.let { "${it}s" } ?: "Permanent"
        player.sendMessage(
            Component.text("§aSuccessfully applied §f${multiplierText} Experience Booster §a(${durationText}) §ato your pickaxe!")
        )
        player.playSound(
            Sound.sound(
                Key.key("minecraft:entity.experience_orb.pickup"),
                Sound.Source.PLAYER,
                1.0f,
                1.5f
            )
        )
    }
}

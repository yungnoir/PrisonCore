package twizzy.tech.listeners

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import twizzy.tech.game.RegionManager
import twizzy.tech.game.items.notes.Money
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
}
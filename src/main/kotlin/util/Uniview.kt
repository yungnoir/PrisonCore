package twizzy.tech.util

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.item.ItemStack
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import twizzy.tech.serverInstance
import java.util.ArrayDeque

class Uniview {
    companion object {
        // Menu stack management moved to a separate inner class
        private object MenuStackManager {
            val stacks = java.util.concurrent.ConcurrentHashMap<Player, ArrayDeque<Interface>>()
            fun getStack(player: Player) = stacks.computeIfAbsent(player) { ArrayDeque() }
            fun clear(player: Player) = stacks.remove(player)
            fun forEach(action: (Player, ArrayDeque<Interface>) -> Unit) = stacks.forEach(action)
            fun get(player: Player): ArrayDeque<Interface>? = stacks[player]
        }

        init {
            MinecraftServer.getGlobalEventHandler().addSuspendingListener(serverInstance, InventoryCloseEvent::class.java) { event ->
                if (!event.isFromClient) return@addSuspendingListener
                val player = event.player
                val stack = MenuStackManager.get(player) ?: return@addSuspendingListener
                if (stack.size > 1) {
                    stack.removeLast()
                    val parentMenu = stack.last()
                    player.scheduler().buildTask {
                        parentMenu.show(player, false)
                    }.delay(TaskSchedule.tick(1)).schedule()
                } else {
                    MenuStackManager.clear(player)
                }
            }
            MinecraftServer.getGlobalEventHandler().addSuspendingListener(serverInstance,PlayerDisconnectEvent::class.java) { event ->
                MenuStackManager.clear(event.player)
            }
        }

        /**
         * Create a new interface
         */
        fun createInterface(name: String, rows: Int): Interface {
            return Interface(name, rows)
        }

        /**
         * Clear the menu stack for a player
         */
        fun clearMenuStack(player: Player) {
            MenuStackManager.clear(player)
        }

        /**
         * Get the current menu stack for a player
         */
        internal fun getMenuStack(player: Player): ArrayDeque<Interface> {
            return MenuStackManager.getStack(player)
        }

        /**
         * Replace the current menu in the stack without changing navigation hierarchy
         * This is useful for refreshing a menu with updated content
         */
        fun replaceCurrentMenu(player: Player, newMenu: Interface) {
            val stack = getMenuStack(player)
            if (stack.isNotEmpty()) {
                val parentMenu = if (stack.size > 1) stack.elementAt(stack.size - 2) else null
                stack.removeLast() // Remove current menu
                newMenu.parentInterface = parentMenu
                stack.addLast(newMenu) // Add the new menu in its place
                newMenu.show(player, false) // Show the new menu without adding to stack again
            } else {
                // If no stack exists, just show normally
                stack.addLast(newMenu)
                newMenu.show(player, false)
            }
        }
    }

    /**
     * Main Interface class that handles GUI creation
     */
    class Interface(private val name: String, private val rows: Int) {
        private var title: String = ""
        val items = mutableMapOf<Int, GuiItem>()
        internal var parentInterface: Interface? = null

        // Animation system optimization
        private inner class AnimationManager {
            private val animations = mutableMapOf<Int, Animation>()
            private var task: Task? = null
            private val activeViewers = mutableSetOf<Player>()

            fun addViewer(player: Player) {
                activeViewers.add(player)
                if (task == null && animations.isNotEmpty()) {
                    startTask()
                }
            }

            fun removeViewer(player: Player) {
                activeViewers.remove(player)
                if (activeViewers.isEmpty()) {
                    task?.cancel()
                    task = null
                }
            }

            private fun startTask() {
                task = MinecraftServer.getSchedulerManager()
                    .buildTask {
                        if (activeViewers.isEmpty()) return@buildTask
                        animations.forEach { (slot, animation) ->
                            // Optimized frame handling
                            val currentItem = items[slot]?.item ?: return@forEach
                            val currentIndex = animation.frames.indexOfFirst { it.material() == currentItem.material() }
                            val nextIndex = if (currentIndex < animation.frames.size - 1) currentIndex + 1 else 0
                            if (nextIndex == 0 && !animation.loop) {
                                animations.remove(slot)
                                animation.onFinish?.invoke()
                                return@forEach
                            }
                            val newItem = animation.frames[nextIndex]
                            val existingGuiItem = items[slot]
                            if (existingGuiItem != null) {
                                items[slot] = existingGuiItem.copy(item = newItem)
                            }
                            MenuStackManager.stacks.forEach { (player, stack) ->
                                if (stack.lastOrNull() == this) {
                                    player.openInventory?.setItemStack(slot, newItem)
                                }
                            }
                        }
                        if (animations.isEmpty()) {
                            task?.cancel()
                            task = null
                        }
                    }
                    .repeat(TaskSchedule.tick(1))
                    .schedule()
            }
        }

        private val animatedSlots = mutableMapOf<Int, Animation>()
        private var animationTask: Task? = null

        data class Animation(
            val frames: List<ItemStack>,
            val frameDuration: TaskSchedule,
            val loop: Boolean = true,
            val onFinish: (() -> Unit)? = null
        )

        data class GuiItem(
            val item: ItemStack,
            val data: Any? = null,
            val onClick: ((player: Player, slot: Int, clickType: Any?, data: Any?) -> Unit)? = null
        )

        fun setTitle(title: String): Interface {
            this.title = title
            return this
        }

        fun addItem(row: Int, slot: Int, item: ItemStack, data: Any? = null, onClick: ((player: Player, slot: Int, clickType: Any?, data: Any?) -> Unit)? = null): Interface {
            require(row in 1..6) { "Row must be between 1 and 6" }
            require(slot in 1..9) { "Slot must be between 1 and 9" }
            require(!item.isAir) { "Item cannot be air" }
            val slotIndex = (row - 1) * 9 + (slot - 1)
            items[slotIndex] = GuiItem(item, data, onClick)
            return this
        }

        /**
         * Add a sub-menu button that opens another interface
         */
        fun addSubMenu(row: Int, slot: Int, item: ItemStack, subMenu: Interface, data: Any? = null): Interface {
            val slotIndex = (row - 1) * 9 + (slot - 1)
            // Set this interface as the parent of the sub-menu
            subMenu.parentInterface = this
            items[slotIndex] = GuiItem(item, data) { player, _, _, _ ->
                subMenu.show(player)
            }
            return this
        }

        /**
         * Add a back button that returns to the parent interface
         * @param row The row position (1-based)
         * @param slot The slot position (1-based)
         * @param customItem Optional custom item for the back button. If null, uses default barrier item
         */
        fun returnButton(row: Int, slot: Int, customItem: ItemStack? = null): Interface {
            val backItem = customItem ?: ItemStack.builder(net.minestom.server.item.Material.BARRIER)
                .customName(Component.text("§c§l« Back"))
                .lore(listOf(Component.text("§7Click to go back to the previous menu")))
                .build()

            val slotIndex = (row - 1) * 9 + (slot - 1)
            items[slotIndex] = GuiItem(backItem) { player, _, _, _ ->
                parentInterface?.let { parent ->
                    // Remove current menu from stack and show parent
                    val stack = getMenuStack(player)
                    if (stack.isNotEmpty()) {
                        stack.removeLast() // Remove current menu
                    }
                    parent.show(player, false) // Don't add to stack again since it's already there
                }
            }
            return this
        }

        fun addAnimatedItem(
            row: Int,
            slot: Int,
            frames: List<ItemStack>,
            frameDuration: TaskSchedule,
            loop: Boolean = true,
            onClick: ((player: Player, slot: Int, clickType: Any?, data: Any?) -> Unit)? = null
        ): Interface {
            require(frames.isNotEmpty()) { "Frames list cannot be empty" }
            require(frames.all { !it.isAir }) { "Animation frames cannot be air" }
            val slotIndex = (row - 1) * 9 + (slot - 1)
            animatedSlots[slotIndex] = Animation(frames, frameDuration, loop)
            items[slotIndex] = GuiItem(frames.first(), onClick = onClick)
            return this
        }

        // Resource cleanup
        fun cleanup() {
            stopAnimations()
            MenuStackManager.stacks.values.forEach { stack ->
                stack.removeIf { it == this }
            }
        }

        // Refresh system enhancement
        private inner class RefreshManager {
            private val version = java.util.concurrent.atomic.AtomicInteger(0)

            fun refresh(player: Player? = null) {
                version.incrementAndGet()
                if (player != null) {
                    refreshForPlayer(player)
                } else {
                    MenuStackManager.stacks.keys.forEach { refreshForPlayer(it) }
                }
            }

            private fun refreshForPlayer(player: Player) {
                if (MenuStackManager.get(player)?.lastOrNull() == this@Interface) {
                    player.scheduler().buildTask {
                        partialRefresh(player)
                    }.delay(TaskSchedule.tick(1)).schedule()
                }
            }

            private fun partialRefresh(player: Player) {
                player.openInventory?.let { inv ->
                    items.forEach { (slot, item) ->
                        if (needsRefresh(slot)) { // Implement change detection
                            inv.setItemStack(slot, item.item)
                        }
                    }
                }
            }

            private fun needsRefresh(slot: Int): Boolean {
                // Placeholder for change detection logic
                // Always returns true for now
                return true
            }
        }

        private val refreshManager = RefreshManager()

        /**
         * Refresh the current menu for all viewing players
         */
        fun refresh() {
            MenuStackManager.forEach { player, stack ->
                if (stack.lastOrNull() == this) {
                    player.scheduler().buildTask {
                        show(player, false, true) // Refresh without adding to stack
                    }.delay(TaskSchedule.tick(1)).schedule()
                }
            }
        }

        /**
         * Refresh the current menu for a specific player
         */
        fun refreshFor(player: Player) {
            val stack = MenuStackManager.get(player) ?: return
            if (stack.lastOrNull() == this) {
                player.scheduler().buildTask {
                    show(player, false, true) // Refresh without adding to stack
                }.delay(TaskSchedule.tick(1)).schedule()
            }
        }

        /**
         * Start animations for this interface
         */
        fun startAnimations() {
            stopAnimations()
            animationTask = MinecraftServer.getSchedulerManager()
                .buildTask {
                    animatedSlots.forEach { (slot, animation) ->
                        val currentFrame = items[slot]?.item ?: return@forEach
                        val currentIndex = animation.frames.indexOfFirst { it == currentFrame }
                        val nextIndex = if (currentIndex < animation.frames.size - 1) currentIndex + 1 else 0
                        if (nextIndex == 0 && !animation.loop) {
                            animatedSlots.remove(slot)
                            animation.onFinish?.invoke()
                            return@forEach
                        }
                        val newItem = animation.frames[nextIndex]
                        val existingGuiItem = items[slot]
                        if (existingGuiItem != null) {
                            items[slot] = existingGuiItem.copy(item = newItem)
                        }
                        MenuStackManager.stacks.forEach { (player, stack) ->
                            if (stack.lastOrNull() == this) {
                                player.openInventory?.setItemStack(slot, newItem)
                            }
                        }
                    }
                    if (animatedSlots.isEmpty()) {
                        animationTask?.cancel()
                        animationTask = null
                    }
                }
                .repeat(TaskSchedule.tick(1))
                .schedule()
        }

        /**
         * Stop all animations for this interface
         */
        fun stopAnimations() {
            animationTask?.cancel()
            animationTask = null
        }

        /**
         * Show this interface to a player
         * @param addToStack whether to add this menu to the navigation stack (default: true)
         */
        fun show(player: Player, addToStack: Boolean = true, isRefresh: Boolean = false) {
            if (!isRefresh) {
                player.closeInventory()
            }
            val inventory = Inventory(getInventoryType(rows), Component.text(title))
            items.forEach { (slot, guiItem) ->
                inventory.setItemStack(slot, guiItem.item)
            }
            inventory.addInventoryCondition { player, slot, clickType, result ->
                result.isCancel = true
                val guiItem = items[slot] ?: return@addInventoryCondition
                inventory.setItemStack(slot, guiItem.item)
                guiItem.onClick?.invoke(player, slot, clickType, guiItem.data)
                val newGuiItem = items[slot]
                if (newGuiItem != null) {
                    inventory.setItemStack(slot, newGuiItem.item)
                }
            }
            if (addToStack) {
                val stack = getMenuStack(player)
                stack.addLast(this)
            }
            player.openInventory(inventory)
            // Start animations when shown
            if (animatedSlots.isNotEmpty()) {
                startAnimations()
            }
        }

        /**
         * Show this interface to a player (original method for backward compatibility)
         */
        fun show(player: Player) {
            show(player, true)
        }

        /**
         * Create a sub-interface that will be linked to this parent
         */
        fun createSubInterface(name: String, rows: Int): Interface {
            return Interface(name, rows)
        }

        /**
         * Get the appropriate InventoryType for the number of rows
         */
        private fun getInventoryType(rows: Int): InventoryType {
            return when (rows) {
                1 -> InventoryType.CHEST_1_ROW
                2 -> InventoryType.CHEST_2_ROW
                3 -> InventoryType.CHEST_3_ROW
                4 -> InventoryType.CHEST_4_ROW
                5 -> InventoryType.CHEST_5_ROW
                6 -> InventoryType.CHEST_6_ROW
                else -> throw IllegalArgumentException("Invalid number of rows: $rows. Must be between 1 and 6.")
            }
        }
    }
}
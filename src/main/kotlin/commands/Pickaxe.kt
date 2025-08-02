package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import twizzy.tech.game.items.pickaxe.Booster
import twizzy.tech.game.items.pickaxe.Enchant
import twizzy.tech.game.items.pickaxe.Pickaxe
import twizzy.tech.player.PlayerData
import twizzy.tech.util.Uniview
import twizzy.tech.util.YamlFactory
import java.math.BigDecimal
import java.util.*

@Command("pickaxe")
class Pickaxe {
    private val menuBuilder = MenuBuilder()
    private val materialCache = mutableMapOf<String, Material>()

    @Command("pickaxe")
    suspend fun openInterface(actor: Player) {
        Uniview.clearMenuStack(actor)
        val pickaxe = Pickaxe.fromItemStack(actor.itemInMainHand)
        menuBuilder.createMainMenu(pickaxe).show(actor)
    }

    @Subcommand("get")
    fun getPickaxe(actor: Player) {
        val pickaxe = Pickaxe(
            blocksMined = 0L,
            level = 1,
            experience = 0,
            owner = actor.uuid.toString()
        )
        actor.inventory.addItemStack(pickaxe.toItemStack())
        actor.sendMessage("§aYou have been given a pickaxe!")
    }

    private inner class MenuBuilder {
        private val enchantCache = mutableMapOf<Boolean, List<Enchant>>()

        suspend fun createMainMenu(pickaxe: Pickaxe?): Uniview.Interface {
            return Uniview.createInterface("pickaxe_main", 3).apply {
                setTitle("Pickaxe Info")
                addSubMenu(2, 3, createBoosterButton(pickaxe), createBoostersMenu(pickaxe))
                addItem(2, 5, createLevelInfoButton(pickaxe)) { _, _, _, _ -> }
                addSubMenu(2, 7, createEnchantInfoButton(pickaxe), createEnchantsMenu(pickaxe))
            }
        }

        fun createBoostersMenu(pickaxe: Pickaxe?): Uniview.Interface {
            return Uniview.createInterface("boosters", 6).apply {
                setTitle("Pickaxe Boosters")
                returnButton(6, 1)

                // Initialize the booster registry
                Booster.Companion.BoosterRegistry.initialize()

                // Active Boosters Section
                addItem(1, 4, createSectionHeaderItem("§d§lActive Boosters")) { _, _, _, _ -> }
                addItem(1, 6, createViewAllBoostersButton()) { player, _, _, _ ->
                    createAllBoostersMenu(pickaxe).show(player)
                }

                val activeBoosters = pickaxe?.activeBoosters() ?: emptyList()
                if (activeBoosters.isNotEmpty()) {
                    activeBoosters.take(18).forEachIndexed { i, booster ->
                        val row = 2 + (i / 9)
                        val slot = 1 + (i % 9)
                        addItem(row, slot, createBoosterItem(booster)) { player, _, _, _ ->
                            val detailMenu = createBoosterDetailMenu(booster)
                            detailMenu.parentInterface = this@apply
                            detailMenu.show(player)
                        }
                    }
                } else {
                    addItem(3, 5, createNoBoostersItem()) { _, _, _, _ -> }
                }

                // Available Boosters Section (if we want to show them for reference)
                addItem(4, 4, createSectionHeaderItem("§a§lAvailable Booster Types")) { _, _, _, _ -> }
                val availableTypes = Booster.Companion.BoosterRegistry.getAllBoosterInfo().take(9)
                availableTypes.forEachIndexed { i, boosterInfo ->
                    val slot = 1 + i
                    addItem(5, slot, createAvailableBoosterItem(boosterInfo)) { _, _, _, _ -> }
                }
            }
        }

        /**
         * Create a menu showing all available booster types
         */
        fun createAllBoostersMenu(pickaxe: Pickaxe?): Uniview.Interface {
            return Uniview.createInterface("all_boosters", 4).apply {
                setTitle("All Booster Types")
                returnButton(4, 1)

                val allBoosterTypes = Booster.Companion.BoosterRegistry.getAllBoosterInfo()
                if (allBoosterTypes.isNotEmpty()) {
                    allBoosterTypes.forEachIndexed { i, boosterInfo ->
                        val row = 1 + (i / 9)
                        val slot = 1 + (i % 9)
                        addItem(row, slot, createAvailableBoosterItem(boosterInfo)) { _, _, _, _ -> }
                    }
                } else {
                    addItem(2, 5, createNoBoosterTypesItem()) { _, _, _, _ -> }
                }
            }
        }

        fun createEnchantsMenu(pickaxe: Pickaxe?): Uniview.Interface {
            return Uniview.createInterface("enchants", 3).apply {
                setTitle("Pickaxe Enchants")
                returnButton(3, 1)
                addSubMenu(2, 4, createTokenButton(), createEnchantListMenu(pickaxe, true))
                addSubMenu(2, 6, createSoulButton(), createEnchantListMenu(pickaxe, false))
            }
        }

        fun createEnchantListMenu(pickaxe: Pickaxe?, isToken: Boolean): Uniview.Interface {
            return Uniview.createInterface("${if (isToken) "token" else "soul"}_enchants", 4).apply {
                setTitle("${if (isToken) "Token" else "Soul"} Enchants")
                returnButton(4, 1)

                val enchants = getSortedEnchants(pickaxe, isToken)
                if (enchants.isEmpty()) {
                    addItem(2, 5, createNoEnchantsItem(isToken)) { _, _, _, _ -> }
                } else {
                    enchants.forEachIndexed { i, enchantInfo ->
                        val row = 1 + (i / 9)
                        val slot = 1 + (i % 9)
                        addItem(row, slot, createEnchantListItem(enchantInfo)) { player, _, clickType, _ ->
                            if (enchantInfo.isLocked) return@addItem

                            if (clickType.toString().contains("SHIFT") && enchantInfo.isOwned) {
                                // Toggle enchant enabled/disabled state
                                handleEnchantToggle(player, enchantInfo.enchant)
                            } else {
                                val detailMenu = createEnchantDetailMenu(enchantInfo, player)
                                // Set the current menu as the parent of the detail menu
                                detailMenu.parentInterface = this@apply
                                detailMenu.show(player)
                            }
                        }
                    }
                }
            }
        }

        fun createEnchantDetailMenu(
            enchantInfo: EnchantInfo,
            player: Player? = null
        ): Uniview.Interface {
            return Uniview.createInterface("enchant_detail", 3).apply {
                setTitle("Enchant Details")
                returnButton(3, 1)

                val canUpgrade = enchantInfo.isOwned && enchantInfo.enchant.canUpgrade()
                val canPurchase = !enchantInfo.isOwned && enchantInfo.canApply

                // Main enchant display
                addItem(1, 5, createEnchantDetailItem(enchantInfo)) { _, _, _, _ -> }

                if (canUpgrade || canPurchase) {
                    // Upgrade controls
                    addUpgradeControls(this, enchantInfo, player)
                } else if (enchantInfo.isOwned) {
                    addItem(2, 5, createMaxLevelItem(enchantInfo.enchant)) { _, _, _, _ -> }
                } else {
                    addItem(2, 5, createLockedItem(enchantInfo.enchant)) { _, _, _, _ -> }
                }

                // Enchant removal option (new feature)
                if (enchantInfo.isOwned) {
                    addItem(3, 8, createRemoveEnchantItem()) { player, _, _, _ ->
                        handleEnchantRemoval(player, enchantInfo.enchant)
                    }
                }
            }
        }

        private fun addUpgradeControls(
            menu: Uniview.Interface,
            enchantInfo: EnchantInfo,
            player: Player?
        ) {
            val amounts = listOf(1, 5, 10)

            // Decrease buttons (reversed order: -10, -5, -1)
            amounts.reversed().forEachIndexed { i, amount ->
                menu.addItem(2, 1 + i, createDecreaseButton(amount)) { p, _, _, _ ->
                    val newEnchantInfo = enchantInfo.withUpgradeAmount(enchantInfo.upgradeAmount - amount)
                    updateUpgradeButton(p, menu, newEnchantInfo, player)
                }
            }

            // Upgrade button
            menu.addItem(2, 5, createUpgradeButton(enchantInfo, player)) { p, _, _, _ ->
                handleUpgrade(p, enchantInfo)
            }

            // Increase buttons (normal order: +1, +5, +10)
            amounts.forEachIndexed { i, amount ->
                menu.addItem(2, 7 + i, createIncreaseButton(amount)) { p, _, _, _ ->
                    val newEnchantInfo = enchantInfo.withUpgradeAmount(enchantInfo.upgradeAmount + amount)
                    updateUpgradeButton(p, menu, newEnchantInfo, player)
                }
            }
        }

        private fun updateUpgradeButton(player: Player, menu: Uniview.Interface, newEnchantInfo: EnchantInfo, playerRef: Player?) {
            // Update the upgrade button item in the menu's items map
            val upgradeSlot = 1 * 9 + 4 // Row 2, Slot 5 (0-indexed)
            val newUpgradeButton = createUpgradeButton(newEnchantInfo, playerRef)

            // Update the menu's internal item map
            val existingGuiItem = menu.items[upgradeSlot]
            if (existingGuiItem != null) {
                menu.items[upgradeSlot] = existingGuiItem.copy(
                    item = newUpgradeButton,
                    onClick = { p, _, _, _ ->
                        handleUpgrade(p, newEnchantInfo) // Use newEnchantInfo instead of old enchantInfo
                    }
                )
            }

            // Update the player's open inventory directly
            player.openInventory?.setItemStack(upgradeSlot, newUpgradeButton)

            // Update the click handlers for the glass pane buttons to use the new enchant info
            updateGlassPaneButtons(menu, newEnchantInfo, playerRef)
        }

        private fun updateGlassPaneButtons(menu: Uniview.Interface, enchantInfo: EnchantInfo, player: Player?) {
            val amounts = listOf(1, 5, 10)

            // Update decrease buttons
            amounts.forEachIndexed { i, amount ->
                val slot = 1 * 9 + i // Row 2, slots 1-3 (0-indexed)
                val existingGuiItem = menu.items[slot]
                if (existingGuiItem != null) {
                    menu.items[slot] = existingGuiItem.copy(
                        onClick = { p, _, _, _ ->
                            val newEnchantInfo = enchantInfo.withUpgradeAmount(enchantInfo.upgradeAmount - amount)
                            updateUpgradeButton(p, menu, newEnchantInfo, player)
                        }
                    )
                }
            }

            // Update increase buttons
            amounts.forEachIndexed { i, amount ->
                val slot = 1 * 9 + (6 + i) // Row 2, slots 7-9 (0-indexed)
                val existingGuiItem = menu.items[slot]
                if (existingGuiItem != null) {
                    menu.items[slot] = existingGuiItem.copy(
                        onClick = { p, _, _, _ ->
                            val newEnchantInfo = enchantInfo.withUpgradeAmount(enchantInfo.upgradeAmount + amount)
                            updateUpgradeButton(p, menu, newEnchantInfo, player)
                        }
                    )
                }
            }
        }

        fun createBoosterDetailMenu(booster: Booster): Uniview.Interface {
            return Uniview.createInterface("booster_detail", 3).apply {
                setTitle("Booster Details")
                returnButton(3, 1)
                addItem(2, 5, createBoosterDetailItem(booster)) { _, _, _, _ -> }
            }
        }

        // Helper methods for item creation
        private fun createBoosterButton(pickaxe: Pickaxe?) = ItemStack.builder(Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE)
            .customName(Component.text("§d§lBoosters"))
            .hideExtraTooltip()
            .lore(listOf(
                Component.text("§6Click to view boosters"),
                Component.text("§7Active: §a${pickaxe?.activeBoosters()?.size ?: 0}")
            )).build()

        private suspend fun createLevelInfoButton(pickaxe: Pickaxe?) = ItemStack.builder(Material.EXPERIENCE_BOTTLE)
            .customName(Component.text("§b§lPickaxe Info"))
            .lore(listOf(
                Component.text("§7Level: §a${pickaxe?.level ?: 1}"),
                Component.text("§7EXP: §e${pickaxe?.experience ?: 0}§7/§e${pickaxe?.expToNext() ?: 100}"),
                Component.text("§7Blocks Mined: §a${pickaxe?.blocksMined ?: 0}")
            )).build()

        private fun createEnchantInfoButton(pickaxe: Pickaxe?) = ItemStack.builder(Material.ENCHANTED_BOOK)
            .customName(Component.text("§b§lEnchants"))
            .lore(listOf(
                Component.text("§dClick to view enchants"),
                Component.text("§7Total: §a${pickaxe?.enchants?.size ?: 0}")
            )).build()

        private fun createNoBoostersItem() = ItemStack.builder(Material.BARRIER)
            .customName(Component.text("§c§lNo Active Boosters"))
            .lore(listOf(Component.text("§7You don't have any active boosters.")))
            .build()

        private fun createBoosterItem(booster: Booster) = ItemStack.builder(Material.POTION)
            .customName(Component.text("§d§l${booster.javaClass.simpleName}"))
            .lore(listOf(
                Component.text("§7${booster.getDisplayString()}"),
                Component.text("§8Click for more details")
            )).build()

        private fun createTokenButton() = ItemStack.builder(Material.EMERALD)
            .customName(Component.text("§a§lToken Enchants"))
            .lore(listOf(Component.text("§7Click to view token enchants")))
            .build()

        private fun createSoulButton() = ItemStack.builder(Material.AMETHYST_SHARD)
            .customName(Component.text("§d§lSoul Enchants"))
            .lore(listOf(Component.text("§7Click to view soul enchants")))
            .build()

        private fun createNoEnchantsItem(isToken: Boolean) = ItemStack.builder(Material.BARRIER)
            .customName(Component.text("§c§lNo ${if (isToken) "Token" else "Soul"} Enchants"))
            .lore(listOf(Component.text("§7No enchants available in the system.")))
            .build()

        private fun createEnchantListItem(info: EnchantInfo): ItemStack {
            val (enchant, isOwned, canApply) = info
            val nameColor = if (isOwned) "§b§l" else if (canApply) "§a§l" else "§7§l"
            val status = when {
                isOwned -> "§7[Level ${enchant.level}]"
                canApply -> "§7[Unlocked]"
                else -> "§7[Locked]"
            }

            // Create lore with shift-click instruction for owned enchants
            val lore = createEnchantLore(enchant, isOwned, canApply, false).toMutableList()
            if (isOwned) {
                lore.add(Component.text("§8Shift-click to toggle enabled/disabled"))
            }

            return ItemStack.builder(resolveMaterial(enchant.getDisplayItem()))
                .customName(Component.text("$nameColor${enchant.name} $status"))
                .hideExtraTooltip()
                .lore(lore)
                .glowing(isOwned && enchant.isEnabled()) // Only glow if owned AND enabled
                .build()
        }

        private fun createEnchantDetailItem(info: EnchantInfo): ItemStack {
            val (enchant, isOwned, canApply) = info
            return ItemStack.builder(resolveMaterial(enchant.getDisplayItem()))
                .customName(Component.text("§b§l${enchant.name}"))
                .hideExtraTooltip()
                .lore(createEnchantLore(enchant, isOwned, canApply, true))
                .glowing(isOwned && enchant.isEnabled()) // Only glow if owned AND enabled
                .build()
        }

        private fun createEnchantLore(
            enchant: Enchant,
            isOwned: Boolean,
            canApply: Boolean,
            showStatus: Boolean
        ): List<Component> {
            val lore = mutableListOf<Component>()
            val currency = if (enchant.isTokenEnchant()) "Tokens" else "Souls"

            if (showStatus) {
                lore.add(when {
                    isOwned -> Component.text("§a✓ OWNED")
                    canApply -> Component.text("§e✓ UNLOCKED")
                    else -> Component.text("§c✗ LOCKED")
                })
            }

            lore.add(Component.text("§7${enchant.getDisplayString()}"))
            lore.add(Component.text("§7Type: §e${enchant.getEnchantType()}"))
            lore.add(Component.text("§7Currency: §e$currency"))
            lore.add(Component.text("§7Required Level: §e${enchant.getRequiredPickaxeLevel()}"))

            if (enchant.getEnchantType() != "Effect") {
                lore.add(Component.text("§7Activation: §e${"%.2f".format(enchant.getActivationChance())}%"))
            }

            when {
                isOwned && enchant.canUpgrade() ->
                    lore.add(Component.text("§7Upgrade: §a${enchant.getUpgradePrice()} $currency"))
                isOwned ->
                    lore.add(Component.text("§c§lMAX LEVEL"))
                canApply ->
                    lore.add(Component.text("§7Purchase: §a${enchant.getBasePrice()} $currency"))
                else ->
                    lore.add(Component.text("§c§lRequires Level ${enchant.getRequiredPickaxeLevel()}"))
            }

            if (canApply) lore.add(Component.text("§8Click for details"))
            return lore
        }

        private fun createUpgradeButton(info: EnchantInfo, player: Player?) =
            createActionButton(
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                if (info.isOwned) "§a§lUpgrade" else "§a§lPurchase",
                listOf(
                    "§7Amount: §e${info.upgradeAmount}",
                    "§7Total: §a${calculateTotalCost(info)}",
                    if (player != null) "§7Balance: §e${getPlayerBalance(player, info.enchant)}" else ""
                ).filter { it.isNotBlank() },
                info.upgradeAmount
            )

        private fun createRemoveEnchantItem() = ItemStack.builder(Material.FIRE_CHARGE)
            .customName(Component.text("§4§lRemove Enchant"))
            .lore(listOf(Component.text("§7Permanently remove this enchant")))
            .build()

        // Additional helper methods
        private fun getSortedEnchants(pickaxe: Pickaxe?, isToken: Boolean): List<EnchantInfo> {
            val enchants = getCachedEnchants(isToken)
            val playerEnchants = pickaxe?.enchants ?: emptyList()
            val pickaxeLevel = pickaxe?.level ?: 1

            return enchants.map { enchant ->
                val owned = playerEnchants.find { it.name.equals(enchant.name, true) }
                val isOwned = owned != null
                val displayEnchant = owned ?: enchant
                val canApply = enchant.canApplyToPickaxe(pickaxeLevel)

                EnchantInfo(
                    enchant = displayEnchant,
                    isOwned = isOwned,
                    canApply = canApply,
                    isLocked = !canApply && !isOwned
                )
            }.sortedWith(compareBy(
                { it.isLocked },  // Locked last
                { -it.enchant.level }, // Higher levels first
                { it.enchant.name }    // Alphabetical
            ))
        }

        private fun getCachedEnchants(isToken: Boolean): List<Enchant> {
            return enchantCache.getOrPut(isToken) {
                Pickaxe.Companion.EnchantRegistry.initialize()
                Pickaxe.Companion.EnchantRegistry.getAllRegisteredTypes()
                    .mapNotNull { type ->
                        Pickaxe.Companion.EnchantRegistry.createEnchant(type, 1)?.takeIf {
                            isToken == it.isTokenEnchant()
                        }
                    }
            }
        }

        private fun resolveMaterial(displayItem: String): Material {
            return materialCache.getOrPut(displayItem) {
                when {
                    displayItem.contains(":") -> Material.fromKey(displayItem.lowercase()) ?: Material.BOOK
                    else -> Material.fromKey(displayItem.uppercase())
                        ?: Material.fromKey("minecraft:${displayItem.lowercase()}")
                        ?: Material.BOOK
                }
            }
        }

        private fun calculateTotalCost(info: EnchantInfo): BigDecimal {
            return if (info.isOwned) {
                // For upgrades: multiply upgrade price by the number of levels being upgraded
                info.enchant.getUpgradePrice().multiply(BigDecimal(info.upgradeAmount))
            } else {
                // For new enchants: calculate total cost for purchasing multiple levels
                var totalCost = BigDecimal.ZERO
                for (level in 1..info.upgradeAmount) {
                    // Create a temporary enchant at each level to get the correct price
                    val tempEnchant = Pickaxe.Companion.EnchantRegistry.createEnchant(
                        Pickaxe.Companion.EnchantRegistry.getTypeName(info.enchant),
                        level
                    )
                    if (tempEnchant != null) {
                        if (level == 1) {
                            totalCost = totalCost.add(tempEnchant.getBasePrice())
                        } else {
                            totalCost = totalCost.add(tempEnchant.getUpgradePrice())
                        }
                    }
                }
                totalCost
            }
        }

        private fun getPlayerBalance(player: Player, enchant: Enchant): String {
            val playerData = PlayerData.getFromCache(player.uuid) ?: return "0"
            val balance = if (enchant.isTokenEnchant()) playerData.tokens else playerData.souls
            return twizzy.tech.util.CompactNotation.format(balance)
        }

        private fun handleUpgrade(player: Player, info: EnchantInfo) {
            // Get the current pickaxe from the player's main hand
            val currentPickaxe = Pickaxe.fromItemStack(player.itemInMainHand) ?: run {
                player.sendMessage("§cYou must be holding a pickaxe!")
                return
            }

            // Get player data for currency checking
            val playerData = PlayerData.getFromCache(player.uuid) ?: run {
                player.sendMessage("§cCould not load your data!")
                return
            }

            // Check if player has enough currency
            val totalCost = calculateTotalCost(info)
            val currency = if (info.enchant.isTokenEnchant()) "Tokens" else "Souls"
            val playerBalance = if (info.enchant.isTokenEnchant()) playerData.tokens else playerData.souls

            if (playerBalance < totalCost) {
                val message = YamlFactory.getMessage("commands.pickaxe.enchants.insufficient_funds", mapOf(
                    "currency" to currency,
                    "amount" to totalCost.toString()
                ))
                player.sendMessage(Component.text(message))
                return
            }

            val updatedPickaxe = if (info.isOwned) {
                // Upgrade existing enchant
                val existingEnchant = currentPickaxe.getEnchantByName(info.enchant.name)
                if (existingEnchant == null) {
                    player.sendMessage("§cEnchant not found on pickaxe!")
                    return
                }

                val newLevel = (existingEnchant.level + info.upgradeAmount).coerceAtMost(existingEnchant.getMaxLevel())
                val updatedEnchant = Pickaxe.Companion.EnchantRegistry.createEnchant(
                    Pickaxe.Companion.EnchantRegistry.getTypeName(existingEnchant),
                    newLevel
                ) ?: run {
                    player.sendMessage("§cFailed to upgrade enchant!")
                    return
                }

                // Remove old enchant and add upgraded one
                val filteredEnchants = currentPickaxe.enchants.filter { !it.name.equals(info.enchant.name, true) }
                currentPickaxe.copy(enchants = filteredEnchants + updatedEnchant)
            } else {
                // Add new enchant
                val newEnchant = Pickaxe.Companion.EnchantRegistry.createEnchant(
                    Pickaxe.Companion.EnchantRegistry.getTypeName(info.enchant),
                    info.upgradeAmount
                ) ?: run {
                    player.sendMessage("§cFailed to create enchant!")
                    return
                }

                currentPickaxe.addEnchant(newEnchant)
            }

            // Deduct currency from player balance
            if (info.enchant.isTokenEnchant()) {
                playerData.tokens = playerData.tokens.subtract(totalCost)
            } else {
                playerData.souls = playerData.souls.subtract(totalCost)
            }

            // Update the player's item
            player.setItemInMainHand(updatedPickaxe.toItemStack())

            // Send success message using language file
            val messageKey = if (info.isOwned) "commands.pickaxe.enchants.upgraded" else "commands.pickaxe.enchants.added"
            val finalLevel = if (info.isOwned) info.enchant.level + info.upgradeAmount else info.upgradeAmount

            val message = YamlFactory.getMessage(messageKey, mapOf(
                "enchant" to info.enchant.name,
                "level" to finalLevel.toString(),
                "amount" to totalCost.toString(),
                "currency" to currency
            ))
            player.sendMessage(Component.text(message))

            // Close the menu and refresh
            player.closeInventory()
        }

        private fun handleEnchantRemoval(player: Player, enchant: Enchant) {
            // New enchant removal logic
            player.sendMessage("§aRemoved ${enchant.name} from your pickaxe!")
            Uniview.getMenuStack(player).firstOrNull()?.refreshFor(player)
            player.closeInventory()
        }

        private fun handleEnchantToggle(player: Player, enchant: Enchant) {
            // Get the current pickaxe from the player's main hand
            val currentPickaxe = Pickaxe.fromItemStack(player.itemInMainHand) ?: run {
                player.sendMessage("§cYou must be holding a pickaxe!")
                return
            }

            // Find the actual enchant on the pickaxe (not the template)
            val actualEnchant = currentPickaxe.getEnchantByName(enchant.name)
            if (actualEnchant == null) {
                player.sendMessage("§cEnchant '${enchant.name}' not found on pickaxe!")
                player.sendMessage("§7Available enchants: ${currentPickaxe.enchants.joinToString(", ") { it.name }}")
                return
            }

            // Toggle the enchant's enabled state
            val updatedPickaxe = currentPickaxe.toggleEnchant(actualEnchant.name)

            // The toggle is successful if we get a different instance
            // (toggleEnchant returns the same instance only if enchant not found, which we already checked)

            // Update the player's item
            player.setItemInMainHand(updatedPickaxe.toItemStack())

            // Get the updated enchant to check its new state
            val toggledEnchant = updatedPickaxe.getEnchantByName(actualEnchant.name)
            val statusMessage = if (toggledEnchant?.isEnabled() == true) {
                "§aEnabled ${actualEnchant.name}!"
            } else {
                "§cDisabled ${actualEnchant.name}!"
            }

            player.sendMessage(statusMessage)

            // Update the specific menu item with the new glowing state immediately
            updateEnchantMenuItemVisual(player, updatedPickaxe, toggledEnchant!!)

            // Refresh the current menu to show updated glowing state
            val currentMenu = Uniview.getMenuStack(player).lastOrNull()
            currentMenu?.refreshFor(player)
        }

        /**
         * Updates a specific enchant menu item to reflect the new enabled/disabled state immediately
         */
        private fun updateEnchantMenuItemVisual(player: Player, updatedPickaxe: Pickaxe, toggledEnchant: Enchant) {
            val openInventory = player.openInventory ?: return
            val currentMenu = Uniview.getMenuStack(player).lastOrNull() ?: return

            // Find the slot containing this enchant by searching for its name in the item display name
            for (slot in 0 until openInventory.size) {
                val item = openInventory.getItemStack(slot)
                if (item != null && item.material() != Material.AIR) {
                    // Get the display name properly from the component
                    val customNameComponent = item.get(ItemComponent.CUSTOM_NAME)
                    val displayName = if (customNameComponent != null) {
                        // Extract the content from the Component - remove color codes for comparison
                        customNameComponent.toString().replace(Regex("§[0-9a-fklmnor]"), "")
                    } else {
                        ""
                    }

                    // Check if this item represents our toggled enchant
                    if (displayName.contains(toggledEnchant.name, ignoreCase = true)) {
                        // Get the enchant info for this enchant
                        val isToken = toggledEnchant.isTokenEnchant()
                        val enchants = getSortedEnchants(updatedPickaxe, isToken)
                        val enchantInfo = enchants.find { it.enchant.name.equals(toggledEnchant.name, ignoreCase = true) }

                        if (enchantInfo != null) {
                            // Update the glowing state directly
                            val updatedItem = item.withGlowing(enchantInfo.enchant.isEnabled())

                            // Update both Uniview's internal state AND the visual display
                            val existingGuiItem = currentMenu.items[slot]
                            if (existingGuiItem != null) {
                                currentMenu.items[slot] = existingGuiItem.copy(item = updatedItem)
                            }
                            openInventory.setItemStack(slot, updatedItem)
                            break
                        }
                    }
                }
            }
        }
    }

    // Data classes for structured information
    private data class EnchantInfo(
        val enchant: Enchant,
        val isOwned: Boolean,
        val canApply: Boolean,
        val isLocked: Boolean,
        val upgradeAmount: Int = 1
    ) {
        fun withUpgradeAmount(newAmount: Int): EnchantInfo {
            val maxPossible = if (isOwned) {
                // For owned enchants, can upgrade to max level
                enchant.getMaxLevel() - enchant.level
            } else {
                // For new enchants, can purchase up to max level (starting from level 1)
                enchant.getMaxLevel()
            }
            val clampedAmount = newAmount.coerceIn(1, maxPossible.coerceAtLeast(1))
            return copy(upgradeAmount = clampedAmount)
        }
    }

    // Extension functions for cleaner item creation
    private fun createActionButton(material: Material, name: String, lore: List<String>, amount: Int = 1): ItemStack {
        return ItemStack.builder(material)
            .customName(Component.text(name))
            .lore(lore.filter { it.isNotBlank() }.map { Component.text(it) })
            .amount(amount)
            .hideExtraTooltip()
            .build()
    }

    private fun createDecreaseButton(amount: Int) = createActionButton(
        Material.RED_STAINED_GLASS_PANE,
        "§c-$amount",
        listOf("§7Decrease amount"),
        amount
    )

    private fun createIncreaseButton(amount: Int) = createActionButton(
        Material.GREEN_STAINED_GLASS_PANE,
        "§a+$amount",
        listOf("§7Increase amount"),
        amount
    )

    private fun createMaxLevelItem(enchant: Enchant) = createActionButton(
        Material.BARRIER,
        "§c§lMAX LEVEL",
        listOf("§7Level: §e${enchant.level}/${enchant.getMaxLevel()}")
    )

    private fun createLockedItem(enchant: Enchant) = createActionButton(
        Material.BARRIER,
        "§c§lLOCKED",
        listOf("§7Requires Level ${enchant.getRequiredPickaxeLevel()}")
    )

    private fun createBoosterDetailItem(booster: Booster) = createActionButton(
        Material.POTION,
        "§d§l${booster.javaClass.simpleName}",
        listOf(
            "§7${booster.getDisplayString()}",
            "§7Type: §e${booster.javaClass.simpleName}",
            "§7Status: §aActive"
        )
    )

    private fun createSectionHeaderItem(title: String) = ItemStack.builder(Material.BLACK_STAINED_GLASS_PANE)
        .customName(Component.text(title))
        .lore(listOf(Component.text("§7Click to toggle visibility")))
        .build()

    private fun createViewAllBoostersButton() = ItemStack.builder(Material.BOOK)
        .customName(Component.text("§e§lView All Boosters"))
        .lore(listOf(Component.text("§7Click to view all available booster types")))
        .build()

    private fun createAvailableBoosterItem(boosterInfo: Booster.BoosterInfo) = ItemStack.builder(Material.PAPER)
        .customName(Component.text("§a§l${boosterInfo.displayName}"))
        .lore(listOf(
            Component.text("§7${boosterInfo.description}"),
            Component.text("§7Type: §e${boosterInfo.className}"),
            Component.text("§8This is an available booster type")
        ))
        .build()

    private fun createNoBoosterTypesItem() = ItemStack.builder(Material.BARRIER)
        .customName(Component.text("§c§lNo Booster Types Available"))
        .lore(listOf(Component.text("§7There are no booster types available in the system.")))
        .build()
}

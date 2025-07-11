package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.game.RegionManager
import twizzy.tech.util.Worlds
import twizzy.tech.util.YamlFactory

@Command("region")
@CommandPermission("admin.region")
@Description("Manage regions claims")
class Claim(private val regionManager: RegionManager, private val worlds: Worlds) {

    @Command("region")
    suspend fun regionUsage(actor: Player) {
        val helpMessages = YamlFactory.getCommandHelp("region")
        helpMessages.forEach { message ->
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("create <name> <radius>")
    suspend fun regionClaim(actor: Player, name: String, @Optional radius: Int?) {
        try {
            // Use centralized method in RegionManager to start the claiming process
            if (radius == null) {
                if (regionManager.startRegionClaiming(actor, name)) {
                    // Claiming process started successfully
                    val message = YamlFactory.getMessage(
                        "commands.region.create.success",
                        mapOf("name" to name, "radius" to "selection")
                    )
                    actor.sendMessage(Component.text(message))
                } else {
                    // Region with this name already exists
                    val message = YamlFactory.getMessage(
                        "commands.region.create.already_exists",
                        mapOf("name" to name)
                    )
                    actor.sendMessage(Component.text(message))
                }
            } else {
                // Create a region with the specified radius around the player
                val playerPos = actor.position

                // Calculate the min and max positions based on the radius
                val pos1 = Pos(
                    playerPos.x() - radius,
                    playerPos.y() - radius,
                    playerPos.z() - radius
                )

                val pos2 = Pos(
                    playerPos.x() + radius,
                    playerPos.y() + radius,
                    playerPos.z() + radius
                )

                // Get the world name
                val worldName = worlds.getWorldNameFromInstance(actor.instance) ?: run {
                    actor.sendMessage(Component.text("Cannot determine current world.").color(NamedTextColor.RED))
                    return
                }

                // Check if a region with this name already exists
                val existingRegion = regionManager.findRegionByName(worldName, name)
                if (existingRegion != null) {
                    actor.sendMessage(
                        Component.text("A region with this name already exists.")
                            .color(NamedTextColor.RED)
                    )
                    return
                }

                // Create the region directly
                val region = regionManager.createRegion(worldName, name, pos1, pos2)

                val message = YamlFactory.getMessage(
                    "commands.region.create.success",
                    mapOf("name" to name, "radius" to radius.toString())
                )
                actor.sendMessage(Component.text(message))
            }
        } catch (e: Exception) {
            val message = YamlFactory.getMessage(
                "commands.region.create.failed",
                mapOf("error" to e.message.orEmpty())
            )
            actor.sendMessage(Component.text(message))
        }
    }

    @Subcommand("flag <name> <string>")
    suspend fun regionFlag(actor: Player, name: String, string: String) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance) ?: run {
            actor.sendMessage(Component.text("Cannot determine current world.").color(NamedTextColor.RED))
            return
        }

        // Find the region with the given name
        val region = regionManager.findRegionByName(worldName, name)

        if (region == null) {
            actor.sendMessage(
                Component.text("Region '").color(NamedTextColor.RED)
                    .append(Component.text(name).color(NamedTextColor.YELLOW))
                    .append(Component.text("' not found.").color(NamedTextColor.RED))
            )
            return
        }

        // Toggle the flag on the region
        val flagAdded = regionManager.toggleRegionFlag(worldName, region.id, string.lowercase())

        if (flagAdded == true) {
            // Flag was added
            actor.sendMessage(
                Component.text("Flag '").color(NamedTextColor.GREEN)
                    .append(Component.text(string.lowercase()).color(NamedTextColor.GOLD))
                    .append(Component.text("' has been added to region '").color(NamedTextColor.GREEN))
                    .append(Component.text(region.name).color(NamedTextColor.GOLD))
                    .append(Component.text("'.").color(NamedTextColor.GREEN))
            )
        } else {
            // Flag was removed
            actor.sendMessage(
                Component.text("Flag '").color(NamedTextColor.YELLOW)
                    .append(Component.text(string.lowercase()).color(NamedTextColor.GOLD))
                    .append(Component.text("' has been removed from region '").color(NamedTextColor.YELLOW))
                    .append(Component.text(region.name).color(NamedTextColor.GOLD))
                    .append(Component.text("'.").color(NamedTextColor.YELLOW))
            )
        }

        // List all current flags on the region
        val flagsText = if (region.flags.isEmpty()) {
            Component.text("none").color(NamedTextColor.GRAY)
        } else {
            Component.text(region.flags.joinToString(", ")).color(NamedTextColor.AQUA)
        }

        actor.sendMessage(
            Component.text("Current flags: ").color(NamedTextColor.WHITE)
                .append(flagsText)
        )
    }

    @Subcommand("info <name>")
    suspend fun regionInfo(actor: Player, @Optional name: String?) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance) ?: run {
            actor.sendMessage(Component.text("Cannot determine current world.").color(NamedTextColor.RED))
            return
        }


        // Get all regions in this world
        val regions = regionManager.getRegions(worldName)

        // If no name is provided, check if the player is in a region
        val regionName = name ?: run {
            val position = actor.position

            // Find a region that contains the player's position
            val playerRegion = regions.find { it.isInRegion(position) }

            if (playerRegion == null) {
                actor.sendMessage(
                    Component.text("You are not standing in any region. Please specify a region name.")
                        .color(NamedTextColor.RED)
                )
                return
            }

            playerRegion.name
        }

        // Find the region with the given name
        val region = regions.find { it.name.equals(regionName, ignoreCase = true) }

        if (region == null) {
            actor.sendMessage(
                Component.text("Region '").color(NamedTextColor.RED)
                    .append(Component.text(regionName).color(NamedTextColor.YELLOW))
                    .append(Component.text("' not found.").color(NamedTextColor.RED))
            )
            return
        }

        // Calculate volume and positions
        val volume = region.getVolume()
        val (min, max) = region.getMinMaxPositions()

        // Display region information
        actor.sendMessage(Component.text("=== Region Information ===").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
        actor.sendMessage(
            Component.text("Name: ").color(NamedTextColor.YELLOW)
                .append(Component.text(region.name).color(NamedTextColor.GREEN))
        )
        actor.sendMessage(
            Component.text("ID: ").color(NamedTextColor.YELLOW)
                .append(Component.text(region.id.toString()).color(NamedTextColor.GRAY))
        )
        actor.sendMessage(
            Component.text("World: ").color(NamedTextColor.YELLOW)
                .append(Component.text(worldName).color(NamedTextColor.GREEN))
        )
        actor.sendMessage(
            Component.text("Size: ").color(NamedTextColor.YELLOW)
                .append(Component.text("$volume blocks").color(NamedTextColor.AQUA))
        )
        actor.sendMessage(
            Component.text("Bounds: ").color(NamedTextColor.YELLOW)
                .append(Component.text("(${min.x().toInt()}, ${min.y().toInt()}, ${min.z().toInt()}) to (${max.x().toInt()}, ${max.y().toInt()}, ${max.z().toInt()})").color(NamedTextColor.AQUA))
        )

        // Display flags
        val flagsText = if (region.flags.isEmpty()) {
            Component.text("none").color(NamedTextColor.GRAY)
        } else {
            Component.text(region.flags.joinToString(", ")).color(NamedTextColor.AQUA)
        }

        actor.sendMessage(
            Component.text("Flags: ").color(NamedTextColor.YELLOW)
                .append(flagsText)
        )

        // Visualize the region for 30 seconds
        regionManager.visualizeRegions(actor, listOf(region))
        actor.sendMessage(
            Component.text("Region has been visualized for 30 seconds.").color(NamedTextColor.GRAY)
        )
    }

    @Subcommand("remove <name>")
    suspend fun regionRemove(actor: Player, @Optional name: String?) {
        val worldName = worlds.getWorldNameFromInstance(actor.instance) ?: run {
            actor.sendMessage(Component.text("Cannot determine current world.").color(NamedTextColor.RED))
            return
        }

        // If no name is provided, check if the player is in a region
        val regionName = name ?: run {
            val position = actor.position
            val regions = regionManager.getRegions(worldName)

            // Find a region that contains the player's position
            val playerRegion = regions.find { it.isInRegion(position) }

            if (playerRegion == null) {
                actor.sendMessage(
                    Component.text("You are not standing in any region. Please specify a region name.")
                        .color(NamedTextColor.RED)
                )
                return
            }

            playerRegion.name
        }

        // Get all regions in this world
        val regions = regionManager.getRegions(worldName)

        // Find the region with the given name
        val region = regions.find { it.name.equals(regionName, ignoreCase = true) }

        if (region == null) {
            actor.sendMessage(
                Component.text("Region '").color(NamedTextColor.RED)
                    .append(Component.text(regionName).color(NamedTextColor.YELLOW))
                    .append(Component.text("' not found.").color(NamedTextColor.RED))
            )
            return
        }

        // Delete the region
        val success = regionManager.deleteRegion(worldName, region.id)

        if (success) {
            actor.sendMessage(
                Component.text("Region '").color(NamedTextColor.GREEN)
                    .append(Component.text(region.name).color(NamedTextColor.GOLD))
                    .append(Component.text("' has been removed.").color(NamedTextColor.GREEN))
            )
        } else {
            actor.sendMessage(
                Component.text("Failed to remove region '").color(NamedTextColor.RED)
                    .append(Component.text(region.name).color(NamedTextColor.YELLOW))
                    .append(Component.text("'.").color(NamedTextColor.RED))
            )
        }
    }

    @Subcommand("list")
    suspend fun regionList(actor: Player) {
        val regions = regionManager.getRegions(worlds.getWorldNameFromInstance(actor.instance))

        if (regions.isEmpty()) {
            actor.sendMessage(
                Component.text("You have no regions.")
                    .color(NamedTextColor.YELLOW)
            )
            return
        }

        // List all regions owned by the player
        actor.sendMessage(Component.text("Your Regions:").color(NamedTextColor.GOLD))
        regions.forEach { region ->
            actor.sendMessage(
                Component.text(" • ").color(NamedTextColor.GRAY)
                    .append(Component.text(region.name).color(NamedTextColor.YELLOW))
            )
        }
    }

    @Subcommand("view")
    suspend fun regionView(actor: Player) {
        val nearbyRegions = regionManager.getNearbyRegions(actor, 100.0) // Show regions within 100 blocks

        if (nearbyRegions.isEmpty()) {
            actor.sendMessage(
                Component.text("No regions found nearby.")
                    .color(NamedTextColor.YELLOW)
            )
            return
        }

        // Show visualization for all nearby regions
        regionManager.visualizeRegions(actor, nearbyRegions)

        actor.sendMessage(
            Component.text("Showing ").color(NamedTextColor.GREEN)
                .append(Component.text("${nearbyRegions.size}").color(NamedTextColor.GOLD))
                .append(Component.text(" nearby regions. The visualization will last for 30 seconds.").color(NamedTextColor.GREEN))
        )

        // List the nearby regions
        actor.sendMessage(Component.text("Nearby regions:").color(NamedTextColor.GOLD))
        nearbyRegions.forEach { region ->
            actor.sendMessage(
                Component.text(" • ").color(NamedTextColor.GRAY)
                    .append(Component.text(region.name).color(NamedTextColor.YELLOW))
            )
        }
    }
}
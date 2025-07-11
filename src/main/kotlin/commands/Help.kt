package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import revxrsal.commands.minestom.annotation.CommandPermission
import twizzy.tech.player.Profile
import java.io.File
import java.util.jar.JarFile

class Help {

    @Command("help")
    @Description("Shows all available commands")
    fun help(actor: Player, @Optional page: Int?) {
        val allCommands = getAllCommandDescriptions()
        val availableCommands = filterCommandsByPermission(actor, allCommands)

        val commandsPerPage = 10
        val totalPages = (availableCommands.size + commandsPerPage - 1) / commandsPerPage
        val currentPage = (page ?: 1).coerceIn(1, totalPages)

        actor.sendMessage(
            Component.text("=== Available Commands (Page $currentPage/$totalPages) ===")
                .color(NamedTextColor.GOLD)
        )

        val startIndex = (currentPage - 1) * commandsPerPage
        val endIndex = (startIndex + commandsPerPage).coerceAtMost(availableCommands.size)
        val commandList = availableCommands.toList()

        for (i in startIndex until endIndex) {
            val (command, description) = commandList[i]
            actor.sendMessage(
                Component.text()
                    .append(Component.text("/$command").color(NamedTextColor.YELLOW))
                    .append(Component.text(" - ").color(NamedTextColor.GRAY))
                    .append(Component.text(description).color(NamedTextColor.WHITE))
                    .build()
            )
        }

        if (totalPages > 1) {
            val pageNavigation = Component.text()

            if (currentPage > 1) {
                pageNavigation.append(
                    Component.text("« Previous").color(NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/help ${currentPage - 1}"))
                )
            }

            if (currentPage > 1 && currentPage < totalPages) {
                pageNavigation.append(Component.text(" | ").color(NamedTextColor.GRAY))
            }

            if (currentPage < totalPages) {
                pageNavigation.append(
                    Component.text("Next »").color(NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/help ${currentPage + 1}"))
                )
            }

            actor.sendMessage(pageNavigation.build())
        }
    }

    private fun filterCommandsByPermission(player: Player, commands: Map<String, Pair<String, String?>>): Map<String, String> {
        val filteredCommands = mutableMapOf<String, String>()

        commands.forEach { (command, data) ->
            val (description, permission) = data

            // If no permission is required, or player has the permission, include the command
            if (permission == null || hasPermission(player, permission)) {
                filteredCommands[command] = description
            }
        }

        return filteredCommands.toSortedMap()
    }

    private fun hasPermission(player: Player, permission: String): Boolean {
        // Get the player's profile which contains their effective permissions
        val profile = Profile.getProfile(player.uuid)

        // If no profile found, default to no permissions (or you could default to allow)
        if (profile == null) {
            return false
        }

        // Check if the player has the exact permission or a wildcard permission
        val effectivePermissions = profile.getEffectivePermissions()

        // Check for exact permission match
        if (effectivePermissions.contains(permission.lowercase())) {
            return true
        }

        // Check for wildcard permissions (e.g., "admin.*" covers "admin.world")
        val permissionParts = permission.lowercase().split(".")
        for (i in permissionParts.indices) {
            val wildcardPerm = permissionParts.take(i + 1).joinToString(".") + ".*"
            if (effectivePermissions.contains(wildcardPerm)) {
                return true
            }
        }

        // Check for super admin permission (*)
        if (effectivePermissions.contains("*")) {
            return true
        }

        return false
    }

    private fun getAllCommandDescriptions(): Map<String, Pair<String, String?>> {
        val commands = mutableMapOf<String, Pair<String, String?>>()

        // Commands to exclude from help (variants/aliases that shouldn't be shown)
        val excludedCommands = setOf(
            "gmc", "gms",  // Gamemode variants
            "tp",               // Teleport aliases
            "bal",                        // Balance alias
            "help",                      // Help command itself
            // Add more commands to exclude here as needed
        )

        try {
            // Get all classes in the commands package
            val commandClasses = getCommandClasses()

            commandClasses.forEach { clazz ->
                // Check for class-level permission
                val classPermissionAnnotation = clazz.getAnnotation(CommandPermission::class.java)
                val classPermission: String? = try {
                    classPermissionAnnotation?.value
                } catch (e: Exception) {
                    null
                }

                val methods = clazz.declaredMethods
                methods.forEach { method ->
                    val commandAnnotation = method.getAnnotation(Command::class.java)
                    val descriptionAnnotation = method.getAnnotation(Description::class.java)
                    val methodPermissionAnnotation = method.getAnnotation(CommandPermission::class.java)
                    val methodPermission: String? = try {
                        methodPermissionAnnotation?.value
                    } catch (e: Exception) {
                        null
                    }

                    if (commandAnnotation != null) {
                        val commandNames = commandAnnotation.value
                        val description = descriptionAnnotation?.value ?: "No description available"

                        // Use method permission if available, otherwise fall back to class permission
                        val requiredPermission = methodPermission ?: classPermission

                        // Only use the first command name (ignore aliases)
                        if (commandNames.isNotEmpty()) {
                            val primaryCommand = commandNames[0]
                            // Extract just the command name (remove parameters)
                            val cleanCommand = primaryCommand.split(" ")[0]

                            // Skip excluded commands
                            if (!excludedCommands.contains(cleanCommand.lowercase())) {
                                commands[cleanCommand] = Pair(description, requiredPermission)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            commands["error"] = Pair("Failed to load commands: ${e.message}", null)
        }

        return commands
    }

    private fun getCommandClasses(): List<Class<*>> {
        val classes = mutableListOf<Class<*>>()
        val packageName = "twizzy.tech.commands"

        try {
            val classLoader = Thread.currentThread().contextClassLoader
            val path = packageName.replace('.', '/')
            val resources = classLoader.getResources(path)

            while (resources.hasMoreElements()) {
                val resource = resources.nextElement()
                val file = File(resource.file)

                if (file.exists() && file.isDirectory) {
                    // We're running from source/IDE
                    file.listFiles()?.forEach { classFile ->
                        if (classFile.name.endsWith(".class")) {
                            val className = classFile.name.substring(0, classFile.name.length - 6)
                            try {
                                val clazz = Class.forName("$packageName.$className")
                                classes.add(clazz)
                            } catch (_: Exception) {
                                // Skip classes that can't be loaded
                            }
                        }
                    }
                } else if (resource.protocol == "jar") {
                    // We're running from a JAR file
                    val jarPath = resource.path.substring(5, resource.path.indexOf("!"))
                    val jar = JarFile(jarPath)
                    val entries = jar.entries()

                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.startsWith(path) && entry.name.endsWith(".class")) {
                            val className = entry.name.substring(path.length + 1, entry.name.length - 6)
                            if (!className.contains("/")) {
                                try {
                                    val clazz = Class.forName("$packageName.$className")
                                    classes.add(clazz)
                                } catch (_: Exception) {
                                    // Skip classes that can't be loaded
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback: try to load known command classes
            val knownCommands = listOf(
                "Activity", "Balance", "Claim", "Clear", "Economy",
                "Gamemode", "Give", "Help", "Mine", "Mines", "Pay",
                "Teleport", "Warps", "Withdraw", "World"
            )

            knownCommands.forEach { className ->
                try {
                    val clazz = Class.forName("$packageName.$className")
                    classes.add(clazz)
                } catch (_: Exception) {
                    // Skip if class doesn't exist
                }
            }
        }

        return classes
    }
}
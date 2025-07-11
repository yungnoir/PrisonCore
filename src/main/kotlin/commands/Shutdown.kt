package twizzy.tech.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.minestom.annotation.CommandPermission
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Optional
import twizzy.tech.util.DurationParser
import kotlin.system.exitProcess

class Shutdown {

    @Command("stop")
    @CommandPermission("admin.shutdown")
    @Description("Shutdown the server")
    fun stopServer(actor: Player) {
        println("[PrisonCore/shutdown] ${actor.username} has initiated a server shutdown.")

        // Set the shutdown flag to stop the main coroutine loop
        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
        for (player in onlinePlayers) {
            player.kick(Component.text("The server is shutting down...", NamedTextColor.RED))
        }
        MinecraftServer.stopCleanly()
    }

    @Command("restart")
    @CommandPermission("admin.restart")
    @Description("Restart the server after a specified duration (e.g., 10m, 1h, 30s)")
    fun restartServer(actor: Player, @Optional duration: String?) {
        val delaySeconds = if (duration.isNullOrBlank()) {
            0L // Immediate restart if no duration provided
        } else {
            DurationParser.parse(duration) ?: run {
                actor.sendMessage(Component.text("Invalid duration format! Use formats like: 10s, 5m, 1h, 2d", NamedTextColor.RED))
                return
            }
        }

        // Check if we're running in an IDE/development environment
        val isInIDE = isRunningInIDE()

        if (delaySeconds == 0L) {
            // Immediate restart
            println("[PrisonCore/shutdown] ${actor.username} has initiated an immediate server restart.")
            if (isInIDE) {
                actor.sendMessage(Component.text("Development mode detected - server will shutdown instead of restart. Use IDE rerun button to restart.", NamedTextColor.YELLOW))
            } else {
                actor.sendMessage(Component.text("Server restart initiated immediately.", NamedTextColor.GREEN))
            }

            // Notify all players about the restart
            val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
            for (player in onlinePlayers) {
                if (isInIDE) {
                    player.kick(Component.text("Server is shutting down for restart. Please reconnect shortly.", NamedTextColor.YELLOW))
                } else {
                    player.kick(Component.text("The server is restarting, you may join in a few moments", NamedTextColor.YELLOW))
                }
            }

            performRestart(isInIDE)
        } else {
            // Scheduled restart
            val formattedDuration = DurationParser.format(delaySeconds)
            println("[PrisonCore/shutdown] ${actor.username} has scheduled a server restart in $formattedDuration.")

            if (isInIDE) {
                actor.sendMessage(Component.text("Development mode: Server shutdown scheduled in $formattedDuration. Use IDE rerun button to restart.", NamedTextColor.YELLOW))
            } else {
                actor.sendMessage(Component.text("Server restart scheduled in $formattedDuration.", NamedTextColor.GREEN))
            }

            // Notify all players about the scheduled restart
            val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
            for (player in onlinePlayers) {
                if (isInIDE) {
                    player.sendMessage(Component.text("⚠ Server shutdown scheduled in $formattedDuration (development mode)", NamedTextColor.YELLOW))
                } else {
                    player.sendMessage(Component.text("⚠ Server restart scheduled in $formattedDuration", NamedTextColor.YELLOW))
                }
            }

            // Schedule the restart
            GlobalScope.launch {
                try {
                    // Send warning messages at intervals
                    scheduleWarnings(delaySeconds, isInIDE)

                    // Wait for the full duration
                    delay(delaySeconds * 1000)

                    // Final notification and restart
                    val currentPlayers = MinecraftServer.getConnectionManager().onlinePlayers
                    for (player in currentPlayers) {
                        if (isInIDE) {
                            player.kick(Component.text("Server is shutting down now. Please reconnect shortly.", NamedTextColor.YELLOW))
                        } else {
                            player.kick(Component.text("The server is restarting now, you may join in a few moments", NamedTextColor.YELLOW))
                        }
                    }

                    performRestart(isInIDE)
                } catch (e: Exception) {
                    println("[PrisonCore/shutdown] Error during scheduled restart: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun isRunningInIDE(): Boolean {
        val classPath = System.getProperty("java.class.path", "")
        return classPath.contains("idea", ignoreCase = true) ||
                classPath.contains("intellij", ignoreCase = true) ||
                classPath.contains("build/classes", ignoreCase = true)
    }

    private suspend fun scheduleWarnings(totalSeconds: Long, isInIDE: Boolean) {
        val warningTimes = listOf(300L, 180L, 120L, 60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L) // seconds
        var elapsed = 0L

        for (warningTime in warningTimes) {
            if (warningTime >= totalSeconds) continue

            val waitTime = totalSeconds - warningTime - elapsed
            if (waitTime > 0) {
                delay(waitTime * 1000)
                elapsed += waitTime

                val formattedTime = DurationParser.format(warningTime)
                val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
                for (player in onlinePlayers) {
                    if (isInIDE) {
                        player.sendMessage(Component.text("⚠ Server shutdown in $formattedTime (development mode)", NamedTextColor.YELLOW))
                    } else {
                        player.sendMessage(Component.text("⚠ Server restart in $formattedTime", NamedTextColor.YELLOW))
                    }
                }
                val action = if (isInIDE) "shutdown" else "restart"
                println("[PrisonCore/shutdown] $action warning sent: $formattedTime remaining")
            }
        }
    }

    private fun performRestart(isInIDE: Boolean) {
        GlobalScope.launch {
            try {
                // Give a moment for players to be kicked
                delay(1000)

                if (isInIDE) {
                    // In IDE mode, just stop cleanly - the developer can use the rerun button
                    println("[PrisonCore/shutdown] Development mode: Stopping server cleanly. Use IDE rerun button to restart.")
                    MinecraftServer.stopCleanly()
                } else {
                    // In production mode, stop and restart the JVM
                    MinecraftServer.stopCleanly()

                    // Give the server time to shut down properly
                    delay(2000)

                    // Restart the JVM process
                    restartJVM()
                }
            } catch (e: Exception) {
                println("[PrisonCore/shutdown] Error during restart: ${e.message}")
                e.printStackTrace()
                exitProcess(1)
            }
        }
    }

    private fun restartJVM() {
        try {
            val javaBin = System.getProperty("java.home") + "/bin/java"
            val currentJar = System.getProperty("java.class.path")

            // Build the command to restart the application
            val command = mutableListOf<String>()
            command.add(javaBin)

            // Add JVM arguments if they exist
            val jvmArgs = System.getProperty("sun.java.command")?.split(" ")?.drop(1) ?: emptyList()
            val inputArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments
            command.addAll(inputArgs)

            command.add("-cp")
            command.add(currentJar)
            command.add("twizzy.tech.MainKt") // Kotlin main class naming convention

            println("[PrisonCore/shutdown] Executing restart command: ${command.joinToString(" ")}")

            // Start the new process
            val processBuilder = ProcessBuilder(command)
            processBuilder.inheritIO()
            processBuilder.start()

            // Exit the current process
            exitProcess(0)

        } catch (e: Exception) {
            println("[PrisonCore/shutdown] Failed to restart JVM: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
    }
}

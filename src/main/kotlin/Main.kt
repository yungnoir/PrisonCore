package twizzy.tech

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import com.github.shynixn.mccoroutine.minestom.launch
import io.github._4drian3d.signedvelocity.minestom.SignedVelocity
import io.github.togar2.pvp.MinestomPvP
import io.github.togar2.pvp.feature.CombatFeatures
import io.github.togar2.pvp.feature.FeatureType
import io.github.togar2.pvp.feature.provider.DifficultyProvider
import io.github.togar2.pvp.utils.CombatVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.lucko.spark.minestom.SparkMinestom
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import revxrsal.commands.minestom.MinestomLamp
import twizzy.tech.commands.*
import twizzy.tech.game.Engine
import twizzy.tech.game.MineManager
import twizzy.tech.game.RegionManager
import twizzy.tech.listeners.ChatHandler
import twizzy.tech.listeners.ConnectionHandler
import twizzy.tech.listeners.ItemInteractions
import twizzy.tech.listeners.MapInteractions
import twizzy.tech.player.PlayerData
import twizzy.tech.player.Ranks
import twizzy.tech.util.*
import java.nio.file.Path

var isServerLoaded = false

suspend fun main() {

    val server = MinecraftServer.init()
    val logger = MinecraftServer.LOGGER


    System.setProperty("minestom.chunk-view-distance", "16")
    System.setProperty("minestom.use-new-chunk-sending", "true")

    System.setProperty("minestom.new-chunk-sending-count-per-interval", "50")
    System.setProperty("minestom.new-chunk-sending-send-interval", "1")

    System.setProperty("minestom.keep-alive-delay", "7") // Keep-alive interval in seconds (10 default)

    println("[PrisonCore/main] Initializing startup on ${Thread.currentThread().name}/${Thread.currentThread().id}")

    // Switches into suspendable scope on startup.
    server.launch {
        try {
            // Create the database configuration file before initializing connections
            val configFile = YamlFactory.createConfigIfNotExists("database.yml", "database.yml")
            YamlFactory.initializeLanguage()
            println("[PrisonCore/main] Successfully retrieved database credentials at: ${configFile.absolutePath}")

            // Now initialize the connections
            val lettuce = LettuceCache.getInstance()
            lettuce.init()
            val mongoStream = MongoStream.getInstance()
            mongoStream.init()

            val ranks = Ranks.getInstance()
            ranks.init()


            println("[PrisonCore/main] MainThread 1 Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")
            delay(2000)
            println("[PrisonCore/main] MainThread 2 Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")

            withContext(Dispatchers.IO) {
                println("[PrisonCore/main] Simulating data load Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")
                Thread.sleep(500)
            }
            println("[PrisonCore/main] MainThread 3 Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")

            // Mark server as loaded after all initialization is complete
            isServerLoaded = true
            println("[PrisonCore/main] Server initialization complete - players can now join!")

            // Continue with the periodic save loop
            while (true) {
                delay(10000 * 60)
                val mongoStream = MongoStream.getInstance()
                val cachedPlayerData = PlayerData.getAllCachedData()

                var savedCount = 0
                cachedPlayerData.forEach { playerData ->
                    try {
                        // Save player data to MongoDB
                        mongoStream.savePlayerData(playerData)

                        savedCount++
                    } catch (e: Exception) {
                        println("[PrisonCore/main] Error saving player data for ${playerData.uuid}: ${e.message}")
                    }
                }

                val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
                for (player in onlinePlayers) {
                    PlayerData.saveInventory(player.uuid, player.inventory)
                    PlayerData.saveBackpack(player.uuid)
                }


                if (savedCount > 0) {
                    println("[PrisonCore/main] Successfully saved $savedCount/${cachedPlayerData.size} player data records")
                }
            }
        } catch (e: Exception) {
            println("[PrisonCore/main] Error during server initialization: ${e.message}")
            e.printStackTrace()
            // Don't set isServerLoaded to true if initialization fails
        }
    }

    MinestomPvP.init()

    // Create the instance using our Worlds system
    val worldsManager = Worlds()
    val regionManager = RegionManager(worldsManager, server)
    val mineManager = MineManager(worldsManager, server, regionManager)
    regionManager.setMineManager(mineManager)

    val instanceMap = InstanceMap(worldsManager, regionManager, mineManager)
    worldsManager.setInstanceMap(instanceMap)


    val instanceContainer = worldsManager.getSpawnWorld()

    // Add an event callback to specify the spawning instance (and the spawn position)
    val globalEventHandler = MinecraftServer.getGlobalEventHandler()


    globalEventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        val player = event.getPlayer()
        event.spawningInstance = instanceContainer
        player.respawnPoint = worldsManager.getSpawnPoint()
    }

    globalEventHandler.addChild(
        CombatFeatures.getVanilla(CombatVersion.LEGACY, DifficultyProvider.DEFAULT)
            .remove(FeatureType.FALL)
            .remove(FeatureType.EXHAUSTION)
            .add(CombatFeatures.FAIR_RISING_FALLING_KNOCKBACK)
            .build().createNode());

    globalEventHandler.addSuspendingListener(server, PlayerSpawnEvent::class.java) { event ->
        val player = event.getPlayer()
        MinestomPvP.setLegacyAttack(player, true)

        player.isAllowFlying = true
        player.addEffect(Potion(PotionEffect.SPEED, 2, -1))
        player.permissionLevel

        player.addEffect(Potion(PotionEffect.HASTE, 1, -1))
        player.getAttribute(Attribute.MINING_EFFICIENCY).setBaseValue(100.0) // Set mining speed to 10
        player.getAttribute(Attribute.BLOCK_BREAK_SPEED).setBaseValue(10.0) // Set breaking speed to 10
        player.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER).setBaseValue(0.0) // Disable fall damage
    }

    val directory = Path.of("spark")
    val spark: SparkMinestom? = SparkMinestom.builder(directory)
        .commands(true) // enables registration of Spark commands
        .permissionHandler({ sender, permission -> true }) // allows all command senders to execute all commands
        .enable()

    val lamp = MinestomLamp.builder()
        .build()

    lamp.register(Shutdown())
    lamp.register(Gamemode())
    lamp.register(Clear())
    lamp.register(Teleport())
    lamp.register(World(worldsManager))
    lamp.register(Claim(regionManager, worldsManager))
    lamp.register(Mines(regionManager, worldsManager, mineManager, instanceMap))
    lamp.register(Mine(instanceMap))
    lamp.register(Balance())
    lamp.register(Economy())
    lamp.register(Warps(worldsManager))
    lamp.register(Give())
    lamp.register(Withdraw())
    lamp.register(Pay())
    lamp.register(Fix())
    lamp.register(Activity())
    lamp.register(Help())

    MapInteractions(server, regionManager, worldsManager)
    ItemInteractions(server, regionManager, worldsManager)

    ConnectionHandler(server, instanceMap)
    ChatHandler(server)
    Engine(server)

    SignedVelocity.initialize()
    server.start("0.0.0.0", 25567)



    // Register shutdown task to run code when the server stops
    MinecraftServer.getSchedulerManager().buildShutdownTask {
        println("[PrisonCore/main] Server is shutting down, saving data and closing connections...")

        println("[PrisonCore/main] Saving all cached player data to MongoDB...")
        val cachedPlayerData = PlayerData.getAllCachedData()
        val mongoStream = MongoStream.getInstance()

        runBlocking {
            var savedCount = 0
            cachedPlayerData.forEach { playerData ->
                try {
                    // Save player data to MongoDB
                    mongoStream.savePlayerData(playerData)

                    savedCount++
                } catch (e: Exception) {
                    println("[PrisonCore/main] Error saving player data for ${playerData.uuid}: ${e.message}")
                }
            }

            val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
            for (player in onlinePlayers) {
                PlayerData.saveInventory(player.uuid, player.inventory)
                PlayerData.saveBackpack(player.uuid)
            }


            println("[PrisonCore/main] Successfully saved $savedCount/${cachedPlayerData.size} player data records")
        }

        // Close connections
        val lettuce = LettuceCache.getInstance()
        println("[PrisonCore/main] Closing Redis connection...")
        lettuce.shutdown()

        println("[PrisonCore/main] Closing MongoDB connection...")
        mongoStream.close()

        println("[PrisonCore/main] Graceful shutdown has completed!")
    }
}

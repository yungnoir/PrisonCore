package twizzy.tech

import com.github.shynixn.mccoroutine.minestom.addSuspendingListener
import com.github.shynixn.mccoroutine.minestom.launch
import io.github.togar2.pvp.MinestomPvP
import io.github.togar2.pvp.feature.CombatFeatures
import io.github.togar2.pvp.feature.FeatureType
import io.github.togar2.pvp.feature.provider.DifficultyProvider
import io.github.togar2.pvp.utils.CombatVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import revxrsal.commands.minestom.MinestomLamp
import twizzy.tech.commands.Balance
import twizzy.tech.commands.Claim
import twizzy.tech.commands.Gamemode
import twizzy.tech.commands.Mine
import twizzy.tech.commands.Mines
import twizzy.tech.commands.World
import twizzy.tech.game.MineManager
import twizzy.tech.game.PlayerData
import twizzy.tech.game.RegionManager
import twizzy.tech.listeners.ConnectionHandler
import twizzy.tech.listeners.ItemInteractions
import twizzy.tech.listeners.MapInteractions
import twizzy.tech.util.InstanceMap
import twizzy.tech.util.Worlds

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
suspend fun main() {

    val minecraftServer = MinecraftServer.init()
    val logger = MinecraftServer.LOGGER


    System.setProperty("minestom.chunk-view-distance", "16")
    System.setProperty("minestom.use-new-chunk-sending", "true")

    System.setProperty("minestom.new-chunk-sending-count-per-interval", "50")
    System.setProperty("minestom.new-chunk-sending-send-interval", "1")

    System.setProperty("minestom.keep-alive-delay", "7") // Keep-alive interval in seconds (10 default)

    println("[MCCoroutineSampleServer/main] Is starting on Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")

    // Switches into suspendable scope on startup.
    minecraftServer.launch {
        println("[MCCoroutineSampleServer/main] MainThread 1 Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")
        delay(2000)
        println("[MCCoroutineSampleServer/main] MainThread 2 Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")

        withContext(Dispatchers.IO) {
            println("[MCCoroutineSampleServer/main] Simulating data load Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")
            Thread.sleep(500)
        }
        println("[MCCoroutineSampleServer/main] MainThread 3 Thread:${Thread.currentThread().name}/${Thread.currentThread().id}")
    }

    MinestomPvP.init()

    // Create the instance using our Worlds system
    val worldsManager = Worlds()
    val regionManager = RegionManager(worldsManager, minecraftServer)
    val mineManager = MineManager(worldsManager, minecraftServer, regionManager)
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

    globalEventHandler.addSuspendingListener(minecraftServer, AsyncPlayerConfigurationEvent::class.java) { event ->
        val player = event.getPlayer()
        instanceMap.createInstance(player, "GTA")

        player.inventory.addItemStack(ItemStack.of(Material.WOODEN_PICKAXE)) // Give player a wooden pickaxe
    }

    globalEventHandler.addChild(
        CombatFeatures.getVanilla(CombatVersion.LEGACY, DifficultyProvider.DEFAULT)
            .remove(FeatureType.FALL)
            .remove(FeatureType.EXHAUSTION)
            .add(CombatFeatures.FAIR_RISING_FALLING_KNOCKBACK)
            .build().createNode());


    globalEventHandler.addSuspendingListener(minecraftServer, AsyncPlayerPreLoginEvent::class.java) { event ->
        val username = event.username
//        playerconnect.retrieveProfile(username)
    }

    globalEventHandler.addSuspendingListener(minecraftServer, PlayerSpawnEvent::class.java) { event ->
        val player = event.getPlayer()
        MinestomPvP.setLegacyAttack(player, true)

        player.isAllowFlying = true
        player.addEffect(Potion(PotionEffect.SPEED, 2, -1))

        player.addEffect(Potion(PotionEffect.HASTE, 1, -1))
        player.getAttribute(Attribute.ARMOR).setBaseValue(20.0) // Set armor to 20
        player.getAttribute(Attribute.MINING_EFFICIENCY).setBaseValue(100.0) // Set mining speed to 10
        player.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER).setBaseValue(0.0) // Disable fall damage
    }


    val lamp = MinestomLamp.builder()
        .build()
    lamp.register(Gamemode())
    lamp.register(World(worldsManager))
    lamp.register(Claim(regionManager, worldsManager))
    lamp.register(Mines(regionManager, worldsManager, mineManager, instanceMap))
    lamp.register(Mine(instanceMap))
    lamp.register(Balance())

    MapInteractions(minecraftServer, regionManager, worldsManager)
    ItemInteractions(minecraftServer, regionManager, worldsManager)

    ConnectionHandler(minecraftServer)

    minecraftServer.start("0.0.0.0", 25565)
}
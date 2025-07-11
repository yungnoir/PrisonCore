package twizzy.tech.util

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.LightingChunk
import twizzy.tech.game.MineManager
import twizzy.tech.game.RegionManager
import java.io.File
import java.util.*

class InstanceMap(
    private val worlds: Worlds,
    private val regionManager: RegionManager,
    private val mineManager: MineManager
) {
    // Map of player UUID to their instance
    val playerInstances = mutableMapOf<UUID, Instance>()
    // Map of player UUID to their spawn position
    private val playerSpawns = mutableMapOf<UUID, Pos>()

    /**
     * Creates a new instance for the player, copying the specified world (Polar, regions, mines)
     */
    suspend fun createInstance(player: Player, worldName: String): Instance? {
        // Get the source world directory
        val worldDir = File(worlds.worldsDir, worldName)
        if (!worldDir.exists()) return null

        // Load the world config
        val configFile = File(worldDir, "config.yml")
        if (!configFile.exists()) return null
        val config = twizzy.tech.util.YamlFactory.loadConfig(configFile)

        // Copy the polar file if it exists
        val polarPath = config["filepath"] as? String ?: ""
        val instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer(worlds.fullbrightKey)
        instance.time = 1000L
        instance.timeRate = 0
        instance.setChunkSupplier(::LightingChunk)
        if (polarPath.isNotEmpty() && File(polarPath).exists()) {
            instance.setChunkLoader(net.hollowcube.polar.PolarLoader(java.nio.file.Paths.get(polarPath)))
        } else {
            // Default generator
            instance.setGenerator { unit ->
                unit.modifier().fillHeight(0, 40, net.minestom.server.instance.block.Block.GRASS_BLOCK)
            }
        }

        // Copy regions
        val regionsFile = File(worldDir, "regions.yml")
        var regionsList: List<Map<String, Any>> = emptyList()
        val mineNames = mutableSetOf<String>()
        val minesFile = File(worldDir, "mines.yml")
        if (minesFile.exists()) {
            val minesData = YamlFactory.loadConfig(minesFile)
            val minesList = minesData["mines"] as? List<Map<String, Any>> ?: emptyList()
            for (mineData in minesList) {
                val name = mineData["name"] as String
                mineNames.add(name)
            }
        }
        if (regionsFile.exists()) {
            val regionsData = YamlFactory.loadConfig(regionsFile)
            regionsList = regionsData["regions"] as? List<Map<String, Any>> ?: emptyList()
            for (regionData in regionsList) {
                try {
                    val name = regionData["name"] as String
                    if (mineNames.contains(name)) continue // skip mine regions
                    val pos1Map = regionData["pos1"] as Map<*, *>
                    val pos2Map = regionData["pos2"] as Map<*, *>
                    val pos1 = net.minestom.server.coordinate.Pos(
                        (pos1Map["x"] as Number).toDouble(),
                        (pos1Map["y"] as Number).toDouble(),
                        (pos1Map["z"] as Number).toDouble()
                    )
                    val pos2 = net.minestom.server.coordinate.Pos(
                        (pos2Map["x"] as Number).toDouble(),
                        (pos2Map["y"] as Number).toDouble(),
                        (pos2Map["z"] as Number).toDouble()
                    )
                    val rawFlags = (regionData["flags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val flags = rawFlags.map { it.lowercase() }.toMutableSet()
                    val id = if (regionData["id"] != null) UUID.fromString(regionData["id"] as String) else UUID.randomUUID()
                    val region = RegionManager.Region(id, player.uuid.toString(), pos1, pos2, flags)
                    regionManager.setRegion(worldName, region)
                } catch (_: Exception) {}
            }
        }

        // Copy mines
        if (minesFile.exists()) {
            val minesData = twizzy.tech.util.YamlFactory.loadConfig(minesFile)
            val minesList = minesData["mines"] as? List<Map<String, Any>> ?: emptyList()
            for (mineData in minesList) {
                try {
                    val name = mineData["name"] as String
                    val originalRegionId = mineData["regionId"] as? String
                    // Find the original region bounds by name (region name == mine name)
                    val regionData = regionsList.find { (it["name"] as? String) == name }
                    if (regionData == null) continue // skip if no region found
                    val pos1Map = regionData["pos1"] as Map<*, *>
                    val pos2Map = regionData["pos2"] as Map<*, *>
                    val pos1 = net.minestom.server.coordinate.Pos(
                        (pos1Map["x"] as Number).toDouble(),
                        (pos1Map["y"] as Number).toDouble(),
                        (pos1Map["z"] as Number).toDouble()
                    )
                    val pos2 = net.minestom.server.coordinate.Pos(
                        (pos2Map["x"] as Number).toDouble(),
                        (pos2Map["y"] as Number).toDouble(),
                        (pos2Map["z"] as Number).toDouble()
                    )

                    val rawFlags = (regionData["flags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val flags = rawFlags.map { it.lowercase() }.toMutableSet()
                    val newRegionId = UUID.randomUUID()
                    val region = RegionManager.Region(newRegionId, player.uuid.toString(), pos1, pos2, flags)
                    regionManager.setRegion(player.uuid.toString(), region)
                    val blocksList = mineData["blocks"] as? List<Map<String, Any>> ?: emptyList()
                    val blocks = blocksList.map { MineManager.MineBlock(it["block"] as String) }.toMutableList()
                    val lastReset = System.currentTimeMillis()
                    val resetInterval = (mineData["resetInterval"] as? Number)?.toLong() ?: 900L
                    val mine = MineManager.Mine("${player.uuid}_mine", newRegionId, blocks, lastReset, resetInterval)
                    // Add to mineManager's map for this player's instance
                    mineManager.setMine(player.uuid.toString(), "${player.uuid}_mine", mine)
                    mineManager.resetMine(player.uuid.toString(), "${player.uuid}_mine")
                } catch (_: Exception) {}
            }
        }

        // Store the instance for the player
        playerInstances[player.uuid] = instance
        // Save the spawn position if available in config
        val spawn = config["spawnPoint"] as? Map<*, *>
        if (spawn != null) {
            val x = (spawn["x"] as? Number)?.toDouble() ?: 0.0
            val y = (spawn["y"] as? Number)?.toDouble() ?: 40.0
            val z = (spawn["z"] as? Number)?.toDouble() ?: 0.0
            val yaw = (spawn["yaw"] as? Number)?.toFloat() ?: 0.0f
            val pitch = (spawn["pitch"] as? Number)?.toFloat() ?: 0.0f
            playerSpawns[player.uuid] = Pos(x, y, z, yaw, pitch)
        }
        return instance
    }

    fun getInstance(player: Player): Instance? = playerInstances[player.uuid]

    fun getSpawn(player: Player): Pos? = playerSpawns[player.uuid]
}
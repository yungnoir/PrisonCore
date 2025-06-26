package twizzy.tech.game

import java.io.File
import java.util.UUID
import twizzy.tech.util.YamlFactory

class PlayerData(private val uuid: UUID) {
    var balance: Double = 0.00
    var blocksBroken: Int = 0
    var backpack: MutableMap<String, Int> = mutableMapOf()

    init {
        loadPlayerData()
    }

    fun incrementBlocksBroken() {
        blocksBroken++
        savePlayerData()
    }

    private fun loadPlayerData() {
        val playerFile = File("players/${uuid}.yml")
        if (!playerFile.exists()) {
            playerFile.parentFile.mkdirs()
            playerFile.createNewFile()
            savePlayerData()
        }

        if (playerFile.exists()) {
            val data = YamlFactory.loadConfig(playerFile)
            balance = data["balance"] as? Double ?: 0.0
            blocksBroken = data["blocksBroken"] as? Int ?: 0
            backpack = data["backpack"] as? MutableMap<String, Int> ?: mutableMapOf()
        }
    }

    fun savePlayerData() {
        val playerFile = File("players/${uuid}.yml")
        val data = mapOf(
            "balance" to balance,
            "blocksBroken" to blocksBroken,
            "backpack" to backpack
        )
        YamlFactory.saveConfig(data, playerFile)
    }

    fun addBlockToBackpack(material: String, quantity: Int) {
        backpack[material] = backpack.getOrDefault(material, 0) + quantity
        savePlayerData()
    }

    fun removeBlockFromBackpack(material: String, quantity: Int): Boolean {
        val currentQuantity = backpack.getOrDefault(material, 0)
        if (currentQuantity < quantity) {
            return false
        }
        backpack[material] = currentQuantity - quantity
        if (backpack[material] == 0) {
            backpack.remove(material)
        }
        savePlayerData()
        return true
    }
}
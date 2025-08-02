package twizzy.tech.game.items.pickaxe.enchants

import twizzy.tech.game.items.pickaxe.Enchant.EnchantConfig
import twizzy.tech.game.items.pickaxe.Enchant.ConfigurableEffectEnchant

class Haste(level: Int) : ConfigurableEffectEnchant(NAME, level, config) {
    companion object {
        const val NAME = "Haste"
        private var config = EnchantConfig(
            maxLevel = 5,
            basePrice = java.math.BigDecimal(1000),
            priceIncrease = 5.0,
            basePercentage = 100.0,
            percentageIncrease = 0.0,
            pickaxeLevel = 1,
            item = "GOLD_PICKAXE"
        )

        fun loadConfig(configMap: Map<String, Any>, basePath: String) {
            config = EnchantConfig.loadFromConfig(
                configMap,
                NAME,
                basePath,
                EnchantConfig(
                    maxLevel = 5,
                    basePrice = java.math.BigDecimal(1000),
                    priceIncrease = 5.0,
                    basePercentage = 100.0,
                    percentageIncrease = 0.0,
                    pickaxeLevel = 1,
                    item = "GOLD_PICKAXE"
                )
            )
        }
    }

    override fun isTokenEnchant(): Boolean = true // Haste is a token enchant
}
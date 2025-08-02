package twizzy.tech.game.items.pickaxe.enchants

import twizzy.tech.game.items.pickaxe.Enchant

class TokenGreed(level: Int) : Enchant.ConfigurableMultiplierEnchant(NAME, level, config) {
    companion object {
        const val NAME = "TokenGreed"
        private var config = EnchantConfig(
            maxLevel = 200,
            basePrice = java.math.BigDecimal(1000),
            priceIncrease = 5.0,
            basePercentage = 0.05, // Correct: should match config.yml (0.05)
            percentageIncrease = 0.01, // Correct: should match config.yml (0.01)
            pickaxeLevel = 1,
            item = "MAGMA_CREAM"
        )

        fun loadConfig(configMap: Map<String, Any>, basePath: String) {
            config = EnchantConfig.loadFromConfig(
                configMap,
                NAME,
                basePath,
                EnchantConfig(
                    maxLevel = 200,
                    basePrice = java.math.BigDecimal(1000),
                    priceIncrease = 5.0,
                    basePercentage = 0.05, // Correct: should match config.yml (0.05)
                    percentageIncrease = 0.01, // Correct: should match config.yml (0.01)
                    pickaxeLevel = 1,
                    item = "MAGMA_CREAM"
                )
            )
        }
    }

    override fun isTokenEnchant(): Boolean = false // Token Greed is a soul enchant
}
package ca.cgagnier.wlednativeandroid.model

import ca.cgagnier.wlednativeandroid.model.wledapi.Info

enum class UpdateSourceType {
    OFFICIAL_WLED,
    QUINLED,
    CUSTOM,
    MOONMODULES,
}

data class UpdateSourceDefinition(
    val type: UpdateSourceType,
    val brandPattern: String,
    val githubOwner: String,
    val githubRepo: String,
    val product: String? = null,
)

object UpdateSourceRegistry {
    val sources = listOf(
        UpdateSourceDefinition(
            type = UpdateSourceType.OFFICIAL_WLED,
            brandPattern = "WLED",
            githubOwner = "wled",
            githubRepo = "WLED",
        ),
        UpdateSourceDefinition(
            type = UpdateSourceType.QUINLED,
            brandPattern = "QuinLED",
            githubOwner = "intermittech",
            githubRepo = "QuinLED-Firmware",
        ),
        UpdateSourceDefinition(
            type = UpdateSourceType.MOONMODULES,
            brandPattern = "WLED",
            product = "MoonModules",
            githubOwner = "MoonModules",
            githubRepo = "WLED-MM",
        ),
    )

    fun getSource(info: Info): UpdateSourceDefinition? {
        val brandMatches = sources.filter { it.brandPattern == info.brand }
        return brandMatches.find { it.product == info.product }
            ?: brandMatches.find { it.product == null }
    }
}

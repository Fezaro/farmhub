package com.farm_tech.farmhub.models.tips

data class FarmingTip(
    val id: String,
    val category: String,
    val title: String,
    val tip: String
)

data class FarmingTipUiModel(
    val id: String = "",
    val category: String = "",
    val title: String = "",
    val tip: String = ""
)

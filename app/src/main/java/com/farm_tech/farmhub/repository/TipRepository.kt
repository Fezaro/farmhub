package com.farm_tech.farmhub.repository

import android.content.Context
import com.farm_tech.farmhub.models.tips.FarmingTip
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.random.Random

object TipRepository {
    private const val ASSET_FILE = "tips.json"

    @Volatile
    private var cachedTips: List<FarmingTip> = emptyList()
    @Volatile
    private var lastTipId: String? = null
    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized && cachedTips.isNotEmpty()) return
        synchronized(this) {
            if (initialized && cachedTips.isNotEmpty()) return
            val json = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<FarmingTip>>() {}.type
            cachedTips = Gson().fromJson(json, type) ?: emptyList()
            initialized = true
        }
    }

    fun isReady(): Boolean = cachedTips.isNotEmpty()

    fun categories(): List<String> = cachedTips.map { it.category }.distinct()

    fun getRandomTip(category: String? = null): FarmingTip? {
        val source = if (category.isNullOrBlank()) cachedTips else cachedTips.filter {
            it.category.equals(category, ignoreCase = true)
        }
        if (source.isEmpty()) return null
        val pool = if (source.size > 1) source.filterNot { it.id == lastTipId }.ifEmpty { source } else source
        val selected = pool[Random.nextInt(pool.size)]
        lastTipId = selected.id
        return selected
    }
}

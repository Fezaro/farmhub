package com.farm_tech.farmhub

import android.app.Application
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.repository.TipRepository

class FarmHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.initialize(this)
        TipRepository.initialize(this)
    }
}

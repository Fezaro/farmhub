package com.farm_tech.farmhub

import android.app.Application
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.repository.MediaRepository
import com.farm_tech.farmhub.repository.TipRepository

class FarmHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.initialize(this)
        MediaRepository.initialize(this)
        TipRepository.initialize(this)
    }
}

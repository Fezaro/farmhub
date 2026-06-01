package com.farm_tech.farmhub.models.message

data class SendMessageRequest(
    val message: String,
    val phone: String
)

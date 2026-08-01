package com.farm_tech.farmhub.models.message

import com.farm_tech.farmhub.models.messaging.MessageItemResponse

data class SendMessageResponse(
    val status: String? = null,
    val message: MessageItemResponse? = null
)

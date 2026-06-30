package com.farm_tech.farmhub.models.messaging
data class UserProfileCache(
    val phone: String,
    val name: String,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - cachedAt > 60 * 60 * 1000 // 1 hour
    }
}
data class UserNameMapping(
    val phone: String,
    val fullName: String,
    val username: String = ""
) {
    fun displayName(): String = if (fullName.isNotBlank()) fullName else username.ifBlank { phone }
}


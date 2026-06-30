package com.farm_tech.farmhub.models.profile

data class UserProfileResponse(
    val data: UserProfileData? = null,
    val status: String? = null
)

data class UserProfileData(
    val id: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val names: String? = null,
    val role: String? = null,
    val phone: String? = null,
    val county: String? = null,
    val subCounty: String? = null,
    val paidUser: String? = null
)

package com.farm_tech.farmhub.models.signup

data class RegisterResponse(
    val user: RegisteredUser? = null,
    val status: String? = null,
    val message: String? = null
)

data class RegisteredUser(
    val id: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val names: String? = null,
    val email: String? = null,
    val role: String? = null,
    val phone: String? = null,
    val county: String? = null,
    val subCounty: String? = null
)

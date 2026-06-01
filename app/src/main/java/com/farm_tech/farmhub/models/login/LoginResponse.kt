package com.farm_tech.farmhub.models.login

data class LoginResponse(
    val status: String,
    val token: String,
    val issued: Long,
    val expires: Long,
    val newUser: Boolean,
    val userDetails: UserDetails
)

data class UserDetails(
    val id: String,
    val createdAt: String,
    val updatedAt: String,
    val names: String,
    val role: String,
    val phone: String,
    val county: String?,
    val subCounty: String?,
    val paidUser: String?
)

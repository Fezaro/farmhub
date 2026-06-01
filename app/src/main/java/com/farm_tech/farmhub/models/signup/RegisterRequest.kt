package com.farm_tech.farmhub.models.signup

data class RegisterRequest(
    val names: String,
    val email: String?,
    val phone: String,
    val county: String?,
    val subCounty: String?,
    val password: String
)

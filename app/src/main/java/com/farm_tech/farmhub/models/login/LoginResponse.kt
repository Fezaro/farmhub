package com.farm_tech.farmhub.models.login

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("issued") val issued: Long? = null,
    @SerializedName("expires") val expires: Long? = null,
    @SerializedName(value = "newUser", alternate = ["new_user"]) val newUser: Boolean? = null,
    @SerializedName(value = "userDetails", alternate = ["user_details", "user"]) val userDetails: UserDetails? = null
)

data class UserDetails(
    @SerializedName(value = "id", alternate = ["_id", "userId"]) val id: String? = null,
    @SerializedName(value = "createdAt", alternate = ["created_at"]) val createdAt: String? = null,
    @SerializedName(value = "updatedAt", alternate = ["updated_at"]) val updatedAt: String? = null,
    @SerializedName("names") val names: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("county") val county: String? = null,
    @SerializedName(value = "subCounty", alternate = ["sub_county"]) val subCounty: String? = null,
    @SerializedName(value = "paidUser", alternate = ["paid_user"]) val paidUser: String? = null
)

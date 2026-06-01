package com.example.app.models.profile

data class ProfileUiModel(
    val id: String,
    val names: String,
    val phone: String,
    val county: String?,
    val subCounty: String?,
    val paymentStatus: String
)

fun UserProfileResponse.toUiModelOrNull(): ProfileUiModel? {
    val profileData = data ?: return null

    return ProfileUiModel(
        id = profileData.id.orEmpty(),
        names = profileData.names ?: "No Name",
        phone = profileData.phone ?: "No Phone",
        county = profileData.county,
        subCounty = profileData.subCounty,
        paymentStatus = profileData.paidUser?.takeIf { it.isNotBlank() } ?: "Unknown"
    )
}


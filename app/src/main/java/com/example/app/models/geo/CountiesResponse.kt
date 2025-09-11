package com.example.app.models.geo

data class CountiesResponse(
    val counties: List<String>? = null,
    val subCounties: List<String>? = null, // present when querying with county param
    val status: String? = null
)


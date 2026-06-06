package com.farm_tech.farmhub.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.farm_tech.farmhub.api.ApiClient

@Composable
fun AuthenticatedAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    @DrawableRes placeholderRes: Int? = null,
    @DrawableRes errorRes: Int? = null
) {
    val context = LocalContext.current
    val token = ApiClient.currentToken()
    val request = remember(imageUrl, token) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .apply {
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholderRes?.let { androidx.compose.ui.res.painterResource(id = it) },
        error = errorRes?.let { androidx.compose.ui.res.painterResource(id = it) }
    )
}


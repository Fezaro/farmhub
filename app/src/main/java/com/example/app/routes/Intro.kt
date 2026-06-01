package com.example.app.routes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.R

@Composable
fun IntroScreen(
    onFarmHelpClick: () -> Unit,
    onVideosClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 740
    val isNarrowWidth = configuration.screenWidthDp < 380

    val sidePadding = if (isNarrowWidth) 16.dp else 22.dp
    val topSpacing = if (isCompactHeight) 8.dp else 20.dp
    val logoSize = if (isCompactHeight) 132.dp else 156.dp
    val headerToLogoSpacing = if (isCompactHeight) 10.dp else 14.dp
    val logoToCardSpacing = if (isCompactHeight) 20.dp else 28.dp
    val cardBetweenSpacing = if (isCompactHeight) 12.dp else 16.dp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sidePadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(topSpacing))

            Text(
                text = "Welcome to",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(headerToLogoSpacing))

            Image(
                painter = painterResource(id = R.drawable.farmhub_logo),
                contentDescription = "FarmHub Logo",
                modifier = Modifier
                    .size(logoSize)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(logoToCardSpacing))

            IntroFeatureCard(
                logoRes = R.drawable.farmhelp_logo_horizontal,
                logoContentDescription = "FarmHelp",
                message = "Do you have farming questions to ask?\nClick here to talk to an expert!",
                accentColor = MaterialTheme.colorScheme.primary,
                compact = isCompactHeight,
                onClick = onFarmHelpClick
            )

            Spacer(modifier = Modifier.height(cardBetweenSpacing))

            IntroFeatureCard(
                logoRes = R.drawable.farmers_videos_logo_final_2,
                logoContentDescription = "FarmVideos",
                message = "Do you want to learn more about farming?\nClick here to watch farming videos for free!",
                accentColor = MaterialTheme.colorScheme.tertiary,
                compact = isCompactHeight,
                onClick = onVideosClick
            )
        }
    }
}

@Composable
private fun IntroFeatureCard(
    logoRes: Int,
    logoContentDescription: String,
    message: String,
    accentColor: Color,
    compact: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        label = "introCardScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 2.dp else 5.dp,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.85f),
        label = "introCardElevation"
    )

    val logoHeight = if (compact) 46.dp else 54.dp
    val logoWidthRatio = if (compact) 0.52f else 0.56f
    val messageFont = if (compact) 16.sp else 18.sp
    val lineHeight = if (compact) 22.sp else 25.sp
    val verticalPadding = if (compact) 14.dp else 18.dp

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = logoContentDescription,
                modifier = Modifier
                    .height(logoHeight)
                    .fillMaxWidth(logoWidthRatio)
            )

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = messageFont),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = lineHeight
            )
        }

    }
}
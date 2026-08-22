package com.farm_tech.farmhub.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farm_tech.farmhub.R
import com.farm_tech.farmhub.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onToggleTheme: () -> Unit,
    isLoggedIn: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.fetchProfile()
    }

    val profile = viewModel.profile
    val error = viewModel.error
    val isLoading = viewModel.isLoading
    val scope = rememberCoroutineScope()

    // No Scaffold, header, or bottom nav here. Only content.
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Cover Photo
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = "Cover Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Profile Photo
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = 50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(60.dp))

        // Username and Email
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }

            if (error != null) {
                Text("Error: $error", color = MaterialTheme.colorScheme.error)
            }

            if (profile != null) {
                Text(profile.names, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, contentDescription = "Email", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(profile.phone, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (profile.county != null) {
                    Text("County: ${profile.county}", fontSize = 14.sp)
                }
                if (profile.subCounty != null) {
                    Text("SubCounty: ${profile.subCounty}", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Subscription Status: ${profile.paymentStatus}", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Preferences / Settings
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PreferenceItem(icon = Icons.Default.Notifications, title = "Notifications") { }
            PreferenceItem(icon = Icons.Default.DarkMode, title = "Dark Mode") { onToggleTheme() }
            PreferenceItem(icon = Icons.Default.Language, title = "Language") { }
            PreferenceItem(icon = Icons.Default.Settings, title = "Account Settings") { }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Auth toggle button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoggedIn) {
                Button(
                    onClick = {
                        scope.launch {
                            onSignOutClick()
                        }
                    }
                ) {
                    Text("Sign Out")
                }
            } else {
                Button(
                    onClick = onSignInClick
                ) {
                    Text("Sign In")
                }
            }
        }
    }
}

@Composable
fun PreferenceItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, fontSize = 16.sp)
    }
}

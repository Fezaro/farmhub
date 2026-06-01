package com.farm_tech.farmhub.routes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.farm_tech.farmhub.auth.SessionRestoration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SplashScreen - Handles app startup and session restoration.
 *
 * Shows while the app:
 * 1. Initializes core services
 * 2. Attempts to restore user session
 * 3. Determines the correct startup destination
 *
 * This gives the WA/TG-like experience where logged-in users
 * go straight to the home screen without re-authenticating.
 */
@Composable
fun SplashScreen(
    onStartupCheckComplete: (isLoggedIn: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            // Simulate minimal startup delay for smooth UI transition
            delay(300)

            // Attempt to restore session from stored credentials
            val isLoggedIn = SessionRestoration.restoreSessionOnStartup(context)

            // Navigate to appropriate screen
            onStartupCheckComplete(isLoggedIn)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}



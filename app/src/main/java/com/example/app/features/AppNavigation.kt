package com.example.app.features

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.app.auth.AuthManager
import com.example.app.auth.TokenValidator
import com.example.app.routes.AuthScreen
import com.example.app.routes.IntroScreen
import com.example.app.ui.tabs.HelpScreen
import com.example.app.ui.tabs.ProfileScreen
import com.example.app.ui.tabs.ChatScreen
import com.example.app.ui.tabs.VideoScreen
import com.example.app.ui.components.BottomNavBar
import com.example.app.ui.components.AppHeader
import com.example.app.ui.tabs.VideoDetailScreen

/**
 * Main app navigation with comprehensive authentication enforcement.
 *
 * Senior practice: Centralized navigation logic ensures consistent auth checks
 * across all protected routes. Token validation occurs at navigation composition time
 * with defense-in-depth checks at screen level as well.
 */
@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun AppNavigation(
    navController: NavHostController,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    var isLoggedIn by remember { mutableStateOf(AuthManager.isLoggedIn(context)) }

    // Track current route for bottom nav reactively
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.INTRO

    // Observe auth state changes (e.g., when token expires)
    LaunchedEffect(Unit) {
        AuthManager.addAuthStateListener(context) { loggedIn ->
            Log.d("AppNavigation", "Auth state changed: loggedIn=$loggedIn")
            isLoggedIn = loggedIn
            // If logged out unexpectedly, redirect to auth
            if (!loggedIn && currentRoute !in listOf(AppRoutes.INTRO, AppRoutes.AUTH)) {
                navController.navigate(AppRoutes.AUTH) {
                    popUpTo(AppRoutes.INTRO) { inclusive = false }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.INTRO
    ) {
        composable(AppRoutes.INTRO) {
            // Standalone: No header/bottom nav
            IntroScreen(
                onFarmHelpClick = { navController.navigate(AppRoutes.HELP) },
                onVideosClick = {
                    if (isLoggedIn) navController.navigate(AppRoutes.VIDEOS)
                    else navController.navigate(AppRoutes.AUTH)
                },
            )
        }
        composable(AppRoutes.AUTH) {
            // Standalone: No header/bottom nav
            AuthScreen(
                onLoginSuccess = {
                    Log.d("AppNavigation", "Login successful. Updating auth state.")
                    isLoggedIn = true
                    navController.navigate(AppRoutes.INTRO) {
                        popUpTo(AppRoutes.AUTH) { inclusive = true }
                    }
                },
                onSignupSuccess = {
                    Log.d("AppNavigation", "Signup successful. Returning to login.")
                    navController.navigate(AppRoutes.AUTH) {
                        popUpTo(AppRoutes.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(AppRoutes.HELP) {
            AppScaffold(
                navController = navController,
                currentRoute = currentRoute,
                isLoggedIn = isLoggedIn,
                onToggleTheme = onToggleTheme
            ) {
                // Screen-level authentication check (defense-in-depth)
                if (!isLoggedIn) {
                    Log.w("AppNavigation", "Unauthorized access to HELP screen. Redirecting to AUTH.")
                    LaunchedEffect(Unit) {
                        navController.navigate(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                if (!TokenValidator.isTokenValid()) {
                    Log.w("AppNavigation", "Token invalid for HELP screen. Triggering logout.")
                    AuthManager.logout(context)
                    LaunchedEffect(Unit) {
                        navController.navigate(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                HelpScreen(
                    onChatClick = {
                        if (isLoggedIn) navController.navigate(AppRoutes.CHAT)
                        else navController.navigate(AppRoutes.AUTH)
                    },
                    onVideosClick = {
                        if (isLoggedIn) navController.navigate(AppRoutes.VIDEOS)
                        else navController.navigate(AppRoutes.AUTH)
                    }
                )
            }
        }
        composable(AppRoutes.PROFILE) {
            AppScaffold(
                navController = navController,
                currentRoute = currentRoute,
                isLoggedIn = isLoggedIn,
                onToggleTheme = onToggleTheme
            ) {
                ProfileScreen(
                    onToggleTheme = onToggleTheme,
                    isLoggedIn = isLoggedIn,
                    onSignInClick = { navController.navigate(AppRoutes.AUTH) },
                    onSignOutClick = {
                        Log.d("AppNavigation", "User initiated logout from ProfileScreen")
                        AuthManager.logout(context)
                        isLoggedIn = false
                        navController.navigate(AppRoutes.AUTH) {
                            popUpTo(AppRoutes.INTRO) { inclusive = false }
                        }
                    }
                )
            }
        }

        composable(AppRoutes.VIDEOS) {
            AppScaffold(
                navController = navController,
                currentRoute = currentRoute,
                isLoggedIn = isLoggedIn,
                onToggleTheme = onToggleTheme
            ) {
                // Route-level authentication check
                if (!isLoggedIn) {
                    Log.w("AppNavigation", "Unauthorized access to VIDEOS route. Redirecting to AUTH.")
                    LaunchedEffect(Unit) {
                        navController.navigate(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                // Screen-level authentication check (defense-in-depth)
                if (!TokenValidator.isTokenValid()) {
                    Log.w("AppNavigation", "Token invalid for VIDEOS screen. Triggering logout.")
                    AuthManager.logout(context)
                    LaunchedEffect(Unit) {
                        navController.navigate(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                VideoScreen(navController = navController)
            }
        }

        composable(
            route = "video_detail/{videoId}",
            arguments = listOf(navArgument("videoId") { type = NavType.IntType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getInt("videoId") ?: return@composable
            AppScaffold(
                navController = navController,
                currentRoute = currentRoute,
                isLoggedIn = isLoggedIn,
                onToggleTheme = onToggleTheme
            ) {
                if (!TokenValidator.isTokenValid()) {
                    Log.w("AppNavigation", "Token invalid for VIDEO_DETAIL screen. Redirecting to AUTH.")
                    LaunchedEffect(Unit) {
                        AuthManager.logout(context)
                        navController.navigate(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                VideoDetailScreen(
                    videoId = videoId,
                    onVideoClick = { nextId ->
                        navController.navigate("video_detail/$nextId")
                    }
                )
            }
        }

        composable(AppRoutes.CHAT) {
            AppScaffold(
                navController = navController,
                currentRoute = currentRoute,
                isLoggedIn = isLoggedIn,
                onToggleTheme = onToggleTheme
            ) {
                // Route-level authentication check
                if (!isLoggedIn) {
                    Log.w("AppNavigation", "Unauthorized access to CHAT route. Redirecting to AUTH.")
                    LaunchedEffect(Unit) {
                        navController.navigate(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                // Screen-level authentication check (defense-in-depth)
                if (!TokenValidator.isTokenValid()) {
                    Log.w("AppNavigation", "Token invalid for CHAT screen. Triggering logout.")
                    AuthManager.logout(context)
                    LaunchedEffect(Unit) {
                        navController.navigate(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                ChatScreen()
            }
        }
    }
}

@Composable
private fun AppScaffold(
    navController: NavHostController,
    currentRoute: String,
    isLoggedIn: Boolean,
    onToggleTheme: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            AppHeader(
                title = "Farm Hub",
                onChatClick = {
                    if (isLoggedIn) navController.navigate(AppRoutes.CHAT)
                    else navController.navigate(AppRoutes.AUTH)
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onTabSelected = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
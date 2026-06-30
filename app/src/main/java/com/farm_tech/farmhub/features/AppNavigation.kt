package com.farm_tech.farmhub.features

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.farm_tech.farmhub.auth.AuthManager
import com.farm_tech.farmhub.auth.TokenValidator
import com.farm_tech.farmhub.auth.SessionRestoration
import com.farm_tech.farmhub.routes.AuthScreen
import com.farm_tech.farmhub.routes.IntroScreen
import com.farm_tech.farmhub.routes.SplashScreen
import com.farm_tech.farmhub.ui.tabs.HelpScreen
import com.farm_tech.farmhub.ui.tabs.ProfileScreen
import com.farm_tech.farmhub.ui.tabs.ChatScreen
import com.farm_tech.farmhub.ui.tabs.TipScreen
import com.farm_tech.farmhub.ui.tabs.WeatherScreen
import com.farm_tech.farmhub.ui.tabs.VideoScreen
import com.farm_tech.farmhub.ui.components.BottomNavBar
import com.farm_tech.farmhub.ui.components.AppHeader
import com.farm_tech.farmhub.ui.tabs.VideoDetailScreen

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
    var startupCheckComplete by remember { mutableStateOf(false) }
    var startingRoute by remember { mutableStateOf(AppRoutes.SPLASH) }

    // Track current route for bottom nav reactively
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startingRoute
    val latestCurrentRoute by rememberUpdatedState(currentRoute)

    // On app startup, determine the correct starting route
    LaunchedEffect(Unit) {
        // This will be called when we navigate away from SPLASH
        startupCheckComplete = true
    }

    // Observe auth state changes (e.g., when token expires via 401)
    LaunchedEffect(Unit) {
        AuthManager.addAuthStateListener(context) { loggedIn ->
            Log.d("AppNavigation", "Auth state changed: loggedIn=$loggedIn")
            isLoggedIn = loggedIn
            // If logged out unexpectedly, redirect to auth
            if (!loggedIn && latestCurrentRoute !in listOf(AppRoutes.INTRO, AppRoutes.AUTH, AppRoutes.SPLASH)) {
                navController.navigateSingleTopTo(AppRoutes.AUTH) {
                    popUpTo(AppRoutes.INTRO) { inclusive = false }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.SPLASH
    ) {
        // SplashScreen handles startup and session restoration
        composable(AppRoutes.SPLASH) {
            SplashScreen(
                onStartupCheckComplete = { restoredLoggedIn ->
                    isLoggedIn = restoredLoggedIn
                    startingRoute = AppRoutes.INTRO

                    // Navigate away from splash to the appropriate screen
                    navController.navigate(startingRoute) {
                        popUpTo(AppRoutes.SPLASH) { inclusive = true }
                    }
                    startupCheckComplete = true
                }
            )
        }

        composable(AppRoutes.INTRO) {
            // Standalone: No header/bottom nav
            IntroScreen(
                onFarmHelpClick = {
                    if (isLoggedIn) navController.navigateToTopLevel(AppRoutes.HELP)
                    else navController.navigateSingleTopTo(AppRoutes.AUTH)
                },
                onVideosClick = {
                    if (isLoggedIn) navController.navigateToTopLevel(AppRoutes.VIDEOS)
                    else navController.navigateSingleTopTo(AppRoutes.AUTH)
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
                isLoggedIn = isLoggedIn
            ) {
                // Screen-level authentication check (defense-in-depth)
                if (!isLoggedIn) {
                    Log.w("AppNavigation", "Unauthorized access to HELP screen. Redirecting to AUTH.")
                    LaunchedEffect(Unit) {
                        navController.navigateSingleTopTo(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                if (!TokenValidator.isTokenValid()) {
                    Log.w("AppNavigation", "Token invalid for HELP screen. Triggering logout.")
                    AuthManager.logout(context)
                    LaunchedEffect(Unit) {
                        navController.navigateSingleTopTo(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                HelpScreen(
                    onChatClick = {
                        if (isLoggedIn) navController.navigateSingleTopTo(AppRoutes.CHAT)
                        else navController.navigateSingleTopTo(AppRoutes.AUTH)
                    },
                    onWeatherClick = {
                        if (isLoggedIn) navController.navigateSingleTopTo(AppRoutes.WEATHER)
                        else navController.navigateSingleTopTo(AppRoutes.AUTH)
                    },
                    onTipClick = {
                        if (isLoggedIn) navController.navigateSingleTopTo(AppRoutes.TIPS)
                        else navController.navigateSingleTopTo(AppRoutes.AUTH)
                    },
                    onVideosClick = {
                        if (isLoggedIn) navController.navigateToTopLevel(AppRoutes.VIDEOS)
                        else navController.navigateSingleTopTo(AppRoutes.AUTH)
                    }
                )
            }
        }
        composable(AppRoutes.PROFILE) {
            AppScaffold(
                navController = navController,
                currentRoute = currentRoute,
                isLoggedIn = isLoggedIn
            ) {
                ProfileScreen(
                    onToggleTheme = onToggleTheme,
                    isLoggedIn = isLoggedIn,
                    onSignInClick = { navController.navigateSingleTopTo(AppRoutes.AUTH) },
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
                isLoggedIn = isLoggedIn
            ) {
                // Route-level authentication check
                if (!isLoggedIn) {
                    Log.w("AppNavigation", "Unauthorized access to VIDEOS route. Redirecting to AUTH.")
                    LaunchedEffect(Unit) {
                        navController.navigateSingleTopTo(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                // Screen-level authentication check (defense-in-depth)
                if (!TokenValidator.isTokenValid()) {
                    Log.w("AppNavigation", "Token invalid for VIDEOS screen. Triggering logout.")
                    AuthManager.logout(context)
                    LaunchedEffect(Unit) {
                        navController.navigateSingleTopTo(AppRoutes.AUTH)
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
                isLoggedIn = isLoggedIn
            ) {
                if (!TokenValidator.isTokenValid()) {
                    Log.w("AppNavigation", "Token invalid for VIDEO_DETAIL screen. Redirecting to AUTH.")
                    LaunchedEffect(Unit) {
                        AuthManager.logout(context)
                        navController.navigateSingleTopTo(AppRoutes.AUTH)
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
                isLoggedIn = isLoggedIn
            ) {
                // Route-level authentication check
                if (!isLoggedIn) {
                    Log.w("AppNavigation", "Unauthorized access to CHAT route. Redirecting to AUTH.")
                    LaunchedEffect(Unit) {
                        navController.navigateSingleTopTo(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                // Screen-level authentication check (defense-in-depth)
                if (!TokenValidator.isTokenValid()) {
                    Log.w("AppNavigation", "Token invalid for CHAT screen. Triggering logout.")
                    AuthManager.logout(context)
                    LaunchedEffect(Unit) {
                        navController.navigateSingleTopTo(AppRoutes.AUTH)
                    }
                    return@AppScaffold
                }

                ChatScreen(
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigateToTopLevel(AppRoutes.INTRO)
                        }
                    }
                )
            }
        }

        composable(AppRoutes.WEATHER) {
            if (!isLoggedIn) {
                LaunchedEffect(Unit) {
                    navController.navigateSingleTopTo(AppRoutes.AUTH)
                }
                return@composable
            }
            WeatherScreen()
        }

        composable(AppRoutes.TIPS) {
            if (!isLoggedIn) {
                LaunchedEffect(Unit) {
                    navController.navigateSingleTopTo(AppRoutes.AUTH)
                }
                return@composable
            }
            TipScreen()
        }
    }
}

@Composable
private fun AppScaffold(
    navController: NavHostController,
    currentRoute: String,
    isLoggedIn: Boolean,
    content: @Composable () -> Unit
) {
    val protectedRoutes = remember { setOf(AppRoutes.HELP, AppRoutes.VIDEOS, AppRoutes.CHAT, AppRoutes.VIDEO_DETAIL) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "FarmHub",
                onChatClick = {
                    if (isLoggedIn) navController.navigateSingleTopTo(AppRoutes.CHAT)
                    else navController.navigateSingleTopTo(AppRoutes.AUTH)
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onTabSelected = { route ->
                    if (route in protectedRoutes && !isLoggedIn) {
                        navController.navigateSingleTopTo(AppRoutes.AUTH)
                        return@BottomNavBar
                    }

                    navController.navigateToTopLevel(route)
                }
            )
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return

    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateSingleTopTo(route: String) {
    navigateSingleTopTo(route) {}
}

private fun NavHostController.navigateSingleTopTo(
    route: String,
    builder: androidx.navigation.NavOptionsBuilder.() -> Unit
) {
    if (currentBackStackEntry?.destination?.route == route) return

    navigate(route) {
        launchSingleTop = true
        builder()
    }
}


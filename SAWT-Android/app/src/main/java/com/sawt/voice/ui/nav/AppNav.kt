package com.sawt.voice.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sawt.voice.ui.components.rememberReduceMotion
import com.sawt.voice.ui.screens.AboutScreen
import com.sawt.voice.ui.screens.CommandLibraryScreen
import com.sawt.voice.ui.screens.DashboardScreen
import com.sawt.voice.ui.screens.FloatingSettingsScreen
import com.sawt.voice.ui.screens.GuideScreen
import com.sawt.voice.ui.screens.HistoryScreen
import com.sawt.voice.ui.screens.ListeningScreen
import com.sawt.voice.ui.screens.PermissionsScreen
import com.sawt.voice.ui.screens.PrivacyScreen
import com.sawt.voice.ui.screens.ShortcutsScreen
import com.sawt.voice.ui.screens.SplashScreen
import com.sawt.voice.ui.screens.VoiceSettingsScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val LISTEN = "listen"
    const val LIBRARY = "library"
    const val SHORTCUTS = "shortcuts"
    const val HISTORY = "history"
    const val VOICE_SETTINGS = "voice_settings"
    const val FLOATING = "floating"
    const val PRIVACY = "privacy"
    const val PERMISSIONS = "permissions"
    const val GUIDE = "guide"
    const val ABOUT = "about"
}

@Composable
fun SawtNavHost(startDestination: String = Routes.SPLASH) {
    val nav = rememberNavController()
    val reduce = rememberReduceMotion()
    val go: (String) -> Unit = { route -> nav.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { nav.popBackStack() }
    val enter: EnterTransition = if (reduce) EnterTransition.None else fadeIn(tween(200))
    val exit: ExitTransition = if (reduce) ExitTransition.None else fadeOut(tween(120))

    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen {
                nav.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
        }
        composable(Routes.HOME) { DashboardScreen(go) }
        composable(Routes.LISTEN) { ListeningScreen(back, go) }
        composable(Routes.LIBRARY) { CommandLibraryScreen(back) }
        composable(Routes.SHORTCUTS) { ShortcutsScreen(back) }
        composable(Routes.HISTORY) { HistoryScreen(back) }
        composable(Routes.VOICE_SETTINGS) { VoiceSettingsScreen(back) }
        composable(Routes.FLOATING) { FloatingSettingsScreen(back) }
        composable(Routes.PRIVACY) { PrivacyScreen(back) }
        composable(Routes.PERMISSIONS) { PermissionsScreen(back) }
        composable(Routes.GUIDE) { GuideScreen(back) }
        composable(Routes.ABOUT) { AboutScreen(back) }
    }
}

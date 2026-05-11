package com.fitness.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitness.app.timer.RestTimerService
import com.fitness.app.ui.components.FitnessBottomBar
import com.fitness.app.ui.navigation.FitnessNavHost
import com.fitness.app.ui.navigation.Routes
import com.fitness.app.ui.theme.FitnessTheme
import com.fitness.app.ui.theme.LocalFitnessColors
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bottom-bar height made available to nested screens so list content can leave
 * room for the bar (since the Scaffold lives at the activity level, not per
 * screen). Falls back to 0 on routes where the bar is hidden.
 */
val LocalBottomBarPadding = staticCompositionLocalOf<Dp> { 0.dp }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        RestTimerService.setAppForeground(true)
    }

    override fun onStop() {
        RestTimerService.setAppForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FitnessTheme {
                val c = LocalFitnessColors.current
                val nav = rememberNavController()
                val backEntry by nav.currentBackStackEntryAsState()
                val currentRoute = backEntry?.destination?.route
                val showBar = currentRoute in Routes.BottomBarRoots

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = c.bg,
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                    bottomBar = {
                        if (showBar) {
                            FitnessBottomBar(
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                    nav.navigate(route) {
                                        popUpTo(Routes.Home) {
                                            saveState = true
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { padding ->
                    // Screens own their status-bar padding (so they can render
                    // edge-to-edge headers/heroes). We hand them the bottom-bar
                    // height so list content can leave room for the bar.
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                        CompositionLocalProvider(
                            LocalBottomBarPadding provides 0.dp
                        ) {
                            FitnessNavHost(navController = nav)
                        }
                    }
                }
            }
        }
    }
}

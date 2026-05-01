package com.fitness.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fitness.app.ui.screens.dashboard.DashboardScreen
import com.fitness.app.ui.screens.history.HistoryScreen
import com.fitness.app.ui.screens.history.SessionDetailScreen
import com.fitness.app.ui.screens.home.HomeScreen
import com.fitness.app.ui.screens.plans.PlanDetailScreen
import com.fitness.app.ui.screens.plans.PlanEditScreen
import com.fitness.app.ui.screens.plans.PlansScreen
import com.fitness.app.ui.screens.settings.SettingsScreen
import com.fitness.app.ui.screens.workout.ActiveWorkoutScreen

@Composable
fun FitnessNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.Home) {

        composable(Routes.Home) {
            HomeScreen(
                onStartWorkout = { sessionId ->
                    navController.navigate(Routes.activeWorkout(sessionId))
                },
                onBrowsePlans = { navController.navigate(Routes.Plans) },
                onOpenHistory = { navController.navigate(Routes.History) },
                onOpenDashboard = { navController.navigate(Routes.Dashboard) },
                onOpenSettings = { navController.navigate(Routes.Settings) }
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.Plans) {
            PlansScreen(
                onOpenPlan = { planId -> navController.navigate(Routes.planDetail(planId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PlanDetail,
            arguments = listOf(navArgument("planId") { type = NavType.LongType })
        ) { entry ->
            val planId = entry.arguments?.getLong("planId") ?: return@composable
            PlanDetailScreen(
                planId = planId,
                onStartDay = { sessionId ->
                    navController.navigate(Routes.activeWorkout(sessionId)) {
                        popUpTo(Routes.Home)
                    }
                },
                onEdit = { id -> navController.navigate(Routes.planEdit(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PlanEdit,
            arguments = listOf(navArgument("planId") { type = NavType.LongType })
        ) { entry ->
            val planId = entry.arguments?.getLong("planId") ?: return@composable
            PlanEditScreen(
                planId = planId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ActiveWorkout,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            ActiveWorkoutScreen(
                sessionId = sessionId,
                onFinished = {
                    navController.popBackStack(Routes.Home, inclusive = false)
                }
            )
        }

        composable(Routes.History) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.sessionDetail(sessionId))
                }
            )
        }

        composable(
            route = Routes.SessionDetail,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            SessionDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Dashboard) {
            DashboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

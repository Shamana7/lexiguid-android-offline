package com.lexiguid.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lexiguid.app.ui.chat.ChatScreen
import com.lexiguid.app.ui.home.HomeScreen
import com.lexiguid.app.ui.modelmanager.ModelManagerScreen
import com.lexiguid.app.ui.onboarding.OnboardingScreen
import com.lexiguid.app.ui.profile.ProfileScreen
import com.lexiguid.app.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHAT = "chat/{conversationId}"
    const val NEW_CHAT = "chat/new"
    const val MODEL_MANAGER = "model_manager"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"

    fun chat(conversationId: String) = "chat/$conversationId"
}

@Composable
fun LexiGuidNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToChat = { conversationId ->
                    navController.navigate(Routes.chat(conversationId))
                },
                onNewChat = {
                    navController.navigate(Routes.NEW_CHAT)
                },
                onNavigateToModelManager = {
                    navController.navigate(Routes.MODEL_MANAGER)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToProfile = {
                    navController.navigate(Routes.PROFILE)
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId")
            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.NEW_CHAT) {
            ChatScreen(
                conversationId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MODEL_MANAGER) {
            ModelManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

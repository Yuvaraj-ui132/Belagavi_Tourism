package com.belagavi.tourism.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.belagavi.tourism.data.repository.PlacesRepository
import com.belagavi.tourism.ui.auth.AuthViewModel
import com.belagavi.tourism.ui.auth.ForgotPasswordView
import com.belagavi.tourism.ui.auth.LoginView
import com.belagavi.tourism.ui.auth.RegisterView
import com.belagavi.tourism.ui.auth.SplashView

@Composable
fun NavGraph(
    placesRepository: PlacesRepository,
    startDestination: String = Screen.Splash.route
) {
    val navController = rememberNavController()
    // Inject single shared AuthViewModel instance scoped to the navigation graph if needed,
    // or let hiltViewModel handle it at each screen.
    val authViewModel: AuthViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashView(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        
        composable(Screen.Login.route) {
            LoginView(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        
        composable(Screen.Register.route) {
            RegisterView(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordView(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        
        composable(Screen.DashboardHost.route) {
            DashboardHostView(
                navController = navController,
                authViewModel = authViewModel,
                placesRepository = placesRepository
            )
        }
        
        composable("place/{placeId}") { backStackEntry ->
            val placeIdStr = backStackEntry.arguments?.getString("placeId")
            val placeId = placeIdStr?.toIntOrNull() ?: 1
            com.belagavi.tourism.ui.dashboard.PlaceDetailView(
                placeId = placeId,
                placesRepository = placesRepository,
                authViewModel = authViewModel,
                navController = navController
            )
        }

        composable(Screen.PrivacyPolicy.route) {
            com.belagavi.tourism.ui.dashboard.PrivacyPolicyView(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "ai_chat?placeId={placeId}",
            arguments = listOf(
                androidx.navigation.navArgument("placeId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val placeIdStr = backStackEntry.arguments?.getString("placeId")
            val placeId = placeIdStr?.toIntOrNull()
            val aiChatViewModel: com.belagavi.tourism.ui.ai.AiChatViewModel = hiltViewModel()
            androidx.compose.runtime.LaunchedEffect(placeId) {
                if (placeId != null) {
                    aiChatViewModel.setContextualPlace(placeId)
                }
            }
            com.belagavi.tourism.ui.ai.AiChatView(
                viewModel = aiChatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlace = { pId ->
                    navController.navigate("place/$pId")
                }
            )
        }
    }
}

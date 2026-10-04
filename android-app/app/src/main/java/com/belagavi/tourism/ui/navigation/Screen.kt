package com.belagavi.tourism.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")
    object DashboardHost : Screen("dashboard_host")
    object PlaceDetail : Screen("place/{placeId}")
    object PrivacyPolicy : Screen("privacy_policy")
    object AiChat : Screen("ai_chat?placeId={placeId}") {
        fun createRoute(placeId: Int? = null): String {
            return if (placeId != null) "ai_chat?placeId=$placeId" else "ai_chat"
        }
    }
}

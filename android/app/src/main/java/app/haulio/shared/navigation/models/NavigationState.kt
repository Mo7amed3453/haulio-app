package app.haulio.shared.navigation.models

sealed class NavigationState {
    object Idle : NavigationState()
    data class Navigating(val route: RouteResponse) : NavigationState()
    object Rerouting : NavigationState()
    object Arrived : NavigationState()
}

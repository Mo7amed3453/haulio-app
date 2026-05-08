package app.haulio.shared.navigation.models

/**
 * Top-level navigation lifecycle state.
 * Local Android stub — mirrors the KMM shared sealed class until the shared module is wired in.
 */
sealed class NavigationState {
    object Idle : NavigationState()
    data class Navigating(val route: RouteResponse) : NavigationState()
    object Rerouting : NavigationState()
    object Arrived : NavigationState()
}

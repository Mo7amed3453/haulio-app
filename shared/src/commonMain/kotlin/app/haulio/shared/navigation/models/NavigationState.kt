package app.haulio.shared.navigation.models

/**
 * Top-level navigation lifecycle state.
 */
sealed interface NavigationState {
    /**
     * No active route.
     */
    data object Idle : NavigationState

    /**
     * Active route guidance is ongoing.
     */
    data class Navigating(val route: RouteResponse) : NavigationState

    /**
     * Navigation is recalculating due to route deviation.
     */
    data object Rerouting : NavigationState

    /**
     * Driver has arrived at destination.
     */
    data object Arrived : NavigationState
}

package app.haulio.shared.navigation

import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.models.NavigationState
import app.haulio.shared.navigation.models.NavigationStep
import app.haulio.shared.navigation.models.RouteDeviation
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

interface NavigationManager {
    val state: StateFlow<NavigationState>
    val routeDeviations: SharedFlow<RouteDeviation>
    val maneuverUpdates: SharedFlow<NavigationStep>
    val remainingEta: StateFlow<Duration>

    suspend fun startNavigation(origin: GeoPoint, target: GeoPoint)
    suspend fun stopNavigation()
    suspend fun updateLocation(location: GeoPoint, timestampSec: Long)
}

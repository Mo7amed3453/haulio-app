package app.haulio.shared.navigation

import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.models.NavigationState
import app.haulio.shared.navigation.models.NavigationStep
import app.haulio.shared.navigation.models.RouteDeviation
import app.haulio.shared.navigation.models.RouteResponse
import app.haulio.shared.navigation.tracking.ETACalculator
import app.haulio.shared.navigation.tracking.RouteTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.math.pow
import kotlinx.coroutines.delay

/**
 * Main navigation orchestrator for route fetching, tracking, and ETA.
 */
class NavigationManager(
    private val routeClient: ValhallaRouteClient,
    private val routeTracker: RouteTracker = RouteTracker(),
    private val etaCalculator: ETACalculator = ETACalculator(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private var activeRoute: RouteResponse? = null
    private var destination: GeoPoint? = null
    private var etaJob: Job? = null
    private var currentLocation: GeoPoint? = null
    private var currentTimestampSec: Long = 0

    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)

    /**
     * Current navigation state.
     */
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    /**
     * Current route deviation stream.
     */
    val routeDeviations: Flow<RouteDeviation> = routeTracker.deviationEvents

    /**
     * Current maneuver stream.
     */
    val maneuverUpdates: Flow<NavigationStep> = routeTracker.maneuverUpdates

    /**
     * Remaining ETA stream.
     */
    val remainingEta: StateFlow<Duration> = etaCalculator.remainingEta

    init {
        scope.launch {
            routeTracker.deviationEvents.collect {
                _state.value = NavigationState.Rerouting
                val location = mutex.withLock { currentLocation }
                val destinationSnapshot = mutex.withLock { destination }
                if (location != null && destinationSnapshot != null) {
                    val reroute = routeClient.getRoute(listOf(location, destinationSnapshot))
                    mutex.withLock { activeRoute = reroute }
                    routeTracker.setRoute(reroute)
                    _state.value = NavigationState.Navigating(reroute)
                } else {
                    _state.value = NavigationState.Idle
                }
            }
        }
    }

    /**
     * Starts navigation by fetching initial route.
     */
    suspend fun startNavigation(origin: GeoPoint, target: GeoPoint) {
        val route = routeClient.getRoute(listOf(origin, target))
        mutex.withLock {
            destination = target
            activeRoute = route
            currentLocation = origin
        }
        routeTracker.setRoute(route)
        _state.value = NavigationState.Navigating(route)
        startEtaLoop()
    }

    /**
     * Stops active navigation and clears state.
     */
    suspend fun stopNavigation() {
        mutex.withLock {
            destination = null
            activeRoute = null
            currentLocation = null
            currentTimestampSec = 0
        }
        etaJob?.cancel()
        etaJob = null
        _state.value = NavigationState.Idle
    }

    /**
     * Feeds current GPS into trackers.
     */
    suspend fun updateLocation(location: GeoPoint, timestampSec: Long) {
        mutex.withLock {
            currentLocation = location
            currentTimestampSec = timestampSec
        }
        routeTracker.updateLocation(location, timestampSec)
        etaCalculator.updateLocation(location, timestampSec)

        val target = mutex.withLock { destination }
        if (target != null) {
            val arrivalMiles = distanceMiles(location, target)
            if (arrivalMiles <= 0.05) {
                _state.value = NavigationState.Arrived
            }
        }
    }

    private fun startEtaLoop() {
        etaJob?.cancel()
        etaJob = scope.launch {
            while (isActive) {
                val location = mutex.withLock { currentLocation }
                val nowSec = mutex.withLock { currentTimestampSec }
                if (location != null && nowSec > 0) {
                    val remaining = routeTracker.remainingDistanceMiles(location)
                    etaCalculator.recalculate(remaining, nowSec)
                }
                delay(30_000)
            }
        }
    }

    private fun distanceMiles(a: GeoPoint, b: GeoPoint): Double {
        val r = 3958.7613
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val x = kotlin.math.sin(dLat / 2).pow(2) +
            kotlin.math.sin(dLon / 2).pow(2) * kotlin.math.cos(lat1) * kotlin.math.cos(lat2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(x), kotlin.math.sqrt(1 - x))
        return r * c
    }
}

package app.haulio.android.services.navigation

import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.models.ManeuverType
import app.haulio.shared.navigation.models.NavigationState
import app.haulio.shared.navigation.models.NavigationStep
import app.haulio.shared.navigation.models.RouteDeviation
import app.haulio.shared.navigation.models.RouteResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// ---------------------------------------------------------------------------
// Bridge interfaces — mirror the KMM NavigationManager surface so the Android
// layer can compile and run without a real KMM binary (mock implementations
// below) and swap in the real KMM class at DI time.
// ---------------------------------------------------------------------------

/**
 * Android-side contract for the shared NavigationManager.
 * Implementations must be coroutine-safe and lifecycle-aware.
 */
interface INavigationManager {
    val state: StateFlow<NavigationState>
    val routeDeviations: SharedFlow<RouteDeviation>
    val maneuverUpdates: SharedFlow<NavigationStep>
    val remainingEta: StateFlow<Duration>

    suspend fun startNavigation(origin: GeoPoint, target: GeoPoint)
    suspend fun stopNavigation()
    suspend fun updateLocation(location: GeoPoint, timestampSec: Long)
}

/**
 * Data model carrying all info needed to render an exit guide sign.
 *
 * @property exitNumber highway exit label (e.g. "42B").
 * @property destination primary destination label.
 * @property services amenity icons available at this exit.
 * @property distanceMiles remaining distance to the exit.
 */
data class ExitInfo(
    val exitNumber: String,
    val destination: String,
    val services: List<ExitService>,
    val distanceMiles: Double,
)

/** Service amenities typically advertised on highway exit signs. */
enum class ExitService { GAS, FOOD, LODGING }

/**
 * Reason the driver provided when prompted after a route deviation.
 */
enum class DeviationReason { ROAD_CLOSED, ACCIDENT, OTHER }

// ---------------------------------------------------------------------------
// KMM real-implementation adapter (placeholder — uncomment once KMM shared module is wired in)
// ---------------------------------------------------------------------------
// class KmmNavigationManagerBridge(
//     private val delegate: app.haulio.shared.navigation.NavigationManager,
// ) : INavigationManager { ... }

// ---------------------------------------------------------------------------
// Mock implementation — used for standalone Android development / preview
// ---------------------------------------------------------------------------

/**
 * Stand-alone mock that emits a predefined route without requiring the KMM
 * module or a live Valhalla server.
 */
class MockNavigationManager : INavigationManager {

    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)
    override val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _routeDeviations = MutableSharedFlow<RouteDeviation>(extraBufferCapacity = 8)
    override val routeDeviations: SharedFlow<RouteDeviation> = _routeDeviations.asSharedFlow()

    private val _maneuverUpdates = MutableSharedFlow<NavigationStep>(extraBufferCapacity = 8)
    override val maneuverUpdates: SharedFlow<NavigationStep> = _maneuverUpdates.asSharedFlow()

    private val _remainingEta = MutableStateFlow(22.minutes)
    override val remainingEta: StateFlow<Duration> = _remainingEta.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mockSteps = listOf(
        NavigationStep(ManeuverType.EXIT_RIGHT, "Take exit 42B toward Route 1 North", 1.8, "Exit 42B / Route 1 North", 0, 10),
        NavigationStep(ManeuverType.RIGHT,      "Turn right onto Route 1 North",       0.3, "Route 1 North",           10, 20),
        NavigationStep(ManeuverType.CONTINUE,   "Continue on Route 1 North",           5.2, "Route 1 North",           20, 50),
        NavigationStep(ManeuverType.LEFT,       "Turn left onto Main Street",          0.5, "Main Street",             50, 60),
        NavigationStep(ManeuverType.DESTINATION,"Arrive at destination",               0.0, null,                      60, 60),
    )

    override suspend fun startNavigation(origin: GeoPoint, target: GeoPoint) {
        val mockRoute = RouteResponse(
            polyline = "",
            decodedShape = listOf(origin, target),
            steps = mockSteps,
            totalDistanceMiles = 7.8,
        )
        _state.value = NavigationState.Navigating(mockRoute)
        simulateProgress()
    }

    override suspend fun stopNavigation() {
        _state.value = NavigationState.Idle
    }

    override suspend fun updateLocation(location: GeoPoint, timestampSec: Long) { /* no-op in mock */ }

    private fun simulateProgress() {
        scope.launch {
            mockSteps.forEach { step ->
                delay(4_000)
                _maneuverUpdates.emit(step)
            }
            delay(2_000)
            // emit a fake deviation so the dialog can be tested
            _routeDeviations.emit(
                RouteDeviation(
                    location = GeoPoint(37.7749, -122.4194),
                    distanceFromRouteMeters = 65.0,
                    offRouteSeconds = 12,
                )
            )
        }
    }
}

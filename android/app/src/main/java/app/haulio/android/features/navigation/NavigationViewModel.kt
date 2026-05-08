package app.haulio.android.features.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.location.LocationPoint
import app.haulio.android.services.location.ObserveLocationUseCase
import app.haulio.android.services.navigation.DeviationReason
import app.haulio.android.services.navigation.ExitInfo
import app.haulio.android.services.navigation.ExitService
import app.haulio.android.services.navigation.INavigationManager
import app.haulio.android.services.navigation.VoiceInstructionService
import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.models.ManeuverType
import app.haulio.shared.navigation.models.NavigationState
import app.haulio.shared.navigation.models.NavigationStep
import app.haulio.shared.navigation.models.RouteDeviation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import kotlin.time.Duration

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

/**
 * Snapshot of everything the NavigationScreen needs to render.
 */
data class NavigationUiState(
    /** Step currently being followed. */
    val currentStep: NavigationStep? = null,
    /** Miles remaining to the current maneuver. */
    val distanceToStepMiles: Double = 0.0,
    /** Remaining ETA from KMM ETACalculator. */
    val remainingEta: Duration = Duration.ZERO,
    /** Current speed derived from successive GPS updates. */
    val currentSpeedMph: Float = 0f,
    /** Non-null while the exit guide sign should be visible (within 2 mi of exit). */
    val activeExit: ExitInfo? = null,
    /** Whether the route-deviation dialog is visible. */
    val showDeviationDialog: Boolean = false,
    /** The most recent deviation event that triggered the dialog. */
    val pendingDeviation: RouteDeviation? = null,
    /** Decoded route geometry for MapLibre polyline. */
    val routePoints: List<LatLng> = emptyList(),
    /** Latest GPS fix for camera tracking. */
    val userLocation: LocationPoint? = null,
    /** True while navigation is active. */
    val isNavigating: Boolean = false,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class NavigationViewModel(
    private val navigationManager: INavigationManager,
    private val observeLocationUseCase: ObserveLocationUseCase,
    private val voiceInstructionService: VoiceInstructionService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NavigationUiState())
    val uiState: StateFlow<NavigationUiState> = _uiState.asStateFlow()

    // Speed calculation state
    private var prevLocation: LocationPoint? = null
    private var prevTimestampMs: Long = 0L

    init {
        observeNavManagerState()
        observeManeuvers()
        observeDeviations()
        observeEta()
        observeLocation()
    }

    // ------------------------------------------------------------------
    // Public actions
    // ------------------------------------------------------------------

    /** Begin navigation from [origin] to [destination]. */
    fun startNavigation(origin: GeoPoint, destination: GeoPoint) {
        viewModelScope.launch {
            navigationManager.startNavigation(origin, destination)
            _uiState.update { it.copy(isNavigating = true) }
        }
    }

    /** Stop navigation and clear state. */
    fun stopNavigation() {
        viewModelScope.launch {
            navigationManager.stopNavigation()
            _uiState.update { NavigationUiState() }
        }
    }

    /** Called when the driver selects a reason in the deviation dialog. */
    fun onDeviationReasonSelected(reason: DeviationReason) {
        // Persist reason locally (placeholder for future crowdsource upload)
        persistDeviationReason(reason)
        _uiState.update { it.copy(showDeviationDialog = false, pendingDeviation = null) }
        // Reroute: NavigationManager already auto-reroutes when it detects deviation,
        // but if the bridge suppresses auto-reroute we can re-trigger here.
        val deviation = _uiState.value.pendingDeviation
        if (deviation != null) {
            viewModelScope.launch {
                val loc = deviation.location
                val snap = _uiState.value.userLocation
                if (snap != null) {
                    navigationManager.updateLocation(
                        GeoPoint(snap.latitude, snap.longitude),
                        System.currentTimeMillis() / 1000,
                    )
                }
            }
        }
    }

    /** Called when the driver dismisses the deviation dialog without a reason. */
    fun onDeviationDismissed() {
        _uiState.update { it.copy(showDeviationDialog = false, pendingDeviation = null) }
    }

    // ------------------------------------------------------------------
    // Private collectors
    // ------------------------------------------------------------------

    private fun observeNavManagerState() {
        viewModelScope.launch {
            navigationManager.state.collect { state ->
                when (state) {
                    is NavigationState.Navigating -> {
                        val points = state.route.decodedShape.map { LatLng(it.lat, it.lon) }
                        _uiState.update { it.copy(routePoints = points, isNavigating = true) }
                    }
                    NavigationState.Idle, NavigationState.Arrived -> {
                        _uiState.update { it.copy(isNavigating = false) }
                    }
                    NavigationState.Rerouting -> { /* spinner could be shown here */ }
                }
            }
        }
    }

    private fun observeManeuvers() {
        viewModelScope.launch {
            navigationManager.maneuverUpdates.collect { step ->
                val exitInfo = step.toExitInfo()
                val distanceMiles = step.distanceMiles

                _uiState.update { current ->
                    current.copy(
                        currentStep = step,
                        distanceToStepMiles = distanceMiles,
                        // Show exit sign only when within 2 miles of an exit maneuver
                        activeExit = if (exitInfo != null && distanceMiles <= 2.0) exitInfo else null,
                    )
                }

                // Voice announcement
                voiceInstructionService.onStepProgress(step, distanceMiles)
            }
        }
    }

    private fun observeDeviations() {
        viewModelScope.launch {
            navigationManager.routeDeviations.collect { deviation ->
                _uiState.update {
                    it.copy(showDeviationDialog = true, pendingDeviation = deviation)
                }
            }
        }
    }

    private fun observeEta() {
        viewModelScope.launch {
            navigationManager.remainingEta.collect { eta ->
                _uiState.update { it.copy(remainingEta = eta) }
            }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            observeLocationUseCase().collect { location ->
                val now = System.currentTimeMillis()
                val speed = computeSpeed(location, now)
                prevLocation = location
                prevTimestampMs = now

                _uiState.update { it.copy(userLocation = location, currentSpeedMph = speed) }

                // Feed GPS into KMM
                if (_uiState.value.isNavigating) {
                    navigationManager.updateLocation(
                        GeoPoint(location.latitude, location.longitude),
                        now / 1000,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun computeSpeed(current: LocationPoint, nowMs: Long): Float {
        val prev = prevLocation ?: return 0f
        val dtSec = (nowMs - prevTimestampMs) / 1000.0
        if (dtSec <= 0.0) return 0f

        val dLat = current.latitude - prev.latitude
        val dLon = current.longitude - prev.longitude
        // Rough metres per degree
        val distMeters = kotlin.math.sqrt(
            (dLat * 111_000).pow2() + (dLon * 85_000).pow2()
        )
        val mph = (distMeters / dtSec) * 2.23694
        return mph.toFloat().coerceAtLeast(0f)
    }

    private fun Double.pow2(): Double = this * this

    /** Parses a NavigationStep into ExitInfo if it is an exit maneuver. */
    private fun NavigationStep.toExitInfo(): ExitInfo? {
        if (maneuverType != ManeuverType.EXIT_RIGHT && maneuverType != ManeuverType.EXIT_LEFT) {
            return null
        }
        val raw = streetName ?: instruction
        // Try to extract "Exit 42B / Route 1 North" → exitNumber="42B", dest="Route 1 North"
        val exitRegex = Regex("""[Ee]xit\s+(\S+)\s*/?\s*(.*)""")
        val match = exitRegex.find(raw)
        val exitNumber = match?.groupValues?.getOrNull(1) ?: raw
        val destination = match?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() } ?: raw

        return ExitInfo(
            exitNumber = exitNumber,
            destination = destination,
            services = listOf(ExitService.GAS, ExitService.FOOD, ExitService.LODGING),
            distanceMiles = distanceMiles,
        )
    }

    private fun persistDeviationReason(reason: DeviationReason) {
        // TODO: write to local Room/DataStore for future crowdsource feature
    }

    override fun onCleared() {
        super.onCleared()
        voiceInstructionService.release()
    }
}

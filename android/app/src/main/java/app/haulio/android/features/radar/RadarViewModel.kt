package app.haulio.android.features.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.radar.IRadarProximityEngine
import app.haulio.android.services.radar.IRadarRepository
import app.haulio.shared.radar.LegalGeofence
import app.haulio.shared.radar.models.RadarAlertEvent
import app.haulio.shared.radar.models.SpeedCamera
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the radar detector feature.
 *
 * @property cameras          Current list of speed cameras for the viewport.
 * @property isRadarVisible   Whether the radar overlay (pins + alerts) is active.
 * @property pendingAlert     Most recent [RadarAlertEvent]; null when no active alert.
 * @property isLegallyBanned  True when the driver is in VA or DC — all radar features disabled.
 * @property bannedJurisdiction Human-readable name of the banned jurisdiction, or null.
 * @property isLoading        True while refreshing camera data.
 * @property submitSuccess    Non-null transient message after a successful camera submission.
 * @property submitError      Non-null transient message after a failed submission.
 */
data class RadarUiState(
    val cameras: List<SpeedCamera>   = emptyList(),
    val isRadarVisible: Boolean      = false,
    val pendingAlert: RadarAlertEvent? = null,
    val isLegallyBanned: Boolean     = false,
    val bannedJurisdiction: String?  = null,
    val isLoading: Boolean           = false,
    val submitSuccess: String?       = null,
    val submitError: String?         = null,
)

class RadarViewModel(
    private val repository: IRadarRepository,
    private val proximityEngine: IRadarProximityEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RadarUiState())
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    init {
        // Collect live camera list
        viewModelScope.launch {
            repository.cameras.collect { list ->
                _uiState.update { it.copy(cameras = list) }
            }
        }

        // Collect proximity alerts
        viewModelScope.launch {
            proximityEngine.alerts.collect { event ->
                // Defense-in-depth: check legal status at time of alert
                if (!_uiState.value.isLegallyBanned) {
                    _uiState.update { it.copy(pendingAlert = event) }
                }
            }
        }
    }

    /**
     * Called on every GPS location update from the Android location service.
     * Checks [LegalGeofence] and force-disables the feature if in a banned area.
     *
     * @param lat Current latitude.
     * @param lng Current longitude.
     */
    fun onLocationUpdate(lat: Double, lng: Double) {
        val banned = LegalGeofence.isRadarBanned(lat, lng)
        if (banned) {
            val jurisdiction = detectJurisdiction(lat, lng)
            _uiState.update { state ->
                state.copy(
                    isLegallyBanned    = true,
                    bannedJurisdiction = jurisdiction,
                    isRadarVisible     = false,
                    pendingAlert       = null,
                )
            }
        } else if (_uiState.value.isLegallyBanned) {
            // Driver has left the banned area
            _uiState.update { it.copy(isLegallyBanned = false, bannedJurisdiction = null) }
        }
    }

    /** Toggles the radar overlay on/off (no-op if legally banned). */
    fun toggleRadarOverlay() {
        if (_uiState.value.isLegallyBanned) return
        val newVisible = !_uiState.value.isRadarVisible
        _uiState.update { it.copy(isRadarVisible = newVisible) }
        if (newVisible) loadCameras()
    }

    /** Refreshes camera data for the given bounding box (defaults to SF metro). */
    fun loadCameras(
        minLat: Double = 37.70,
        minLng: Double = -122.52,
        maxLat: Double = 37.84,
        maxLng: Double = -122.35,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.refresh(minLat, minLng, maxLat, maxLng)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Submits a new crowd-reported camera at the long-pressed location. */
    fun submitCamera(lat: Double, lng: Double, speedMph: Int?) {
        viewModelScope.launch {
            val result = repository.submitCamera(lat, lng, speedMph)
            if (result.isSuccess) {
                _uiState.update { it.copy(submitSuccess = "Speed camera reported — thank you!") }
            } else {
                _uiState.update { it.copy(submitError = "Submission failed, please try again.") }
            }
        }
    }

    /** Dismisses the current alert banner. */
    fun dismissAlert() {
        _uiState.update { it.copy(pendingAlert = null) }
    }

    fun clearSubmitFeedback() {
        _uiState.update { it.copy(submitSuccess = null, submitError = null) }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun detectJurisdiction(lat: Double, lng: Double): String {
        // DC bounding box
        if (lat in 38.791..38.995 && lng in -77.120..-76.909) return "DC"
        // Virginia coarse check
        if (lat in 36.54..39.47 && lng in -83.68..-75.24) return "VA"
        return "this jurisdiction"
    }
}

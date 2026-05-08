package app.haulio.android.features.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.IDeliveryLogger
import app.haulio.android.services.location.LocationPoint
import app.haulio.android.services.location.ObserveLocationUseCase
import app.haulio.shared.navigation.models.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

/**
 * All state needed to render [ArrivalScreen].
 *
 * @property destination        The geocoded destination set by the address search flow.
 * @property isNearDestination  True when the driver is within [ARRIVAL_RADIUS_METERS] of [destination].
 * @property showUnitSelector   True when a unit dialog should be shown.
 * @property availableUnits     Known unit/apt labels parsed from the address (may be empty).
 * @property selectedUnit       The unit/apt number the driver confirmed (null until confirmed).
 * @property isDelivered        True after the driver tapped "Mark as Delivered".
 * @property photoUri           URI of the proof-of-delivery photo (null if not taken).
 * @property isLoggingDelivery  True while the Supabase write is in-flight.
 * @property errorMessage       Non-null if logging failed.
 * @property distanceMeters     Real-time distance from driver to destination.
 */
data class ArrivalUiState(
    val destination: AddressSuggestion? = null,
    val isNearDestination: Boolean = false,
    val showUnitSelector: Boolean = false,
    val availableUnits: List<String> = emptyList(),
    val selectedUnit: String? = null,
    val isDelivered: Boolean = false,
    val photoUri: String? = null,
    val isLoggingDelivery: Boolean = false,
    val errorMessage: String? = null,
    val distanceMeters: Double = Double.MAX_VALUE,
)

private const val ARRIVAL_RADIUS_METERS = 50.0

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class ArrivalViewModel(
    private val observeLocationUseCase: ObserveLocationUseCase,
    private val deliveryLogger: IDeliveryLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArrivalUiState())
    val uiState: StateFlow<ArrivalUiState> = _uiState.asStateFlow()

    init {
        observeLocation()
    }

    // ------------------------------------------------------------------
    // Public actions
    // ------------------------------------------------------------------

    /**
     * Set the delivery destination once the address search flow resolves a geocoded suggestion.
     */
    fun setDestination(suggestion: AddressSuggestion) {
        val units = parseUnits(suggestion.parsed.unitNumber)
        _uiState.update {
            it.copy(
                destination    = suggestion,
                availableUnits = units,
            )
        }
    }

    /** Driver confirmed which unit number to use. */
    fun onUnitSelected(unit: String) {
        _uiState.update { it.copy(selectedUnit = unit, showUnitSelector = false) }
    }

    /** Driver dismissed the unit selector without selecting. */
    fun onUnitSelectorDismissed() {
        _uiState.update { it.copy(showUnitSelector = false) }
    }

    /** Driver took a proof-of-delivery photo; [uri] is the local content URI. */
    fun onPhotoCaptured(uri: String) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    /**
     * Driver tapped "Mark as Delivered".
     * Logs the delivery to Supabase and marks [isDelivered] = true.
     */
    fun onMarkDelivered() {
        val snap = _uiState.value
        val dest = snap.destination ?: return

        _uiState.update { it.copy(isLoggingDelivery = true) }

        viewModelScope.launch {
            val result = runCatching {
                deliveryLogger.logDelivery(
                    address     = dest.parsed.formatted,
                    coordinates = dest.coordinates,
                    unit        = snap.selectedUnit,
                    photoUri    = snap.photoUri,
                )
            }
            _uiState.update {
                it.copy(
                    isDelivered    = result.isSuccess,
                    isLoggingDelivery = false,
                    errorMessage   = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ------------------------------------------------------------------
    // Private
    // ------------------------------------------------------------------

    private fun observeLocation() {
        viewModelScope.launch {
            observeLocationUseCase().collect { loc ->
                val dest = _uiState.value.destination ?: return@collect
                val dist = haversineMeters(loc, dest.coordinates)
                val near = dist <= ARRIVAL_RADIUS_METERS

                val wasNear = _uiState.value.isNearDestination
                val hasUnit = _uiState.value.availableUnits.isNotEmpty()

                _uiState.update { state ->
                    state.copy(
                        distanceMeters    = dist,
                        isNearDestination = near,
                        // Show unit selector once, when first entering the radius and address has units
                        showUnitSelector  = if (!wasNear && near && hasUnit && !state.isDelivered)
                            true
                        else
                            state.showUnitSelector,
                    )
                }
            }
        }
    }

    private fun haversineMeters(loc: LocationPoint, dest: GeoPoint): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(dest.lat - loc.latitude)
        val dLon = Math.toRadians(dest.lon - loc.longitude)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(loc.latitude)) *
            kotlin.math.cos(Math.toRadians(dest.lat)) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return R * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    private fun parseUnits(unitNumber: String?): List<String> {
        if (unitNumber.isNullOrBlank()) return emptyList()
        // If the field contains a range like "1A-10Z" we can't expand it; just return as-is
        return listOf(unitNumber)
    }
}

package app.haulio.android.features.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.fuel.FuelGrade
import app.haulio.android.services.fuel.FuelPrice
import app.haulio.android.services.fuel.FuelStation
import app.haulio.android.services.fuel.IFuelDataAggregator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FuelUiState(
    val stations: List<FuelStation>   = emptyList(),
    val regionalAverage: FuelPrice?   = null,
    val isFuelVisible: Boolean        = false,
    val isLoading: Boolean            = false,
    val submitSuccess: String?        = null,
    val submitError: String?          = null,
)

class FuelViewModel(
    private val aggregator: IFuelDataAggregator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FuelUiState())
    val uiState: StateFlow<FuelUiState> = _uiState.asStateFlow()

    init {
        // Collect live station updates
        viewModelScope.launch {
            aggregator.stations.collect { list ->
                _uiState.update { it.copy(stations = list) }
            }
        }

        // Collect regional average
        viewModelScope.launch {
            aggregator.regionalAverage.collect { avg ->
                _uiState.update { it.copy(regionalAverage = avg) }
            }
        }
    }

    /** Toggles the fuel station overlay on the map and opens/closes the bottom sheet. */
    fun toggleFuelOverlay() {
        val newVisible = !_uiState.value.isFuelVisible
        _uiState.update { it.copy(isFuelVisible = newVisible) }
        if (newVisible) loadStations()
    }

    /** Manually trigger a data refresh for the given bounding box. */
    fun loadStations(
        minLat: Double = 37.70,
        minLng: Double = -122.52,
        maxLat: Double = 37.84,
        maxLng: Double = -122.35,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            aggregator.refresh(minLat, minLng, maxLat, maxLng)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Submit a driver-reported price for a station. */
    fun submitPrice(stationId: String, price: Double, grade: FuelGrade) {
        viewModelScope.launch {
            val result = aggregator.submit(stationId, price, grade)
            if (result.isSuccess) {
                _uiState.update { it.copy(submitSuccess = "Price submitted — thank you!") }
            } else {
                _uiState.update { it.copy(submitError = "Submission failed, please try again.") }
            }
        }
    }

    fun clearSubmitFeedback() {
        _uiState.update { it.copy(submitSuccess = null, submitError = null) }
    }
}

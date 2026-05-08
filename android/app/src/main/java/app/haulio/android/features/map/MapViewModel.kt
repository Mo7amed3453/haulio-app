package app.haulio.android.features.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.location.LocationPoint
import app.haulio.android.services.location.ObserveLocationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val searchQuery: String = "",
    val userLocation: LocationPoint? = null
)

class MapViewModel(
    private val observeLocationUseCase: ObserveLocationUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeLocationUseCase().collect { location ->
                _uiState.update { current ->
                    current.copy(userLocation = location)
                }
            }
        }
    }

    fun onSearchChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFuelTap() {
        // Placeholder foundation action hook.
    }
}

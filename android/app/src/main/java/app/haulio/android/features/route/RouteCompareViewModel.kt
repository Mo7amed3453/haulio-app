package app.haulio.android.features.route

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.prefs.AppPrefKeys
import app.haulio.android.services.traffic.CongestionLevel
import app.haulio.android.services.traffic.IRouteClient
import app.haulio.android.services.traffic.RouteOption
import app.haulio.shared.navigation.models.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouteCompareUiState(
    val routes: List<RouteOption> = emptyList(),
    val selectedRouteId: String? = null,
    val isLoading: Boolean = false,
    val countdownSeconds: Int = 8,
    val isCountingDown: Boolean = false,
    val alwaysShowAlternatives: Boolean = true,
    val errorMessage: String? = null,
)

class RouteCompareViewModel(
    private val routeClient: IRouteClient,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteCompareUiState())
    val uiState: StateFlow<RouteCompareUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            val showAlways = dataStore.data
                .map { prefs -> prefs[AppPrefKeys.ALWAYS_SHOW_ALTERNATIVES] ?: true }
                .first()
            _uiState.update { it.copy(alwaysShowAlternatives = showAlways) }
        }
    }

    fun fetchAlternatives(origin: GeoPoint, destination: GeoPoint) {
        if (_uiState.value.isLoading || _uiState.value.routes.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            routeClient.fetchAlternatives(
                origin      = origin,
                destination = destination,
                alts        = 3,
                costing     = "auto",
            ).fold(
                onSuccess = { routes ->
                    val fastest = routes.minByOrNull { it.etaMinutes }?.id
                    _uiState.update {
                        it.copy(
                            routes           = routes,
                            isLoading        = false,
                            selectedRouteId  = fastest,
                            isCountingDown   = true,
                            countdownSeconds = 8,
                        )
                    }
                    startCountdown(fastest)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading    = false,
                            errorMessage = err.message ?: "Failed to fetch routes",
                        )
                    }
                },
            )
        }
    }

    fun selectRoute(routeId: String) {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(selectedRouteId = routeId, isCountingDown = false, countdownSeconds = 0)
        }
    }

    fun toggleAlwaysShow(enabled: Boolean) {
        _uiState.update { it.copy(alwaysShowAlternatives = enabled) }
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[AppPrefKeys.ALWAYS_SHOW_ALTERNATIVES] = enabled }
        }
    }

    private fun startCountdown(fastestId: String?) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = 8
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _uiState.update { it.copy(countdownSeconds = remaining) }
            }
            _uiState.update {
                it.copy(
                    selectedRouteId = fastestId ?: it.routes.firstOrNull()?.id,
                    isCountingDown  = false,
                )
            }
        }
    }
}

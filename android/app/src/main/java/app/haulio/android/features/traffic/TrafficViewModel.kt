package app.haulio.android.features.traffic

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.prefs.AppPrefKeys
import app.haulio.android.services.traffic.ITrafficAggregator
import app.haulio.android.services.traffic.IRerouteListener
import app.haulio.android.services.traffic.RerouteSuggestion
import app.haulio.android.services.traffic.TrafficEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrafficUiState(
    val events: List<TrafficEvent>         = emptyList(),
    val isTrafficVisible: Boolean          = true,
    val rerouteSuggestion: RerouteSuggestion? = null,
    val rerouteCountdown: Int              = 10,
    val isRerouteActive: Boolean           = false,
)

class TrafficViewModel(
    private val trafficAggregator: ITrafficAggregator,
    private val rerouteListener: IRerouteListener,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrafficUiState())
    val uiState: StateFlow<TrafficUiState> = _uiState.asStateFlow()

    private var rerouteCountdownJob: Job? = null

    init {
        // Load persisted overlay visibility
        viewModelScope.launch {
            val visible = dataStore.data
                .map { prefs -> prefs[AppPrefKeys.TRAFFIC_OVERLAY_VISIBLE] ?: true }
                .first()
            _uiState.update { it.copy(isTrafficVisible = visible) }
        }

        // Collect live traffic events
        viewModelScope.launch {
            trafficAggregator.events.collect { events ->
                _uiState.update { it.copy(events = events) }
            }
        }

        // Collect reroute suggestions
        viewModelScope.launch {
            rerouteListener.suggestions.collect { suggestion ->
                rerouteCountdownJob?.cancel()
                _uiState.update {
                    it.copy(
                        rerouteSuggestion = suggestion,
                        rerouteCountdown  = 10,
                        isRerouteActive   = true,
                    )
                }
                startRerouteCountdown()
            }
        }

        // Initial data refresh
        viewModelScope.launch { trafficAggregator.refresh() }
    }

    fun toggleTrafficOverlay() {
        val newVisible = !_uiState.value.isTrafficVisible
        _uiState.update { it.copy(isTrafficVisible = newVisible) }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[AppPrefKeys.TRAFFIC_OVERLAY_VISIBLE] = newVisible
            }
        }
    }

    /** Accept the reroute suggestion immediately. */
    fun switchToFasterRoute() {
        rerouteCountdownJob?.cancel()
        _uiState.update { it.copy(rerouteSuggestion = null, isRerouteActive = false) }
        // TODO: trigger actual navigation reroute via KMM bridge
    }

    /** Dismiss the reroute banner without switching. */
    fun dismissReroute() {
        rerouteCountdownJob?.cancel()
        _uiState.update { it.copy(rerouteSuggestion = null, isRerouteActive = false) }
    }

    private fun startRerouteCountdown() {
        rerouteCountdownJob = viewModelScope.launch {
            var remaining = 10
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _uiState.update { it.copy(rerouteCountdown = remaining) }
            }
            // Auto-switch after 10 s of no interaction
            _uiState.update { it.copy(rerouteSuggestion = null, isRerouteActive = false) }
        }
    }
}

package app.haulio.android.features.crime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.crime.ICrimeRepository
import app.haulio.android.services.prefs.AppPrefKeys
import app.haulio.shared.crime.HighRiskAlertEvent
import app.haulio.shared.crime.models.CrimeGridCell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long to suppress re-showing an alert for the same cell (1 hour in ms). */
private const val ALERT_SUPPRESS_DURATION_MS = 60 * 60 * 1000L

data class CrimeUiState(
    val cells: List<CrimeGridCell>        = emptyList(),
    val isCrimeVisible: Boolean           = false,
    val pendingAlert: HighRiskAlertEvent? = null,
    val isLoading: Boolean                = false,
)

class CrimeViewModel(
    private val repository: ICrimeRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrimeUiState())
    val uiState: StateFlow<CrimeUiState> = _uiState.asStateFlow()

    init {
        // Collect alert events from the repository and apply 1-hour cell suppression
        viewModelScope.launch {
            repository.alerts(emptyFlow()).collect { event ->
                if (event != null) {
                    showAlertIfNotSuppressed(event)
                }
            }
        }

        // Load initial data for the default SF bbox
        loadGrid()
    }

    /** Toggles the crime heatmap overlay on/off. */
    fun toggleCrimeOverlay() {
        val newVisible = !_uiState.value.isCrimeVisible
        _uiState.update { it.copy(isCrimeVisible = newVisible) }
        if (newVisible) loadGrid()
    }

    /**
     * Fetches crime grid cells for the given bounding box.
     * Defaults to the San Francisco metro area.
     */
    fun loadGrid(
        south: Double = 37.70,
        west: Double  = -122.52,
        north: Double = 37.84,
        east: Double  = -122.35,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.gridForBbox(south, west, north, east).collect { cells ->
                _uiState.update { it.copy(cells = cells, isLoading = false) }
            }
        }
    }

    /** Dismisses the current high-risk alert banner. */
    fun dismissAlert() {
        _uiState.update { it.copy(pendingAlert = null) }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Shows [event] only if the same cell was not alerted within the last hour.
     * Persists the cell ID + timestamp in DataStore when showing.
     */
    private suspend fun showAlertIfNotSuppressed(event: HighRiskAlertEvent) {
        val prefs = dataStore.data.first()
        val lastCellId = prefs[AppPrefKeys.CRIME_LAST_ALERT_CELL_ID]
        val lastTsMs   = prefs[AppPrefKeys.CRIME_LAST_ALERT_TS_MS] ?: 0L

        val isSuppressed = lastCellId == event.cellId &&
            System.currentTimeMillis() - lastTsMs < ALERT_SUPPRESS_DURATION_MS

        if (isSuppressed) return

        // Persist so we suppress for the next hour
        dataStore.edit { prefs ->
            prefs[AppPrefKeys.CRIME_LAST_ALERT_CELL_ID] = event.cellId
            prefs[AppPrefKeys.CRIME_LAST_ALERT_TS_MS]   = System.currentTimeMillis()
        }

        _uiState.update { it.copy(pendingAlert = event) }
    }
}

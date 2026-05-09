package app.haulio.android.features.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.traffic.IIncidentRepository
import app.haulio.android.services.traffic.IncidentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IncidentReportUiState(
    val isSubmitting: Boolean     = false,
    val successMessage: String?   = null,
    val errorMessage: String?     = null,
    /** Expiry timestamp (ms) of the most recent user-reported incident. */
    val lastReportExpiry: Long?   = null,
)

class IncidentReportViewModel(
    private val incidentRepository: IIncidentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncidentReportUiState())
    val uiState: StateFlow<IncidentReportUiState> = _uiState.asStateFlow()

    fun reportIncident(type: IncidentType, lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            incidentRepository.report(type, lat, lon).fold(
                onSuccess = {
                    val expiresAt = System.currentTimeMillis() + 3_600_000L
                    _uiState.update {
                        it.copy(
                            isSubmitting     = false,
                            successMessage   = "Reported. Visible to nearby drivers.",
                            lastReportExpiry = expiresAt,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting  = false,
                            errorMessage  = err.message ?: "Failed to submit report",
                        )
                    }
                },
            )
        }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

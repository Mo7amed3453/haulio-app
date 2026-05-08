package app.haulio.android.features.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.IAddressParser
import app.haulio.android.services.address.IDeliveryLogger
import app.haulio.android.services.address.IGeocodingManager
import app.haulio.shared.navigation.models.GeoPoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

/**
 * Complete UI state for [AddressSearchScreen].
 *
 * @property query              Current text-field value.
 * @property suggestions        Up to 5 geocoded suggestions for the dropdown.
 * @property selectedSuggestion The suggestion the user tapped (drives navigation).
 * @property isLoading          True while debounce + geocode is in-flight.
 * @property errorMessage       Non-null when a geocoding error should be surfaced.
 * @property originalQuery      The raw query before the user possibly edited a correction.
 */
data class AddressSearchUiState(
    val query: String = "",
    val suggestions: List<AddressSuggestion> = emptyList(),
    val selectedSuggestion: AddressSuggestion? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val originalQuery: String = "",
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@OptIn(FlowPreview::class)
class AddressSearchViewModel(
    private val addressParser: IAddressParser,
    private val geocodingManager: IGeocodingManager,
    private val deliveryLogger: IDeliveryLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddressSearchUiState())
    val uiState: StateFlow<AddressSearchUiState> = _uiState.asStateFlow()

    /** Internal query channel — debounced before hitting geocoder. */
    private val _queryFlow = MutableStateFlow("")

    init {
        observeQueryDebounced()
    }

    // ------------------------------------------------------------------
    // Public actions
    // ------------------------------------------------------------------

    /**
     * Called on every keystroke from [AddressSearchBar].
     */
    fun onQueryChanged(raw: String) {
        _uiState.update { it.copy(query = raw, selectedSuggestion = null, errorMessage = null) }
        _queryFlow.value = raw
    }

    /**
     * Pre-fill the search bar (e.g. from barcode scanner).
     */
    fun prefillQuery(raw: String) {
        _uiState.update { it.copy(query = raw, originalQuery = raw) }
        _queryFlow.value = raw
    }

    /**
     * Called when the user taps a suggestion chip.
     *
     * If the selected address differs from the original query that was first auto-parsed,
     * we log it as a manual correction so the learning engine can surface it later.
     */
    fun onSuggestionSelected(suggestion: AddressSuggestion) {
        val original = _uiState.value.originalQuery
        val corrected = suggestion.parsed.formatted

        _uiState.update { it.copy(selectedSuggestion = suggestion, suggestions = emptyList()) }

        if (original.isNotEmpty() && original != corrected) {
            viewModelScope.launch {
                deliveryLogger.logCorrection(
                    original    = original,
                    corrected   = corrected,
                    coordinates = suggestion.coordinates,
                )
            }
        }
    }

    /**
     * Clear current suggestions and selection.
     */
    fun clearSelection() {
        _uiState.update { AddressSearchUiState() }
        _queryFlow.value = ""
    }

    // ------------------------------------------------------------------
    // Private — debounce pipeline
    // ------------------------------------------------------------------

    private fun observeQueryDebounced() {
        viewModelScope.launch {
            _queryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .filter { it.length >= 3 }
                .flatMapLatest { raw ->
                    _uiState.update { it.copy(isLoading = true) }

                    // 1. Normalise the input through AddressParser
                    val normalised = runCatching { addressParser.expandAbbreviations(raw) }
                        .getOrDefault(raw)

                    // 2. Stream geocoded suggestions from GeocodingManager
                    geocodingManager
                        .searchAddress(normalised)
                        .catch { err ->
                            _uiState.update {
                                it.copy(
                                    isLoading    = false,
                                    errorMessage = err.message ?: "Geocoding failed",
                                )
                            }
                            emit(emptyList())
                        }
                }
                .collectLatest { results ->
                    _uiState.update {
                        it.copy(
                            suggestions     = results,
                            isLoading       = false,
                            originalQuery   = if (it.originalQuery.isEmpty()) it.query else it.originalQuery,
                        )
                    }
                }
        }
    }
}

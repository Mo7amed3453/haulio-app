package app.haulio.android.features.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.IAddressParser
import app.haulio.android.services.address.IGeocodingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

sealed interface ScannerState {
    /** Camera is active and waiting for a barcode. */
    data object Scanning : ScannerState

    /** Barcode detected, parsing and geocoding in progress. */
    data object Processing : ScannerState

    /**
     * Successfully extracted an address.
     *
     * @property rawBarcode   The raw barcode string.
     * @property extractedAddress Address text parsed from the barcode.
     * @property suggestion   Geocoded suggestion (null if geocoding failed or extraction returned nothing).
     */
    data class Success(
        val rawBarcode: String,
        val extractedAddress: String,
        val suggestion: AddressSuggestion?,
    ) : ScannerState

    /**
     * The barcode was scanned but address extraction failed.
     * Shows the raw text for manual editing.
     *
     * @property rawBarcode Raw barcode string.
     * @property message    Human-readable failure reason.
     */
    data class Fallback(
        val rawBarcode: String,
        val message: String,
    ) : ScannerState

    /** An unrecoverable error (camera permission denied, ML Kit crash, etc.). */
    data class Error(val message: String) : ScannerState
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * Processes ML Kit barcode scan results:
 *
 * 1. Attempt regex extraction of a shipping address from the raw barcode value.
 * 2. Pass the extracted text to [IAddressParser] for normalisation.
 * 3. Submit normalised text to [IGeocodingManager] for geocoding.
 * 4. Emit [ScannerState.Success] on success or [ScannerState.Fallback] if parsing fails.
 *
 * Supported shipping label formats are handled via [ShippingLabelExtractor].
 */
class BarcodeScannerViewModel(
    private val addressParser: IAddressParser,
    private val geocodingManager: IGeocodingManager,
) : ViewModel() {

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Scanning)
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    /** Set to true after the first successful scan to prevent re-processing. */
    private var hasProcessed = false

    // ------------------------------------------------------------------
    // Public actions
    // ------------------------------------------------------------------

    /**
     * Called by [BarcodeScannerScreen] when ML Kit detects a barcode.
     * Re-entrant calls while [Processing] are ignored (single-scan policy).
     */
    fun onBarcodeDetected(rawValue: String) {
        if (hasProcessed || _state.value is ScannerState.Processing) return
        hasProcessed = true
        _state.update { ScannerState.Processing }

        viewModelScope.launch {
            val extracted = ShippingLabelExtractor.extract(rawValue)

            if (extracted == null) {
                _state.update {
                    ScannerState.Fallback(
                        rawBarcode = rawValue,
                        message    = "Could not extract address. Edit the text below.",
                    )
                }
                return@launch
            }

            // Normalise with AddressParser
            val normalised = runCatching { addressParser.expandAbbreviations(extracted) }
                .getOrDefault(extracted)

            // Geocode — take the first emitted suggestion
            val suggestion = geocodingManager
                .searchAddress(normalised)
                .catch { emit(emptyList()) }
                .firstOrNull()
                ?.firstOrNull()

            _state.update {
                ScannerState.Success(
                    rawBarcode       = rawValue,
                    extractedAddress = normalised,
                    suggestion       = suggestion,
                )
            }
        }
    }

    /** Reset so the scanner is ready for another code. */
    fun resetScanner() {
        hasProcessed = false
        _state.update { ScannerState.Scanning }
    }

    fun onCameraError(message: String) {
        _state.update { ScannerState.Error(message) }
    }
}

// ---------------------------------------------------------------------------
// Shipping label address extractor
// ---------------------------------------------------------------------------

/**
 * Pure-function extractor that recognises common shipping label barcode formats
 * and returns the embedded address text, or `null` if none is found.
 *
 * Formats handled:
 * - FedEx Ground (`ADRB` record)
 * - UPS MaxiCode (structured field parsing)
 * - Amazon logistics JSON (`"address":{"…"}`)
 * - Generic "SHIP TO:" prefix
 * - Multiline text containing a recognisable US ZIP code
 */
object ShippingLabelExtractor {

    /**
     * Returns the best address string found in [raw], or null when extraction fails.
     */
    fun extract(raw: String): String? =
        tryFedEx(raw)
            ?: tryUps(raw)
            ?: tryAmazonJson(raw)
            ?: tryShipToPrefix(raw)
            ?: tryZipPattern(raw)

    // FedEx: look for "ADRB" record marker — address follows on next two tokens
    private val FEDEX_ADRB = Regex("""ADRB\s+(.+?)(?:\s{2,}|$)""", RegexOption.DOT_MATCHES_ALL)

    private fun tryFedEx(raw: String): String? =
        FEDEX_ADRB.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    // UPS MaxiCode: `Ship To:` field in structured text
    private val UPS_SHIPTO = Regex("""Ship\s+To:\s*(.+?)\s*(?:Ship\s+From:|$)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private fun tryUps(raw: String): String? =
        UPS_SHIPTO.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    // Amazon JSON: {"address":{"name":"…","address1":"…","city":"…","state":"…","zip":"…"}}
    private val AMAZON_ADDR1 = Regex(""""address1"\s*:\s*"([^"]+)"""")
    private val AMAZON_CITY  = Regex(""""city"\s*:\s*"([^"]+)"""")
    private val AMAZON_STATE = Regex(""""state"\s*:\s*"([^"]+)"""")
    private val AMAZON_ZIP   = Regex(""""zip(?:Code)?"\s*:\s*"([^"]+)"""")

    private fun tryAmazonJson(raw: String): String? {
        val addr1 = AMAZON_ADDR1.find(raw)?.groupValues?.getOrNull(1) ?: return null
        val city  = AMAZON_CITY.find(raw)?.groupValues?.getOrNull(1) ?: ""
        val state = AMAZON_STATE.find(raw)?.groupValues?.getOrNull(1) ?: ""
        val zip   = AMAZON_ZIP.find(raw)?.groupValues?.getOrNull(1) ?: ""
        return "$addr1, $city, $state $zip".trim().takeIf { addr1.isNotEmpty() }
    }

    // Generic "SHIP TO:" prefix
    private val GENERIC_SHIPTO = Regex("""SHIP\s+TO\s*:\s*(.+)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private fun tryShipToPrefix(raw: String): String? =
        GENERIC_SHIPTO.find(raw)?.groupValues?.getOrNull(1)
            ?.lines()
            ?.take(4)
            ?.joinToString(", ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    // Fallback: if the raw string contains a recognisable ZIP, grab surrounding context
    private val ZIP_CONTEXT = Regex("""(.{5,80}\b\d{5}(?:-\d{4})?\b)""")

    private fun tryZipPattern(raw: String): String? =
        ZIP_CONTEXT.find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
}

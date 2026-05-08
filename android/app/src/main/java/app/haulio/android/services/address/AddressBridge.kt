package app.haulio.android.services.address

import app.haulio.shared.navigation.models.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

/**
 * A parsed and normalised representation of a raw address string.
 *
 * @property streetNumber  House/building number, e.g. "1600".
 * @property streetName    Street name without type, e.g. "Pennsylvania".
 * @property streetType    Abbreviated street type, e.g. "Ave".
 * @property unitNumber    Apartment / suite / unit, e.g. "Apt 4B".
 * @property city          City name.
 * @property state         Two-letter state code, e.g. "DC".
 * @property zip           Five-digit ZIP, e.g. "20500".
 * @property zip4          Optional four-digit ZIP+4 extension, e.g. "0001".
 * @property formatted     Full formatted address ready for display.
 */
data class ParsedAddress(
    val streetNumber: String = "",
    val streetName: String = "",
    val streetType: String = "",
    val unitNumber: String? = null,
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val zip4: String? = null,
    val formatted: String = "",
)

/**
 * Confidence level returned after geocoding an address.
 */
enum class ConfidenceLevel {
    /** Pelias or Geocodio returned a full match with ZIP+4. */
    VERIFIED_ZIP4,
    /** Geocoded but low confidence or no ZIP+4 extension. */
    APPROXIMATE,
    /** Both geocoders failed to locate the address. */
    NOT_FOUND,
}

/**
 * A geocoded address suggestion ready for display in the dropdown.
 *
 * @property parsed        The normalised [ParsedAddress].
 * @property coordinates   Lat/lon returned by the geocoder.
 * @property confidence    Confidence level of the geocode result.
 * @property driverCount   Number of drivers who previously confirmed this address (0 = none yet).
 */
data class AddressSuggestion(
    val parsed: ParsedAddress,
    val coordinates: GeoPoint,
    val confidence: ConfidenceLevel,
    val driverCount: Int = 0,
)

// ---------------------------------------------------------------------------
// Bridge interfaces — mirror KMM AddressParser / GeocodingManager surface
// ---------------------------------------------------------------------------

/**
 * Android-side contract for the shared KMM AddressParser.
 */
interface IAddressParser {
    /**
     * Parse [rawInput] into a structured [ParsedAddress].
     * Implementations should normalise casing, expand abbreviations, etc.
     */
    suspend fun parse(rawInput: String): ParsedAddress

    /**
     * Expand common abbreviations in [input] (e.g. "St" → "Street").
     * Returns the expanded string without changing semantics.
     */
    suspend fun expandAbbreviations(input: String): String
}

/**
 * Android-side contract for the shared KMM GeocodingManager.
 */
interface IGeocodingManager {
    /**
     * Reactive search: emits up to 5 [AddressSuggestion]s as the query is refined.
     * Callers should collect this in a `viewModelScope`.
     */
    fun searchAddress(query: String): Flow<List<AddressSuggestion>>

    /**
     * One-shot geocode for a fully-formed address string.
     * Returns null when both Pelias and Geocodio fail.
     */
    suspend fun geocode(address: String): AddressSuggestion?
}

/**
 * Android-side contract for delivery and correction logging.
 * A [DeliveryLogger] wraps the Supabase client; see [app.haulio.android.services.address.DeliveryLogger].
 */
interface IDeliveryLogger {
    suspend fun logDelivery(
        address: String,
        coordinates: GeoPoint,
        unit: String?,
        photoUri: String?,
    )

    suspend fun logCorrection(
        original: String,
        corrected: String,
        coordinates: GeoPoint,
    )

    /**
     * Returns how many distinct drivers have corrected deliveries within [radiusMeters] of [coordinates].
     */
    suspend fun getCorrectionCount(coordinates: GeoPoint, radiusMeters: Double): Int
}

// ---------------------------------------------------------------------------
// KMM real-implementation adapters (placeholder — wire in when KMM module ships)
// ---------------------------------------------------------------------------

/**
 * Wraps the real KMM AddressParser.
 * Replace the TODO bodies once the KMM shared module is published.
 */
class KmmAddressParserBridge : IAddressParser {
    override suspend fun parse(rawInput: String): ParsedAddress {
        // TODO: delegate to KMM shared AddressParser
        throw UnsupportedOperationException("Wire up KMM AddressParser")
    }

    override suspend fun expandAbbreviations(input: String): String {
        // TODO: delegate to KMM shared AddressParser
        throw UnsupportedOperationException("Wire up KMM AddressParser")
    }
}

/**
 * Wraps the real KMM GeocodingManager.
 */
class KmmGeocodingManagerBridge : IGeocodingManager {
    override fun searchAddress(query: String): Flow<List<AddressSuggestion>> = flow {
        // TODO: delegate to KMM shared GeocodingManager
        throw UnsupportedOperationException("Wire up KMM GeocodingManager")
    }

    override suspend fun geocode(address: String): AddressSuggestion? {
        // TODO: delegate to KMM shared GeocodingManager
        throw UnsupportedOperationException("Wire up KMM GeocodingManager")
    }
}

// ---------------------------------------------------------------------------
// Mock implementations — realistic fake data for standalone development
// ---------------------------------------------------------------------------

private val ABBREVIATION_MAP = mapOf(
    "st"   to "Street",
    "ave"  to "Avenue",
    "blvd" to "Boulevard",
    "dr"   to "Drive",
    "rd"   to "Road",
    "ln"   to "Lane",
    "ct"   to "Court",
    "pl"   to "Place",
    "hwy"  to "Highway",
    "pkwy" to "Parkway",
    "n"    to "North",
    "s"    to "South",
    "e"    to "East",
    "w"    to "West",
    "apt"  to "Apt",
    "ste"  to "Suite",
)

/**
 * Mock [IAddressParser] that applies a simple abbreviation expansion and
 * returns a best-effort [ParsedAddress] without a KMM dependency.
 */
class MockAddressParser : IAddressParser {

    override suspend fun parse(rawInput: String): ParsedAddress {
        delay(50) // simulate lightweight processing
        val expanded = expandAbbreviations(rawInput)
        val parts = expanded.trim().split(Regex("[,\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

        val streetPart = parts.getOrElse(0) { expanded }
        val streetTokens = streetPart.split(" ")
        val number = streetTokens.firstOrNull()?.takeIf { it.all(Char::isDigit) } ?: ""
        val remaining = if (number.isNotEmpty()) streetTokens.drop(1) else streetTokens
        val streetName = remaining.dropLast(1).joinToString(" ")
        val streetType = remaining.lastOrNull() ?: ""

        val city  = parts.getOrElse(1) { "San Francisco" }
        val stateZip = parts.getOrElse(2) { "CA 94103" }.split(" ")
        val state = stateZip.getOrElse(0) { "CA" }
        val zipFull = stateZip.getOrElse(1) { "94103" }
        val zip  = zipFull.take(5)
        val zip4 = if (zipFull.length > 6) zipFull.substring(6) else null

        val formatted = buildString {
            if (number.isNotEmpty()) append("$number ")
            append("$streetName $streetType")
            append(", $city, $state $zip")
            if (zip4 != null) append("-$zip4")
        }

        return ParsedAddress(
            streetNumber = number,
            streetName   = streetName,
            streetType   = streetType,
            city         = city,
            state        = state,
            zip          = zip,
            zip4         = zip4,
            formatted    = formatted,
        )
    }

    override suspend fun expandAbbreviations(input: String): String {
        return input.split(" ").joinToString(" ") { token ->
            ABBREVIATION_MAP[token.lowercase().trimEnd('.')]?.let { expanded ->
                // preserve original casing style
                if (token.first().isUpperCase()) expanded else expanded.lowercase()
            } ?: token
        }
    }
}

private val MOCK_SUGGESTIONS = listOf(
    AddressSuggestion(
        parsed = ParsedAddress(
            streetNumber = "1600",
            streetName   = "Pennsylvania",
            streetType   = "Avenue",
            city         = "Washington",
            state        = "DC",
            zip          = "20500",
            zip4         = "0001",
            formatted    = "1600 Pennsylvania Avenue, Washington, DC 20500-0001",
        ),
        coordinates = GeoPoint(38.8976763, -77.0365298),
        confidence  = ConfidenceLevel.VERIFIED_ZIP4,
        driverCount = 5,
    ),
    AddressSuggestion(
        parsed = ParsedAddress(
            streetNumber = "1600",
            streetName   = "Pennsylvania",
            streetType   = "Avenue NW",
            city         = "Washington",
            state        = "DC",
            zip          = "20006",
            formatted    = "1600 Pennsylvania Avenue NW, Washington, DC 20006",
        ),
        coordinates = GeoPoint(38.8976, -77.0366),
        confidence  = ConfidenceLevel.APPROXIMATE,
        driverCount = 1,
    ),
    AddressSuggestion(
        parsed = ParsedAddress(
            streetNumber = "1620",
            streetName   = "Pennsylvania",
            streetType   = "Avenue NW",
            city         = "Washington",
            state        = "DC",
            zip          = "20006",
            formatted    = "1620 Pennsylvania Avenue NW, Washington, DC 20006",
        ),
        coordinates = GeoPoint(38.8980, -77.0370),
        confidence  = ConfidenceLevel.APPROXIMATE,
        driverCount = 0,
    ),
    AddressSuggestion(
        parsed = ParsedAddress(
            streetNumber = "160",
            streetName   = "Pennsylvania",
            streetType   = "Street",
            city         = "San Francisco",
            state        = "CA",
            zip          = "94107",
            formatted    = "160 Pennsylvania Street, San Francisco, CA 94107",
        ),
        coordinates = GeoPoint(37.7591, -122.3974),
        confidence  = ConfidenceLevel.APPROXIMATE,
        driverCount = 0,
    ),
    AddressSuggestion(
        parsed = ParsedAddress(
            streetNumber = "1600",
            streetName   = "Pennsylvania",
            streetType   = "Avenue",
            city         = "Mockville",
            state        = "OH",
            zip          = "44444",
            formatted    = "1600 Pennsylvania Avenue, Mockville, OH 44444",
        ),
        coordinates = GeoPoint(40.0, -82.0),
        confidence  = ConfidenceLevel.NOT_FOUND,
        driverCount = 0,
    ),
)

/**
 * Mock [IGeocodingManager] that filters [MOCK_SUGGESTIONS] by the query prefix
 * and introduces a small artificial delay to simulate network latency.
 */
class MockGeocodingManager : IGeocodingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun searchAddress(query: String): Flow<List<AddressSuggestion>> = flow {
        delay(200) // simulate Pelias round-trip
        val lower = query.lowercase()
        val filtered = MOCK_SUGGESTIONS
            .filter { it.parsed.formatted.lowercase().contains(lower) }
            .take(5)
        emit(filtered)
    }

    override suspend fun geocode(address: String): AddressSuggestion? {
        delay(250)
        return MOCK_SUGGESTIONS.firstOrNull {
            it.parsed.formatted.lowercase().contains(address.lowercase())
        }
    }
}

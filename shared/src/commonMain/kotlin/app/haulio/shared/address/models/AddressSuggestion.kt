package app.haulio.shared.address.models

import kotlinx.serialization.Serializable

/**
 * A geocoded address suggestion with coordinates and confidence metadata.
 *
 * @property formattedAddress The fully formatted, corrected address string.
 * @property latitude WGS-84 latitude in decimal degrees.
 * @property longitude WGS-84 longitude in decimal degrees.
 * @property confidence Numeric confidence score from the geocoder (0.0–1.0).
 * @property badge Overall confidence classification for display.
 * @property zip4 ZIP+4 code if available from the geocoder.
 * @property source Which geocoding service produced this result.
 */
@Serializable
data class AddressSuggestion(
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val confidence: Double,
    val badge: ConfidenceLevel,
    val zip4: String? = null,
    val source: GeocodingSource = GeocodingSource.PELIAS,
)

/**
 * Identifies which geocoding service produced a result.
 */
@Serializable
enum class GeocodingSource {
    PELIAS,
    GEOCODIO,
    CACHE,
}

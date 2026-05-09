package app.haulio.shared.address.geocoding.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for the Geocodio geocoding API.
 *
 * Endpoint: GET https://api.geocod.io/v1.7/geocode?q={address}&fields=zip4&api_key={KEY}
 */
@Serializable
data class GeocodioResponse(
    val results: List<GeocodioResult> = emptyList(),
)

/**
 * A single result from Geocodio.
 */
@Serializable
data class GeocodioResult(
    val location: GeocodioLocation,
    @SerialName("formatted_address") val formattedAddress: String = "",
    @SerialName("address_components") val addressComponents: GeocodioAddressComponents = GeocodioAddressComponents(),
    val accuracy: Double = 0.0,
    @SerialName("accuracy_type") val accuracyType: String = "",
    val fields: GeocodioFields? = null,
)

/**
 * Geographic coordinates from Geocodio.
 */
@Serializable
data class GeocodioLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

/**
 * Parsed address components from Geocodio.
 */
@Serializable
data class GeocodioAddressComponents(
    val number: String? = null,
    @SerialName("predirectional") val preDirectional: String? = null,
    val street: String? = null,
    val suffix: String? = null,
    @SerialName("secondaryunit") val secondaryUnit: String? = null,
    @SerialName("secondarynumber") val secondaryNumber: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val country: String? = null,
)

/**
 * Extended fields from Geocodio (when requested via &fields=zip4).
 */
@Serializable
data class GeocodioFields(
    val zip4: GeocodioZip4? = null,
)

/**
 * ZIP+4 data from Geocodio.
 */
@Serializable
data class GeocodioZip4(
    val zip4: String? = null,
)

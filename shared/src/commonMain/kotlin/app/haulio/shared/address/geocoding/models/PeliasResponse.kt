package app.haulio.shared.address.geocoding.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for the Pelias geocoding API.
 *
 * Pelias returns results in GeoJSON FeatureCollection format.
 * Endpoint: GET https://geo.haulio.app/v1/search?text={address}&boundary.country=US&size=5
 */
@Serializable
data class PeliasResponse(
    val features: List<PeliasFeature> = emptyList(),
)

/**
 * A single GeoJSON Feature from Pelias.
 */
@Serializable
data class PeliasFeature(
    val geometry: PeliasGeometry,
    val properties: PeliasProperties,
)

/**
 * GeoJSON geometry containing coordinates.
 * Note: GeoJSON uses [longitude, latitude] order.
 */
@Serializable
data class PeliasGeometry(
    val coordinates: List<Double> = emptyList(),
) {
    /** Latitude (y-coordinate). */
    val latitude: Double get() = coordinates.getOrElse(1) { 0.0 }
    /** Longitude (x-coordinate). */
    val longitude: Double get() = coordinates.getOrElse(0) { 0.0 }
}

/**
 * Properties of a Pelias geocoding result.
 */
@Serializable
data class PeliasProperties(
    val confidence: Double = 0.0,
    val label: String = "",
    @SerialName("housenumber") val houseNumber: String? = null,
    val street: String? = null,
    val locality: String? = null,
    val region: String? = null,
    @SerialName("region_a") val regionAbbr: String? = null,
    val postalcode: String? = null,
    val country: String? = null,
)

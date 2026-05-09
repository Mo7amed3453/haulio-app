package app.haulio.shared.address.geocoding

import app.haulio.shared.address.geocoding.models.PeliasFeature
import app.haulio.shared.address.geocoding.models.PeliasResponse
import app.haulio.shared.address.models.AddressSuggestion
import app.haulio.shared.address.models.ConfidenceLevel
import app.haulio.shared.address.models.GeocodingSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * HTTP client for the Pelias geocoding service.
 *
 * Pelias is the primary geocoder in the Smart Address Auto-Correction system.
 * It queries the self-hosted instance at geo.haulio.app.
 *
 * Endpoint: GET https://geo.haulio.app/v1/search?text={address}&boundary.country=US&size=5
 *
 * @param httpClient Ktor HttpClient configured with JSON content negotiation.
 * @param baseUrl Base URL of the Pelias instance.
 */
class PeliasClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://geo.haulio.app",
) {

    /**
     * Geocodes an address string using Pelias.
     *
     * @param address The normalized address text to geocode.
     * @param size Maximum number of results to return (default 5).
     * @return List of [AddressSuggestion] sorted by confidence descending.
     */
    suspend fun search(address: String, size: Int = 5): List<AddressSuggestion> {
        val response: PeliasResponse = httpClient.get("$baseUrl/v1/search") {
            parameter("text", address)
            parameter("boundary.country", "US")
            parameter("size", size)
        }.body()

        return response.features.map { feature ->
            feature.toAddressSuggestion()
        }.sortedByDescending { it.confidence }
    }

    /**
     * Converts a Pelias feature to an [AddressSuggestion].
     */
    private fun PeliasFeature.toAddressSuggestion(): AddressSuggestion {
        val confidence = properties.confidence
        val badge = when {
            confidence >= 0.9 && properties.postalcode?.length == 5 -> ConfidenceLevel.VERIFIED_ZIP4
            confidence >= 0.7 -> ConfidenceLevel.APPROXIMATE
            else -> ConfidenceLevel.NOT_FOUND
        }

        // Extract ZIP+4 from postalcode if it contains a dash
        val zip4 = properties.postalcode?.let { zip ->
            if (zip.contains("-") && zip.length >= 10) {
                zip.substringAfter("-")
            } else {
                null
            }
        }

        return AddressSuggestion(
            formattedAddress = properties.label,
            latitude = geometry.latitude,
            longitude = geometry.longitude,
            confidence = confidence,
            badge = badge,
            zip4 = zip4,
            source = GeocodingSource.PELIAS,
        )
    }
}

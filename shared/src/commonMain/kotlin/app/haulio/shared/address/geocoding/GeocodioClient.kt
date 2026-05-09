package app.haulio.shared.address.geocoding

import app.haulio.shared.address.geocoding.models.GeocodioResponse
import app.haulio.shared.address.geocoding.models.GeocodioResult
import app.haulio.shared.address.models.AddressSuggestion
import app.haulio.shared.address.models.ConfidenceLevel
import app.haulio.shared.address.models.GeocodingSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * HTTP client for the Geocodio geocoding service (fallback geocoder).
 *
 * Used when Pelias returns results with confidence < 0.7 or no results.
 * Geocodio provides ZIP+4 data which is valuable for address verification.
 *
 * Endpoint: GET https://api.geocod.io/v1.7/geocode?q={address}&fields=zip4&api_key={KEY}
 *
 * @param httpClient Ktor HttpClient configured with JSON content negotiation.
 * @param apiKey Geocodio API key (from BuildConfig or environment, never hardcoded).
 * @param baseUrl Base URL for the Geocodio API.
 */
class GeocodioClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.geocod.io/v1.7",
) {

    /**
     * Geocodes an address string using Geocodio with ZIP+4 field enrichment.
     *
     * @param address The normalized address text to geocode.
     * @return List of [AddressSuggestion] sorted by accuracy descending.
     * @throws IllegalStateException if apiKey is blank.
     */
    suspend fun geocode(address: String): List<AddressSuggestion> {
        require(apiKey.isNotBlank()) { "Geocodio API key must not be blank" }

        val response: GeocodioResponse = httpClient.get("$baseUrl/geocode") {
            parameter("q", address)
            parameter("fields", "zip4")
            parameter("api_key", apiKey)
        }.body()

        return response.results.map { result ->
            result.toAddressSuggestion()
        }.sortedByDescending { it.confidence }
    }

    /**
     * Converts a Geocodio result to an [AddressSuggestion].
     */
    private fun GeocodioResult.toAddressSuggestion(): AddressSuggestion {
        // Geocodio accuracy is 0-1 scale, with 1.0 being rooftop-level
        val confidence = accuracy.coerceIn(0.0, 1.0)
        val zip4 = fields?.zip4?.zip4

        val badge = when {
            confidence >= 0.8 && zip4 != null -> ConfidenceLevel.VERIFIED_ZIP4
            confidence >= 0.5 -> ConfidenceLevel.APPROXIMATE
            else -> ConfidenceLevel.NOT_FOUND
        }

        return AddressSuggestion(
            formattedAddress = formattedAddress,
            latitude = location.lat,
            longitude = location.lng,
            confidence = confidence,
            badge = badge,
            zip4 = zip4,
            source = GeocodingSource.GEOCODIO,
        )
    }
}

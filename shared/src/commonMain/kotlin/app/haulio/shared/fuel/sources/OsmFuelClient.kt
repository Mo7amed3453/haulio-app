package app.haulio.shared.fuel.sources

import app.haulio.shared.fuel.models.FuelStation
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fetches fuel station metadata from the OpenStreetMap Overpass API.
 *
 * Queries [amenity=fuel] nodes within a given bounding box.
 * Returns station geometry and tags only — no price data.
 *
 * Endpoint: POST https://overpass-api.de/api/interpreter
 *   (via GET with encoded query for KMM compatibility)
 *
 * @param httpClient Ktor [HttpClient] configured with JSON content negotiation.
 * @param baseUrl    Overpass API base URL (overridable for testing).
 */
class OsmFuelClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://overpass-api.de/api",
) {

    /**
     * Fetches fuel stations within the bounding box defined by
     * ([minLat], [minLng]) – ([maxLat], [maxLng]).
     *
     * @return [Result.success] with a list of [FuelStation] (prices are null),
     *         or [Result.failure] wrapping the underlying network or parse error.
     */
    suspend fun fetchStationsInBbox(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ): Result<List<FuelStation>> = runCatching {
        // Overpass QL query for amenity=fuel nodes in bounding box
        val query = buildQuery(minLat, minLng, maxLat, maxLng)

        val response: OverpassResponse = httpClient.get("$baseUrl/interpreter") {
            parameter("data", query)
        }.body()

        response.elements.mapNotNull { it.toFuelStationOrNull() }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun buildQuery(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ): String = buildString {
        append("[out:json][timeout:25];")
        append("node[\"amenity\"=\"fuel\"]")
        append("($minLat,$minLng,$maxLat,$maxLng);")
        append("out body;")
    }

    private fun OverpassElement.toFuelStationOrNull(): FuelStation? {
        if (lat == null || lon == null) return null
        val stationId = "osm_${id}"
        return FuelStation(
            id              = stationId,
            name            = tags["name"],
            brand           = tags["brand"] ?: tags["operator"],
            lat             = lat,
            lng             = lon,
            latestPrice     = null,
            lastReportedTs  = null,
        )
    }
}

// ---------------------------------------------------------------------------
// Overpass API response models
// ---------------------------------------------------------------------------

@Serializable
internal data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList(),
)

@Serializable
internal data class OverpassElement(
    val type: String = "",
    val id: Long = 0L,
    val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    val tags: Map<String, String> = emptyMap(),
)

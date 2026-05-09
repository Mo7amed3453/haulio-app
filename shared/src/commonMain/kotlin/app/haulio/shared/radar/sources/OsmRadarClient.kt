package app.haulio.shared.radar.sources

import app.haulio.shared.radar.models.SpeedCamera
import app.haulio.shared.radar.models.SpeedCameraSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fetches speed camera locations from the OpenStreetMap Overpass API.
 *
 * Queries [highway=speed_camera] nodes within a given bounding box.
 * Returns camera geometry only — no speed data unless tagged in OSM.
 *
 * Endpoint: GET https://overpass-api.de/api/interpreter?data=<ql>
 *
 * @param httpClient Ktor [HttpClient] configured with JSON content negotiation.
 * @param baseUrl    Overpass API base URL (overridable for testing).
 */
class OsmRadarClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://overpass-api.de/api",
) {

    /**
     * Fetches speed cameras within the bounding box defined by
     * ([minLat], [minLng]) – ([maxLat], [maxLng]).
     *
     * @return [Result.success] with a list of [SpeedCamera] (source = [SpeedCameraSource.OSM]),
     *         or [Result.failure] wrapping the underlying network or parse error.
     */
    suspend fun fetchCamerasInBbox(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ): Result<List<SpeedCamera>> = runCatching {
        val query = buildQuery(minLat, minLng, maxLat, maxLng)
        val response: OsmOverpassResponse = httpClient.get("$baseUrl/interpreter") {
            parameter("data", query)
        }.body()
        response.elements.mapNotNull { it.toSpeedCameraOrNull() }
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
        append("node[\"highway\"=\"speed_camera\"]")
        append("($minLat,$minLng,$maxLat,$maxLng);")
        append("out body;")
    }

    private fun OsmRadarElement.toSpeedCameraOrNull(): SpeedCamera? {
        if (lat == null || lon == null) return null
        val speedMph = tags["maxspeed"]
            ?.filter { it.isDigit() }
            ?.toIntOrNull()
        return SpeedCamera(
            id              = "osm_$id",
            lat             = lat,
            lng             = lon,
            postedSpeedMph  = speedMph,
            source          = SpeedCameraSource.OSM,
            reportedAt      = null,
            confirmedCount  = 0,
        )
    }
}

// ---------------------------------------------------------------------------
// Overpass API response models (internal)
// ---------------------------------------------------------------------------

@Serializable
internal data class OsmOverpassResponse(
    val elements: List<OsmRadarElement> = emptyList(),
)

@Serializable
internal data class OsmRadarElement(
    val type: String = "",
    val id: Long = 0L,
    val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    val tags: Map<String, String> = emptyMap(),
)

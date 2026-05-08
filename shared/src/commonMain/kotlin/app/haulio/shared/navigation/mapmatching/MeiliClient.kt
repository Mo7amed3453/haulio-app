package app.haulio.shared.navigation.mapmatching

import app.haulio.shared.navigation.models.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

/**
 * Client for Valhalla Meili `/trace_route` map matching.
 */
class MeiliClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://routing.haulio.app",
) {
    /**
     * Snaps GPS samples to road network and returns matched geometry.
     */
    suspend fun traceRoute(points: List<TimedGeoPoint>): List<GeoPoint> {
        if (points.isEmpty()) return emptyList()
        val req = TraceRouteRequest(
            shape = points.map { TracePoint(it.lat, it.lon, it.timeUnixSeconds) },
            costing = "auto",
            shapeMatch = "map_snap",
        )
        val resp: TraceRouteResponse = httpClient.post("$baseUrl/trace_route") {
            setBody(req)
        }.body()
        return resp.matchedPoints.map { GeoPoint(it.lat, it.lon) }
    }
}

/**
 * Timestamped GPS sample used for map matching.
 */
data class TimedGeoPoint(
    val lat: Double,
    val lon: Double,
    val timeUnixSeconds: Long,
)

@Serializable
private data class TraceRouteRequest(
    val shape: List<TracePoint>,
    val costing: String,
    val shapeMatch: String,
)

@Serializable
private data class TracePoint(
    val lat: Double,
    val lon: Double,
    val time: Long,
)

@Serializable
private data class TraceRouteResponse(
    val matchedPoints: List<MatchedPoint>,
)

@Serializable
private data class MatchedPoint(
    val lat: Double,
    val lon: Double,
)

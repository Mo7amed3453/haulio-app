package app.haulio.shared.navigation

import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.models.ManeuverType
import app.haulio.shared.navigation.models.NavigationStep
import app.haulio.shared.navigation.models.RouteResponse
import app.haulio.shared.util.PolylineDecoder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * HTTP client for Valhalla route API.
 */
class ValhallaRouteClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://routing.haulio.app",
) {
    /**
     * Fetches and parses a route from Valhalla.
     */
    suspend fun getRoute(locations: List<GeoPoint>): RouteResponse {
        require(locations.size >= 2) { "At least origin and destination are required." }

        val request = RouteRequest(
            locations = locations.map { LatLon(it.lat, it.lon) },
            costing = "auto",
            directionsOptions = DirectionsOptions(units = "miles"),
            language = "en-US",
        )
        val response: ValhallaRouteApiResponse = httpClient.post("$baseUrl/route") {
            setBody(request)
        }.body()

        val legs = response.trip.legs
        val shape = legs.firstOrNull()?.shape.orEmpty()
        val decoded = PolylineDecoder.decode(shape, precision = 6)
        val steps = legs.flatMap { leg ->
            leg.maneuvers.map { m ->
                NavigationStep(
                    maneuverType = ManeuverType.fromValhallaType(m.type),
                    instruction = m.instruction,
                    distanceMiles = m.length,
                    streetName = m.streetNames.firstOrNull(),
                    beginShapeIndex = m.beginShapeIndex,
                    endShapeIndex = m.endShapeIndex,
                )
            }
        }
        val totalDistance = steps.sumOf { it.distanceMiles }
        return RouteResponse(
            polyline = shape,
            decodedShape = decoded,
            steps = steps,
            totalDistanceMiles = totalDistance,
        )
    }
}

@Serializable
private data class RouteRequest(
    val locations: List<LatLon>,
    val costing: String,
    @SerialName("directions_options") val directionsOptions: DirectionsOptions,
    val language: String,
)

@Serializable
private data class LatLon(
    val lat: Double,
    val lon: Double,
)

@Serializable
private data class DirectionsOptions(val units: String)

@Serializable
private data class ValhallaRouteApiResponse(val trip: Trip)

@Serializable
private data class Trip(val legs: List<Leg>)

@Serializable
private data class Leg(
    val shape: String,
    val maneuvers: List<Maneuver>,
)

@Serializable
private data class Maneuver(
    val type: Int,
    val instruction: String,
    val length: Double,
    @SerialName("street_names") val streetNames: List<String> = emptyList(),
    @SerialName("begin_shape_index") val beginShapeIndex: Int,
    @SerialName("end_shape_index") val endShapeIndex: Int,
)

package app.haulio.shared.traffic.extreme

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client for the TomTom Traffic Flow Segment Data API.
 *
 * Used exclusively in **extreme mode**, called after [BusyZoneTrigger] fires a
 * [ZoneActivationEvent]. Each call is gated by [TomTomBudgetManager] to stay within
 * the daily quota.
 *
 * Endpoint: GET https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json
 *           ?key={apiKey}&point={lat},{lng}
 *
 * @param httpClient Ktor HttpClient configured with JSON content negotiation.
 * @param apiKey TomTom API key (never hardcoded; injected from secure config).
 * @param baseUrl Override for the TomTom API base URL (useful for testing).
 */
class TomTomClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.tomtom.com",
) {

    /**
     * Fetches real-time traffic flow data for the road segment at ([lat], [lng]).
     *
     * @param lat WGS-84 latitude of the query point.
     * @param lng WGS-84 longitude of the query point.
     * @return [Result.success] wrapping [FlowSegmentData], or [Result.failure] on any error.
     */
    suspend fun fetchFlow(lat: Double, lng: Double): Result<FlowSegmentData> = runCatching {
        require(apiKey.isNotBlank()) { "TomTom API key must not be blank" }

        val response: TomTomFlowResponse = httpClient.get(
            "$baseUrl/traffic/services/4/flowSegmentData/absolute/10/json"
        ) {
            parameter("key", apiKey)
            parameter("point", "$lat,$lng")
        }.body()

        response.flowSegmentData
    }
}

// ---------------------------------------------------------------------------
// Domain model
// ---------------------------------------------------------------------------

/**
 * Real-time traffic flow data for a road segment returned by TomTom.
 *
 * @property currentSpeed Current speed of traffic on the segment (km/h).
 * @property freeFlowSpeed Speed under free-flow (no congestion) conditions (km/h).
 * @property currentTravelTime Current travel time to traverse the segment (seconds).
 * @property freeFlowTravelTime Travel time under free-flow conditions (seconds).
 * @property confidence Confidence level of the measurement (0.0 – 1.0).
 */
@Serializable
data class FlowSegmentData(
    @SerialName("currentSpeed") val currentSpeed: Int = 0,
    @SerialName("freeFlowSpeed") val freeFlowSpeed: Int = 0,
    @SerialName("currentTravelTime") val currentTravelTime: Int = 0,
    @SerialName("freeFlowTravelTime") val freeFlowTravelTime: Int = 0,
    @SerialName("confidence") val confidence: Double = 0.0,
)

// ---------------------------------------------------------------------------
// Response wrapper
// ---------------------------------------------------------------------------

@Serializable
internal data class TomTomFlowResponse(
    @SerialName("flowSegmentData") val flowSegmentData: FlowSegmentData,
)

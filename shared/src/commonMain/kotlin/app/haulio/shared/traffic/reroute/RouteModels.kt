package app.haulio.shared.traffic.reroute

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Simplified Valhalla route response used by the AutoRerouteEngine.
 *
 * Full Valhalla JSON schema: https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/
 */
@Serializable
data class RouteResponse(
    val trip: Trip,
)

@Serializable
data class Trip(
    val legs: List<RouteLeg>,
    val summary: RouteSummary,
    val status: Int = 0,
    @SerialName("status_message") val statusMessage: String = "",
)

@Serializable
data class RouteLeg(
    val maneuvers: List<Maneuver>,
    val shape: String = "", // encoded polyline6
    val summary: LegSummary = LegSummary(),
)

@Serializable
data class LegSummary(
    val length: Double = 0.0,   // miles
    val time: Int = 0,          // seconds
)

@Serializable
data class Maneuver(
    val length: Double = 0.0,               // miles
    val time: Int = 0,                      // seconds
    @SerialName("begin_shape_index") val beginShapeIndex: Int = 0,
    @SerialName("end_shape_index") val endShapeIndex: Int = 0,
    @SerialName("speed_limit") val speedLimitMph: Int? = null,
    @SerialName("street_names") val streetNames: List<String>? = null,
    @SerialName("instruction") val instruction: String = "",
)

@Serializable
data class RouteSummary(
    val length: Double = 0.0,   // total miles
    val time: Int = 0,          // total seconds
    val cost: Double = 0.0,
)

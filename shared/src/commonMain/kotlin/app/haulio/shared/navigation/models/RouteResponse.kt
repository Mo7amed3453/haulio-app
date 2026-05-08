package app.haulio.shared.navigation.models

/**
 * A latitude/longitude pair in decimal degrees.
 */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
)

/**
 * Parsed route response from Valhalla `/route`.
 *
 * @property polyline encoded shape string with precision 6.
 * @property decodedShape decoded route geometry points.
 * @property steps flattened maneuver list for all legs.
 * @property totalDistanceMiles route distance in miles.
 */
data class RouteResponse(
    val polyline: String,
    val decodedShape: List<GeoPoint>,
    val steps: List<NavigationStep>,
    val totalDistanceMiles: Double,
)

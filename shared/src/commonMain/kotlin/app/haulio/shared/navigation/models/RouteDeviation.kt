package app.haulio.shared.navigation.models

/**
 * Event describing route deviation detection.
 *
 * @property location current GPS location when deviation was emitted.
 * @property distanceFromRouteMeters nearest distance from route geometry.
 * @property offRouteSeconds consecutive off-route seconds.
 */
data class RouteDeviation(
    val location: GeoPoint,
    val distanceFromRouteMeters: Double,
    val offRouteSeconds: Long,
)

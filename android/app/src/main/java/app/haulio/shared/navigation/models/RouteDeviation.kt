package app.haulio.shared.navigation.models

/**
 * Event describing route deviation detection.
 * Local Android stub — mirrors the KMM shared data class until the shared module is wired in.
 */
data class RouteDeviation(
    val location: GeoPoint,
    val distanceFromRouteMeters: Double,
    val offRouteSeconds: Int,
)

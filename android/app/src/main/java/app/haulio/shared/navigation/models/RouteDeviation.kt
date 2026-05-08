package app.haulio.shared.navigation.models

data class RouteDeviation(
    val location: GeoPoint,
    val distanceFromRouteMeters: Double,
    val offRouteSeconds: Int,
)

package app.haulio.shared.navigation.models

data class RouteResponse(
    val polyline: String,
    val decodedShape: List<GeoPoint>,
    val steps: List<NavigationStep>,
    val totalDistanceMiles: Double,
)

package app.haulio.shared.navigation.models

data class NavigationStep(
    val maneuverType: ManeuverType,
    val instruction: String,
    val distanceMiles: Double,
    val streetName: String?,
    val startShapeIndex: Int,
    val endShapeIndex: Int,
)

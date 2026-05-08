package app.haulio.shared.navigation.models

/**
 * A single turn-by-turn instruction step.
 * Local Android stub — mirrors the KMM shared data class until the shared module is wired in.
 */
data class NavigationStep(
    val maneuverType: ManeuverType,
    val instruction: String,
    val distanceMiles: Double,
    val streetName: String?,
    val startShapeIndex: Int,
    val endShapeIndex: Int,
)

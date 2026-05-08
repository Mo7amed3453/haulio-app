package app.haulio.shared.navigation.models

/**
 * A single turn-by-turn instruction step.
 *
 * @property maneuverType normalized maneuver type.
 * @property instruction human-readable instruction.
 * @property distanceMiles maneuver segment length in miles.
 * @property streetName primary street name if present.
 * @property beginShapeIndex start index of step in decoded polyline.
 * @property endShapeIndex end index of step in decoded polyline.
 */
data class NavigationStep(
    val maneuverType: ManeuverType,
    val instruction: String,
    val distanceMiles: Double,
    val streetName: String?,
    val beginShapeIndex: Int,
    val endShapeIndex: Int,
)

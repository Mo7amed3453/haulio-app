package app.haulio.shared.navigation.models

/**
 * Supported maneuver types mapped from Valhalla maneuver integer codes.
 * Local Android stub — mirrors the KMM shared enum until the shared module is wired in.
 */
enum class ManeuverType {
    START,
    DESTINATION,
    CONTINUE,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    UTURN_RIGHT,
    UTURN_LEFT,
    SHARP_LEFT,
    LEFT,
    SLIGHT_LEFT,
    RAMP_STRAIGHT,
    RAMP_RIGHT,
    RAMP_LEFT,
    EXIT_RIGHT,
    EXIT_LEFT,
    MERGE,
    ROUNDABOUT_ENTER,
    ROUNDABOUT_EXIT,
    TRANSIT,
    FERRY,
    UNKNOWN;

    companion object {
        fun fromValhallaType(code: Int): ManeuverType = when (code) {
            0 -> START
            1 -> DESTINATION
            2 -> CONTINUE
            3 -> SLIGHT_RIGHT
            4 -> RIGHT
            5 -> SHARP_RIGHT
            6 -> UTURN_RIGHT
            7 -> UTURN_LEFT
            8 -> SHARP_LEFT
            9 -> LEFT
            10 -> SLIGHT_LEFT
            11 -> RAMP_STRAIGHT
            12 -> RAMP_RIGHT
            13 -> RAMP_LEFT
            14 -> EXIT_RIGHT
            15 -> EXIT_LEFT
            16 -> MERGE
            17 -> ROUNDABOUT_ENTER
            18 -> ROUNDABOUT_EXIT
            19 -> TRANSIT
            20 -> FERRY
            else -> UNKNOWN
        }
    }
}

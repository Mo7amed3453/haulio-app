package app.haulio.shared.radar.models

/**
 * Local stub for the KMM shared RadarAlertEvent model.
 * Replace with actual KMM artifact when the shared module is wired.
 */
data class RadarAlertEvent(
    val camera: SpeedCamera,
    val level: RadarAlertLevel,
    val distanceMeters: Double,
    val headingMatch: Boolean,
)

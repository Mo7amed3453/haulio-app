package app.haulio.shared.radar.models

/**
 * Emitted by [app.haulio.shared.radar.RadarProximityEngine] when a driver
 * is approaching a speed camera at an alertable distance.
 *
 * @property camera          The camera that triggered this event.
 * @property level           Severity level (distance bucket).
 * @property distanceMeters  Exact haversine distance in metres at the time of emission.
 * @property headingMatch    True if the driver is travelling roughly toward the camera
 *                           (heading within ±60°), or if no heading data is available.
 */
data class RadarAlertEvent(
    val camera: SpeedCamera,
    val level: RadarAlertLevel,
    val distanceMeters: Double,
    val headingMatch: Boolean,
)

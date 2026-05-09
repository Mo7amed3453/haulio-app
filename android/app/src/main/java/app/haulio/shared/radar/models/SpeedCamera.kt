package app.haulio.shared.radar.models

/**
 * Local stub for the KMM shared SpeedCamera model.
 * Replace with actual KMM artifact when the shared module is wired.
 *
 * Constructor order matches usage in [app.haulio.android.services.radar.RadarBridge]:
 * SpeedCamera(id, lat, lng, postedSpeedMph, source, reportedAt, confirmedCount)
 */
data class SpeedCamera(
    val id: String,
    val lat: Double,
    val lng: Double,
    val postedSpeedMph: Int?,
    val source: SpeedCameraSource,
    val reportedAt: Long?,
    val confirmedCount: Int,
)

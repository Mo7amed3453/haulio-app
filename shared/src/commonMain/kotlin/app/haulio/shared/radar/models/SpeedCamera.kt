package app.haulio.shared.radar.models

/**
 * Source of truth for a speed camera record.
 */
enum class SpeedCameraSource {
    /** Pulled from OpenStreetMap Overpass API. */
    OSM,
    /** Reported by a Haulio driver; not yet formally verified. */
    CROWD,
    /** Crowd-reported camera that has been confirmed by multiple drivers. */
    CONFIRMED,
}

/**
 * A single speed camera location.
 *
 * @property id            Stable identifier (OSM node ID prefixed with "osm_", or Supabase UUID).
 * @property lat           WGS-84 latitude.
 * @property lng           WGS-84 longitude.
 * @property postedSpeedMph Speed limit posted at the camera, in mph. Null if unknown.
 * @property source        Origin of this record.
 * @property reportedAt    Epoch-millisecond timestamp when the record was first created. Null for OSM.
 * @property confirmedCount Number of independent driver confirmations (crowd only).
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

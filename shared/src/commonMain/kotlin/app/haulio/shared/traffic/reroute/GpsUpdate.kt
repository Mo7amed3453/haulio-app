package app.haulio.shared.traffic.reroute

import kotlinx.serialization.Serializable

/**
 * A GPS position + velocity sample emitted by the device location service.
 *
 * @property lat WGS-84 latitude in decimal degrees.
 * @property lng WGS-84 longitude in decimal degrees.
 * @property speedKph Current speed reported by the GPS receiver, in km/h.
 * @property headingDeg Compass heading (0–359 degrees). Null if stationary.
 * @property timestampMs Epoch-millisecond timestamp of this reading.
 * @property accuracyMeters Horizontal accuracy estimate in metres. Null if unavailable.
 */
@Serializable
data class GpsUpdate(
    val lat: Double,
    val lng: Double,
    val speedKph: Double,
    val headingDeg: Double? = null,
    val timestampMs: Long,
    val accuracyMeters: Double? = null,
)

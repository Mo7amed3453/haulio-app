package app.haulio.shared.traffic.models

import kotlinx.serialization.Serializable

/**
 * A single traffic event observed on the road network.
 *
 * @property sourceId Unique identifier assigned by the originating data source.
 * @property type Classification of the event (accident, construction, etc.).
 * @property severity Impact severity of the event.
 * @property lat WGS-84 latitude in decimal degrees.
 * @property lng WGS-84 longitude in decimal degrees.
 * @property headingDeg Optional compass heading (0–359) indicating directionality.
 * @property startTs Epoch-millisecond timestamp when the event began.
 * @property expiresTs Epoch-millisecond timestamp when the event is expected to end.
 * @property source Which data provider reported this event.
 */
@Serializable
data class TrafficEvent(
    val sourceId: String,
    val type: TrafficEventType,
    val severity: TrafficSeverity,
    val lat: Double,
    val lng: Double,
    val headingDeg: Double? = null,
    val startTs: Long,
    val expiresTs: Long,
    val source: TrafficSource,
)

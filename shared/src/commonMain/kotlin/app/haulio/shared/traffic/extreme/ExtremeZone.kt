package app.haulio.shared.traffic.extreme

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Classification of an extreme traffic zone that warrants TomTom flow enrichment.
 */
enum class ExtremeZoneType {
    /** School zone with morning and afternoon drop-off/pickup windows. */
    SCHOOL,

    /** Industrial / port area with heavy shift-change traffic. */
    INDUSTRIAL,

    /** At-grade rail crossing with train-schedule-based activation. */
    RAIL_CROSSING,

    /** Historical corridor with custom time windows sourced from Supabase. */
    HISTORICAL_CORRIDOR,
}

/**
 * A time window during which a zone is considered active on specific days of the week.
 *
 * @property daysOfWeek Set of [DayOfWeek] values on which this window applies.
 * @property localStart Start of the active window in local time (inclusive).
 * @property localEnd End of the active window in local time (inclusive).
 */
data class DayWindow(
    val daysOfWeek: Set<DayOfWeek>,
    val localStart: LocalTime,
    val localEnd: LocalTime,
)

/**
 * A geographic zone with scheduled activity windows that trigger TomTom enrichment.
 *
 * @property id Unique stable identifier.
 * @property type Classification of this zone.
 * @property centerLat WGS-84 latitude of the zone centre.
 * @property centerLng WGS-84 longitude of the zone centre.
 * @property radiusMiles Activation radius in statute miles.
 * @property windows Ordered list of time windows during which this zone is active.
 * @property timeZoneId IANA timezone identifier used to interpret [windows] (e.g. "America/Los_Angeles").
 */
data class ExtremeZone(
    val id: String,
    val type: ExtremeZoneType,
    val centerLat: Double,
    val centerLng: Double,
    val radiusMiles: Double,
    val windows: List<DayWindow>,
    val timeZoneId: String = "America/Los_Angeles",
)

/**
 * Emitted by [BusyZoneTrigger] when a vehicle enters an active extreme zone.
 *
 * @property zone The zone that was entered.
 * @property triggeredAtMs Epoch-millisecond timestamp when the activation was detected.
 */
data class ZoneActivationEvent(
    val zone: ExtremeZone,
    val triggeredAtMs: Long,
)

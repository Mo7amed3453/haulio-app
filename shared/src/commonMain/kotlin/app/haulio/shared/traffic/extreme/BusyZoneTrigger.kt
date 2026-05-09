package app.haulio.shared.traffic.extreme

import app.haulio.shared.traffic.reroute.GpsUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Monitors GPS updates and emits a [ZoneActivationEvent] the first time a vehicle
 * enters an extreme zone that is currently within its active time window.
 *
 * ### Per-fix evaluation
 * 1. Query [ExtremeZoneRepository.zonesForLocation] for zones whose radius encompasses
 *    the current position.
 * 2. For each newly entered zone, check whether the current local time falls inside one
 *    of its [DayWindow] entries (timezone-aware via kotlinx-datetime).
 * 3. Emit [ZoneActivationEvent] for each qualifying entry.
 * 4. Track which zones are currently "active" so entry is only emitted once per entry.
 *
 * @param repository Provider of zone definitions (bundled + optional Supabase sync).
 * @param clock Injectable clock for deterministic testing.
 */
class BusyZoneTrigger(
    private val repository: ExtremeZoneRepository,
    private val clock: Clock = Clock.System,
) {
    private val _activations = MutableSharedFlow<ZoneActivationEvent>()

    /** Emits one [ZoneActivationEvent] per zone-entry (re-emits after zone exit and re-entry). */
    val activations: Flow<ZoneActivationEvent> = _activations.asSharedFlow()

    private val activeZoneIds = mutableSetOf<String>()

    /**
     * Evaluates [update] against all known extreme zones.
     * Must be called for each incoming [GpsUpdate] (typically from the navigation layer).
     */
    suspend fun onGpsUpdate(update: GpsUpdate) {
        val now: Instant = clock.now()
        val nearbyZones = repository.zonesForLocation(update.lat, update.lng, now)

        // Determine which previously-active zones have been exited
        val exitedIds = activeZoneIds.filter { id -> nearbyZones.none { it.id == id } }
        exitedIds.forEach { activeZoneIds.remove(it) }

        // Determine newly entered, currently active zones
        val entered = nearbyZones.filter { zone ->
            zone.id !in activeZoneIds && isWindowActive(zone, now)
        }

        for (zone in entered) {
            activeZoneIds.add(zone.id)
            _activations.emit(
                ZoneActivationEvent(
                    zone = zone,
                    triggeredAtMs = now.toEpochMilliseconds(),
                )
            )
        }
    }

    /**
     * Returns `true` if [instant] falls within any of [zone]'s [DayWindow] entries,
     * evaluated in the zone's local timezone.
     */
    internal fun isWindowActive(zone: ExtremeZone, instant: Instant): Boolean {
        val tz = runCatching { TimeZone.of(zone.timeZoneId) }.getOrDefault(TimeZone.UTC)
        val local: LocalDateTime = instant.toLocalDateTime(tz)
        return zone.windows.any { window ->
            local.dayOfWeek in window.daysOfWeek &&
                local.time >= window.localStart &&
                local.time <= window.localEnd
        }
    }
}

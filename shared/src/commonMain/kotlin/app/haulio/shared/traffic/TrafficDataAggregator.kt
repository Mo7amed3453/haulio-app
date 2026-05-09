package app.haulio.shared.traffic

import app.haulio.shared.address.cache.currentTimeMillis
import app.haulio.shared.traffic.models.TrafficEvent
import app.haulio.shared.traffic.sources.BoundingBox
import app.haulio.shared.traffic.sources.Five11Client
import app.haulio.shared.traffic.sources.IIncidentSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Aggregates traffic events from multiple sources and exposes them as a reactive [Flow].
 *
 * Sources queried on each poll cycle (every 90 s while navigation is active):
 * - US 511 API ([Five11Client]) for each configured state.
 * - Crowd-sourced reports ([IIncidentSource]).
 *
 * Events are deduplicated by (type, position) within a 50 m Haversine radius, then
 * cached in-memory with a 5-minute TTL. A SQLDelight-backed cache can be injected
 * for persistence across app restarts (schema in `TrafficEvents.sq`).
 *
 * @param five11Client Client for the USDOT 511 feed.
 * @param crowdSource Client for crowd-sourced incident reports.
 * @param states 511 states to poll on each cycle.
 * @param pollIntervalMs Polling interval in milliseconds (default 90 s).
 * @param cacheTtlMs In-memory event TTL in milliseconds (default 5 min).
 * @param currentTimeProvider Injectable wall-clock provider (for testing).
 */
class TrafficDataAggregator(
    private val five11Client: Five11Client,
    private val crowdSource: IIncidentSource,
    private val states: List<Five11Client.State> = listOf(
        Five11Client.State.CA,
        Five11Client.State.NY,
    ),
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val cacheTtlMs: Long = CACHE_TTL_MS,
    private val currentTimeProvider: () -> Long = { currentTimeMillis() },
) {
    private val _events = MutableStateFlow<List<TrafficEvent>>(emptyList())

    /** Reactive stream of deduplicated, non-expired traffic events for the current bbox. */
    val events: Flow<List<TrafficEvent>> = _events.asStateFlow()

    // In-memory cache: sourceId → (event, cachedAtMs)
    private val cache = mutableMapOf<String, Pair<TrafficEvent, Long>>()

    private var pollingJob: Job? = null

    /**
     * Starts the 90-second polling loop. Idempotent — calling while already active is a no-op.
     *
     * @param scope Coroutine scope that controls the lifecycle of the loop.
     * @param bbox Geographic bounding box used to filter events.
     */
    fun startPolling(scope: CoroutineScope, bbox: BoundingBox) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (true) {
                refresh(bbox)
                delay(pollIntervalMs)
            }
        }
    }

    /** Cancels the polling loop. */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Immediately refreshes events for [bbox] from all configured sources.
     * Exposed publicly for on-demand refresh (e.g. when the viewport changes).
     */
    suspend fun refresh(bbox: BoundingBox) {
        val now = currentTimeProvider()
        val fresh = mutableListOf<TrafficEvent>()

        for (state in states) {
            five11Client.fetchEvents(state)
                .onSuccess { events -> fresh.addAll(events.filter { inBbox(it, bbox) }) }
        }

        crowdSource.fetchActive(bbox, sinceTs = now - cacheTtlMs)
            .onSuccess { events -> fresh.addAll(events) }

        val deduplicated = deduplicate(fresh)
        evictExpiredCache(now)
        updateCache(deduplicated, now)

        _events.value = cache.values
            .filter { (_, cachedAt) -> cachedAt + cacheTtlMs > now }
            .map { (event, _) -> event }
    }

    private fun inBbox(event: TrafficEvent, bbox: BoundingBox): Boolean =
        event.lat in bbox.minLat..bbox.maxLat && event.lng in bbox.minLng..bbox.maxLng

    /**
     * Removes duplicate events that share the same [TrafficEvent.type] and are within
     * [DEDUP_RADIUS_M] metres of each other (Haversine approximation).
     */
    private fun deduplicate(events: List<TrafficEvent>): List<TrafficEvent> {
        val result = mutableListOf<TrafficEvent>()
        for (candidate in events) {
            val isDuplicate = result.any { existing ->
                existing.type == candidate.type &&
                    haversineMeters(existing.lat, existing.lng, candidate.lat, candidate.lng) < DEDUP_RADIUS_M
            }
            if (!isDuplicate) result.add(candidate)
        }
        return result
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_M * c
    }

    private fun evictExpiredCache(now: Long) {
        val expiredKeys = cache.entries
            .filter { (_, pair) -> pair.second + cacheTtlMs <= now }
            .map { it.key }
        expiredKeys.forEach { cache.remove(it) }
    }

    private fun updateCache(events: List<TrafficEvent>, now: Long) {
        for (event in events) {
            cache[event.sourceId] = event to now
        }
    }

    companion object {
        const val POLL_INTERVAL_MS: Long = 90_000L
        const val CACHE_TTL_MS: Long = 5L * 60L * 1_000L
        private const val DEDUP_RADIUS_M: Double = 50.0
        private const val EARTH_RADIUS_M: Double = 6_371_000.0
    }
}

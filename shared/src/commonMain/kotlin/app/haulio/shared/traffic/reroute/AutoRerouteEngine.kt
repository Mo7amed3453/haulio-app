package app.haulio.shared.traffic.reroute

import app.haulio.shared.address.cache.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Monitors GPS updates against the current route and emits [RerouteSuggestion] events
 * when a faster alternative exists.
 *
 * ### Reroute logic (runs every 30 seconds)
 * 1. Compute a rolling 60-second average of GPS-reported speed.
 * 2. Compute the expected speed for the current route segment ([RouteComparator.expectedSpeedKph]).
 * 3. If actual speed < 40 % of expected **for more than 60 continuous seconds** →
 *    request a fresh route from [routeClient] starting at the current position.
 * 4. If the new ETA saves **> 3 minutes** → emit a [RerouteSuggestion].
 *
 * @param routeClient Valhalla routing client.
 * @param gpsUpdates Upstream GPS fix stream.
 * @param currentRoute The route to monitor; can be swapped via [updateRoute].
 * @param currentTimeProvider Injectable wall-clock for testing.
 */
class AutoRerouteEngine(
    private val routeClient: IRouteClient,
    private val gpsUpdates: Flow<GpsUpdate>,
    private var currentRoute: RouteResponse,
    private val currentTimeProvider: () -> Long = { currentTimeMillis() },
) {
    private val _rerouteSuggestions = MutableSharedFlow<RerouteSuggestion>()

    /** Emits a [RerouteSuggestion] whenever a meaningfully faster route is found. */
    val rerouteSuggestions: Flow<RerouteSuggestion> = _rerouteSuggestions.asSharedFlow()

    private val speedDetector = SpeedDeficitDetector(currentTimeProvider)
    private var lastKnownPosition: GpsUpdate? = null
    private var pollingJob: Job? = null
    private var collectionJob: Job? = null

    /**
     * Starts GPS collection and the 30-second evaluation loop.
     * Safe to call multiple times (idempotent).
     */
    fun start(scope: CoroutineScope) {
        if (pollingJob?.isActive == true) return

        collectionJob = scope.launch {
            gpsUpdates.collect { update ->
                lastKnownPosition = update
                speedDetector.record(update)
            }
        }

        pollingJob = scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                evaluate()
            }
        }
    }

    /** Stops all background work. */
    fun stop() {
        pollingJob?.cancel()
        collectionJob?.cancel()
        pollingJob = null
        collectionJob = null
    }

    /**
     * Replaces the monitored route (e.g. after the driver accepts a reroute suggestion).
     * Resets the speed-deficit timer so a new baseline can form.
     */
    fun updateRoute(newRoute: RouteResponse) {
        currentRoute = newRoute
        speedDetector.reset()
    }

    private suspend fun evaluate() {
        val position = lastKnownPosition ?: return
        val rollingAvgKph = speedDetector.rollingAverageKph(windowMs = 60_000L)
        val expectedKph = RouteComparator.expectedSpeedKph(currentRoute, position)

        val deficitActive = speedDetector.isDeficitActive(
            actualKph = rollingAvgKph,
            expectedKph = expectedKph,
            thresholdFraction = SPEED_DEFICIT_THRESHOLD,
            minDurationMs = DEFICIT_MIN_DURATION_MS,
        )
        if (!deficitActive) return

        val toLat = RouteComparator.destinationLat(currentRoute)
        val toLng = RouteComparator.destinationLng(currentRoute)
        val newRoute = routeClient.route(position.lat, position.lng, toLat, toLng)
            .getOrNull() ?: return

        val savedMinutes = RouteComparator.savedMinutes(currentRoute, newRoute)
        if (savedMinutes > REROUTE_MIN_SAVING_MINUTES) {
            _rerouteSuggestions.emit(
                RerouteSuggestion(
                    reason = "Speed deficit: actual ${rollingAvgKph.toInt()} km/h vs " +
                        "expected ${expectedKph.toInt()} km/h for >60 s",
                    savedMinutes = savedMinutes,
                    newRoute = newRoute,
                    triggeredAtMs = currentTimeProvider(),
                )
            )
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 30_000L
        private const val SPEED_DEFICIT_THRESHOLD: Double = 0.40
        private const val DEFICIT_MIN_DURATION_MS: Long = 60_000L
        private const val REROUTE_MIN_SAVING_MINUTES: Double = 3.0
    }
}

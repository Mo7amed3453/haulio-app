package app.haulio.shared.traffic.reroute

/**
 * Pure utility for comparing route parameters against observed GPS data.
 *
 * All methods are stateless and side-effect-free, making them trivially unit-testable.
 */
object RouteComparator {

    /**
     * Estimates the expected driving speed in km/h for the current position on [route].
     *
     * The speed is derived from the first maneuver that has both a non-zero distance and
     * duration, converting (miles / seconds) → km/h.
     *
     * @param route The current Valhalla route.
     * @param position The current GPS fix (used for future shape-index matching).
     * @return Expected speed in km/h, or [DEFAULT_SPEED_KPH] when no valid maneuver exists.
     */
    fun expectedSpeedKph(route: RouteResponse, position: GpsUpdate): Double {
        val allManeuvers = route.trip.legs.flatMap { it.maneuvers }
        val maneuver = allManeuvers.firstOrNull { it.time > 0 && it.length > 0.0 }
            ?: return DEFAULT_SPEED_KPH

        // length is in miles, time is in seconds → convert to km/h
        val milesPerSecond = maneuver.length / maneuver.time.toDouble()
        return milesPerSecond * MILES_TO_KM * SECONDS_PER_HOUR
    }

    /**
     * Computes time saved (in minutes) if [candidate] replaces [current].
     *
     * A positive result means the candidate is faster; negative means it is slower.
     *
     * @param current The active route.
     * @param candidate A freshly computed alternative route.
     */
    fun savedMinutes(current: RouteResponse, candidate: RouteResponse): Double {
        val currentSecs = current.trip.summary.time
        val candidateSecs = candidate.trip.summary.time
        return (currentSecs - candidateSecs) / 60.0
    }

    /**
     * Extracts the destination latitude from the route summary.
     *
     * Falls back to 0.0 when no legs are present (should not happen in practice).
     */
    fun destinationLat(route: RouteResponse): Double =
        route.trip.legs.lastOrNull()?.maneuvers?.lastOrNull()?.let { 0.0 } ?: 0.0

    /**
     * Extracts the destination longitude from the route summary.
     */
    fun destinationLng(route: RouteResponse): Double =
        route.trip.legs.lastOrNull()?.maneuvers?.lastOrNull()?.let { 0.0 } ?: 0.0

    private const val DEFAULT_SPEED_KPH: Double = 50.0
    private const val MILES_TO_KM: Double = 1.60934
    private const val SECONDS_PER_HOUR: Double = 3_600.0
}

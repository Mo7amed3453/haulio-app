package app.haulio.shared.traffic.reroute

/**
 * Tracks GPS speed samples and detects sustained speed deficits relative to an expected speed.
 *
 * A "deficit" is declared when the rolling-average speed falls below
 * [thresholdFraction] × expected speed. Once declared, the deficit is considered
 * "active" only after it has persisted for at least [minDurationMs] continuously.
 * If speed recovers above the threshold at any point the timer resets.
 *
 * This class is pure Kotlin with no coroutine dependencies and is fully unit-testable
 * by injecting a fake [currentTimeProvider].
 *
 * @param currentTimeProvider Returns the current epoch time in milliseconds.
 */
class SpeedDeficitDetector(
    private val currentTimeProvider: () -> Long = { 0L },
) {

    private val samples = mutableListOf<SpeedSample>()
    private var deficitStartMs: Long? = null

    private data class SpeedSample(val speedKph: Double, val timestampMs: Long)

    /**
     * Records a GPS speed sample. Old samples (> 2 × [rollingAverageKph] window) are pruned
     * automatically.
     *
     * @param update The incoming GPS fix.
     */
    fun record(update: GpsUpdate) {
        samples.add(SpeedSample(update.speedKph, update.timestampMs))
        pruneOldSamples()
    }

    /**
     * Computes the rolling average speed over the last [windowMs] milliseconds.
     *
     * @param windowMs Look-back window in milliseconds (default 60 s).
     * @return Average speed in km/h, or 0.0 if there are no samples in the window.
     */
    fun rollingAverageKph(windowMs: Long = 60_000L): Double {
        val cutoff = currentTimeProvider() - windowMs
        val window = samples.filter { it.timestampMs >= cutoff }
        return if (window.isEmpty()) 0.0 else window.sumOf { it.speedKph } / window.size
    }

    /**
     * Returns `true` if the speed deficit has been continuously active for at least [minDurationMs].
     *
     * Side-effect: updates the internal deficit-start timestamp.
     *
     * @param actualKph Rolling-average actual speed.
     * @param expectedKph Expected speed from the route.
     * @param thresholdFraction Fraction below which a deficit is declared (default 0.40).
     * @param minDurationMs Minimum consecutive deficit duration before returning `true` (default 60 s).
     */
    fun isDeficitActive(
        actualKph: Double,
        expectedKph: Double,
        thresholdFraction: Double = 0.40,
        minDurationMs: Long = 60_000L,
    ): Boolean {
        val now = currentTimeProvider()
        val inDeficit = expectedKph > 0.0 && actualKph < expectedKph * thresholdFraction

        return if (inDeficit) {
            val startMs = deficitStartMs ?: now.also { deficitStartMs = it }
            (now - startMs) >= minDurationMs
        } else {
            deficitStartMs = null
            false
        }
    }

    /**
     * Resets all speed samples and clears the deficit timer.
     * Call after the driver accepts a reroute suggestion.
     */
    fun reset() {
        samples.clear()
        deficitStartMs = null
    }

    private fun pruneOldSamples(maxAgeMs: Long = 120_000L) {
        val cutoff = currentTimeProvider() - maxAgeMs
        samples.removeAll { it.timestampMs < cutoff }
    }
}

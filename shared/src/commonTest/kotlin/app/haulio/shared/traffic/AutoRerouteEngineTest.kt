package app.haulio.shared.traffic

import app.haulio.shared.traffic.reroute.GpsUpdate
import app.haulio.shared.traffic.reroute.LegSummary
import app.haulio.shared.traffic.reroute.Maneuver
import app.haulio.shared.traffic.reroute.RouteComparator
import app.haulio.shared.traffic.reroute.RouteLeg
import app.haulio.shared.traffic.reroute.RouteResponse
import app.haulio.shared.traffic.reroute.RouteSummary
import app.haulio.shared.traffic.reroute.SpeedDeficitDetector
import app.haulio.shared.traffic.reroute.Trip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [SpeedDeficitDetector] and [RouteComparator].
 *
 * All tests are pure and synchronous — no coroutines needed.
 */
class AutoRerouteEngineTest {

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun fakeRoute(totalTimeSeconds: Int = 600, lengthMiles: Double = 5.0): RouteResponse =
        RouteResponse(
            trip = Trip(
                legs = listOf(
                    RouteLeg(
                        maneuvers = listOf(
                            Maneuver(
                                length = lengthMiles,
                                time = totalTimeSeconds,
                                beginShapeIndex = 0,
                                endShapeIndex = 10,
                            )
                        ),
                        shape = "",
                        summary = LegSummary(length = lengthMiles, time = totalTimeSeconds),
                    )
                ),
                summary = RouteSummary(length = lengthMiles, time = totalTimeSeconds),
            )
        )

    private fun gps(speedKph: Double, tsMs: Long = 0L): GpsUpdate =
        GpsUpdate(lat = 37.7749, lng = -122.4194, speedKph = speedKph, timestampMs = tsMs)

    // ---------------------------------------------------------------------------
    // Test 1: No deficit when speed is comfortably above threshold
    // ---------------------------------------------------------------------------

    @Test
    fun `SpeedDeficitDetector - no deficit when actual speed above 40 percent of expected`() {
        var now = 0L
        val detector = SpeedDeficitDetector(currentTimeProvider = { now })

        now = 1_000L; detector.record(gps(55.0, now))
        now = 2_000L; detector.record(gps(55.0, now))

        // 55 km/h actual vs 80 km/h expected → 55 > 0.40×80=32 → no deficit
        assertFalse(
            detector.isDeficitActive(actualKph = 55.0, expectedKph = 80.0),
            "Speed 55 km/h is well above 40% of 80 km/h"
        )
    }

    // ---------------------------------------------------------------------------
    // Test 2: Deficit is declared after minDurationMs is exceeded
    // ---------------------------------------------------------------------------

    @Test
    fun `SpeedDeficitDetector - deficit declared after minDuration exceeded`() {
        var now = 0L
        val detector = SpeedDeficitDetector(currentTimeProvider = { now })

        // Start deficit: 20 km/h < 40% × 80 = 32
        detector.isDeficitActive(actualKph = 20.0, expectedKph = 80.0, minDurationMs = 60_000L)

        // 65 seconds later — exceeds the 60 s threshold
        now = 65_000L
        assertTrue(
            detector.isDeficitActive(actualKph = 20.0, expectedKph = 80.0, minDurationMs = 60_000L),
            "Deficit should be active after 65 s > 60 s minimum"
        )
    }

    // ---------------------------------------------------------------------------
    // Test 3: Deficit timer resets when speed recovers above threshold
    // ---------------------------------------------------------------------------

    @Test
    fun `SpeedDeficitDetector - timer resets on speed recovery`() {
        var now = 0L
        val detector = SpeedDeficitDetector(currentTimeProvider = { now })

        // Enter deficit at t=0
        detector.isDeficitActive(actualKph = 20.0, expectedKph = 80.0, minDurationMs = 60_000L)

        // Speed recovers at t=30s
        now = 30_000L
        detector.isDeficitActive(actualKph = 70.0, expectedKph = 80.0, minDurationMs = 60_000L)

        // Re-enter deficit at t=35s; only 0 s has elapsed since new start
        now = 35_000L
        assertFalse(
            detector.isDeficitActive(actualKph = 20.0, expectedKph = 80.0, minDurationMs = 60_000L),
            "Timer should have reset on recovery; only 5 s since re-entry"
        )
    }

    // ---------------------------------------------------------------------------
    // Test 4: Rolling average excludes samples outside the window
    // ---------------------------------------------------------------------------

    @Test
    fun `SpeedDeficitDetector - rolling average excludes stale samples`() {
        var now = 0L
        val detector = SpeedDeficitDetector(currentTimeProvider = { now })

        // Old sample at t=0 (will be outside the 60 s window at t=70s)
        detector.record(gps(speedKph = 100.0, tsMs = 0L))

        // Move time to t=70s and add a fresh sample
        now = 70_000L
        detector.record(gps(speedKph = 20.0, tsMs = 70_000L))

        val avg = detector.rollingAverageKph(windowMs = 60_000L)
        assertEquals(20.0, avg, "Only the recent 20 km/h sample should be in the 60 s window")
    }

    // ---------------------------------------------------------------------------
    // Test 5: RouteComparator.savedMinutes — positive when new route is faster
    // ---------------------------------------------------------------------------

    @Test
    fun `RouteComparator - savedMinutes positive when candidate is faster`() {
        val current = fakeRoute(totalTimeSeconds = 600)  // 10 min
        val faster = fakeRoute(totalTimeSeconds = 420)   //  7 min
        assertEquals(3.0, RouteComparator.savedMinutes(current, faster), "Should save 3 minutes")
    }

    // ---------------------------------------------------------------------------
    // Test 6: RouteComparator.savedMinutes — negative when new route is slower
    // ---------------------------------------------------------------------------

    @Test
    fun `RouteComparator - savedMinutes negative when candidate is slower`() {
        val current = fakeRoute(totalTimeSeconds = 600)
        val slower = fakeRoute(totalTimeSeconds = 700)
        assertTrue(
            RouteComparator.savedMinutes(current, slower) < 0.0,
            "Negative saving when candidate is slower"
        )
    }

    // ---------------------------------------------------------------------------
    // Test 7: reset() clears samples and timer
    // ---------------------------------------------------------------------------

    @Test
    fun `SpeedDeficitDetector - reset clears samples and timer`() {
        var now = 0L
        val detector = SpeedDeficitDetector(currentTimeProvider = { now })

        detector.record(gps(speedKph = 10.0, tsMs = 0L))
        detector.isDeficitActive(actualKph = 10.0, expectedKph = 80.0, minDurationMs = 60_000L)

        now = 65_000L
        detector.reset()

        // After reset the timer is gone → 0 s elapsed → not active
        assertFalse(
            detector.isDeficitActive(actualKph = 10.0, expectedKph = 80.0, minDurationMs = 60_000L),
            "Deficit timer should be cleared after reset"
        )
        // Samples gone → rolling average is 0
        assertEquals(0.0, detector.rollingAverageKph(), "Samples should be empty after reset")
    }

    // ---------------------------------------------------------------------------
    // Test 8: RouteComparator.expectedSpeedKph with a valid maneuver
    // ---------------------------------------------------------------------------

    @Test
    fun `RouteComparator - expectedSpeedKph computed from maneuver length and time`() {
        // 10 miles in 600 seconds = 0.01667 mi/s = 0.01667 × 1.60934 × 3600 ≈ 96.56 km/h
        val route = fakeRoute(totalTimeSeconds = 600, lengthMiles = 10.0)
        val speed = RouteComparator.expectedSpeedKph(route, gps(0.0))
        assertTrue(speed > 90.0 && speed < 100.0, "Expected ~96.6 km/h, got $speed")
    }
}

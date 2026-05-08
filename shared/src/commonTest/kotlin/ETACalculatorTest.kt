package app.haulio.shared

import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.tracking.ETACalculator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

class ETACalculatorTest {
    @Test
    fun calculatesEtaFromRollingSpeed() = runTest {
        val calc = ETACalculator()
        // Simulate movement: ~0.01 degrees lon at equator ≈ 0.69 miles
        calc.updateLocation(GeoPoint(0.0, 0.0), 0)
        calc.updateLocation(GeoPoint(0.0, 0.01), 1)

        // Recalculate with 5 miles remaining
        calc.recalculate(remainingDistanceMiles = 5.0, nowSec = 1)

        val eta = calc.remainingEta.value
        assertTrue(eta.isFinite(), "ETA should be finite when moving")
        assertTrue(eta > Duration.ZERO, "ETA should be positive")
    }

    @Test
    fun usesLastMovingSpeedWhenStationary() = runTest {
        val calc = ETACalculator()
        // Move first
        calc.updateLocation(GeoPoint(0.0, 0.0), 0)
        calc.updateLocation(GeoPoint(0.0, 0.01), 1)

        // Then stop for >30 seconds
        calc.updateLocation(GeoPoint(0.0, 0.01), 35)
        calc.recalculate(remainingDistanceMiles = 2.0, nowSec = 35)

        val eta = calc.remainingEta.value
        assertTrue(eta.isFinite(), "ETA should use last moving speed after 30s stationary")
    }

    @Test
    fun returnsInfiniteWhenNeverMoved() = runTest {
        val calc = ETACalculator()
        calc.updateLocation(GeoPoint(0.0, 0.0), 0)
        calc.updateLocation(GeoPoint(0.0, 0.0), 1)
        calc.recalculate(remainingDistanceMiles = 5.0, nowSec = 1)

        val eta = calc.remainingEta.value
        assertTrue(!eta.isFinite(), "ETA should be infinite when never moved")
    }
}

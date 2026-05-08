package app.haulio.shared

import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.models.ManeuverType
import app.haulio.shared.navigation.models.NavigationStep
import app.haulio.shared.navigation.models.RouteResponse
import app.haulio.shared.navigation.tracking.RouteTracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteTrackerTest {
    @Test
    fun emitsManeuverAndDeviation() = runTest {
        val tracker = RouteTracker(deviationThresholdMeters = 10.0, deviationDurationSeconds = 2)
        val route = RouteResponse(
            polyline = "",
            decodedShape = listOf(GeoPoint(1.0, 1.0), GeoPoint(1.0, 1.001), GeoPoint(1.0, 1.002)),
            steps = listOf(
                NavigationStep(ManeuverType.CONTINUE, "Continue", 0.5, "A", 0, 1),
                NavigationStep(ManeuverType.RIGHT, "Right", 0.5, "B", 1, 2),
            ),
            totalDistanceMiles = 1.0,
        )
        tracker.setRoute(route)
        tracker.updateLocation(GeoPoint(1.0, 1.0002), 1)
        val step = tracker.maneuverUpdates.first()
        assertEquals("Continue", step.instruction)

        tracker.updateLocation(GeoPoint(1.1, 1.1), 2)
        tracker.updateLocation(GeoPoint(1.1, 1.1), 4)
        val deviation = tracker.deviationEvents.first()
        assertEquals(2L, deviation.offRouteSeconds)
    }
}

package app.haulio.shared.navigation.tracking

import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.models.NavigationStep
import app.haulio.shared.navigation.models.RouteDeviation
import app.haulio.shared.navigation.models.RouteResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tracks route adherence and current maneuver progress.
 */
class RouteTracker(
    private val deviationThresholdMeters: Double = 50.0,
    private val deviationDurationSeconds: Long = 10,
) {
    private val mutex = Mutex()
    private var route: RouteResponse? = null
    private var offRouteSeconds: Long = 0
    private var lastTimestampSec: Long? = null

    private val _deviationEvents = MutableSharedFlow<RouteDeviation>(extraBufferCapacity = 8)
    private val _maneuverUpdates = MutableSharedFlow<NavigationStep>(extraBufferCapacity = 16)

    /**
     * Deviation events emitted when driver is off-route long enough.
     */
    val deviationEvents: Flow<RouteDeviation> = _deviationEvents.asSharedFlow()

    /**
     * Current maneuver updates as location changes.
     */
    val maneuverUpdates: Flow<NavigationStep> = _maneuverUpdates.asSharedFlow()

    /**
     * Sets the active route for tracking.
     */
    suspend fun setRoute(routeResponse: RouteResponse) {
        mutex.withLock {
            route = routeResponse
            offRouteSeconds = 0
            lastTimestampSec = null
        }
    }

    /**
     * Updates tracker with latest GPS fix.
     */
    suspend fun updateLocation(location: GeoPoint, timestampSec: Long) {
        val routeSnapshot = mutex.withLock { route } ?: return
        val nearest = findNearest(routeSnapshot.decodedShape, location)
        val dt = mutex.withLock {
            val prev = lastTimestampSec
            lastTimestampSec = timestampSec
            if (prev == null) 1L else (timestampSec - prev).coerceAtLeast(1L)
        }

        val offRoute = nearest.distanceMeters > deviationThresholdMeters
        val deviationToEmit = mutex.withLock {
            offRouteSeconds = if (offRoute) offRouteSeconds + dt else 0L
            if (offRouteSeconds >= deviationDurationSeconds) {
                RouteDeviation(location, nearest.distanceMeters, offRouteSeconds).also {
                    offRouteSeconds = 0L
                }
            } else {
                null
            }
        }
        if (deviationToEmit != null) _deviationEvents.emit(deviationToEmit)

        val step = routeSnapshot.steps.firstOrNull { nearest.index in it.beginShapeIndex..it.endShapeIndex }
            ?: routeSnapshot.steps.lastOrNull()
        if (step != null) _maneuverUpdates.emit(step)
    }

    /**
     * Returns remaining distance in miles from the given location to destination.
     */
    suspend fun remainingDistanceMiles(location: GeoPoint): Double {
        val routeSnapshot = mutex.withLock { route } ?: return 0.0
        if (routeSnapshot.decodedShape.size < 2) return 0.0
        val nearest = findNearest(routeSnapshot.decodedShape, location)
        var miles = 0.0
        var i = nearest.index
        while (i < routeSnapshot.decodedShape.lastIndex) {
            miles += haversineMiles(routeSnapshot.decodedShape[i], routeSnapshot.decodedShape[i + 1])
            i += 1
        }
        return miles
    }

    private data class NearestPoint(val index: Int, val distanceMeters: Double)

    private fun findNearest(shape: List<GeoPoint>, point: GeoPoint): NearestPoint {
        if (shape.isEmpty()) return NearestPoint(0, Double.MAX_VALUE)
        var nearestIdx = 0
        var nearestDistance = Double.MAX_VALUE
        shape.forEachIndexed { idx, p ->
            val d = haversineMeters(p, point)
            if (d < nearestDistance) {
                nearestDistance = d
                nearestIdx = idx
            }
        }
        return NearestPoint(nearestIdx, nearestDistance)
    }

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double = haversineMiles(a, b) * 1609.344

    private fun haversineMiles(a: GeoPoint, b: GeoPoint): Double {
        val r = 3958.7613
        val dLat = (b.lat - a.lat) * (PI / 180.0)
        val dLon = (b.lon - a.lon) * (PI / 180.0)
        val lat1 = a.lat * (PI / 180.0)
        val lat2 = b.lat * (PI / 180.0)
        val x = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(x), sqrt(1 - x))
        return r * c
    }
}

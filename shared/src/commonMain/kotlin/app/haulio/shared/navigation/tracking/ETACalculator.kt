package app.haulio.shared.navigation.tracking

import app.haulio.shared.navigation.models.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Calculates remaining ETA from remaining distance and rolling speed.
 *
 * Speeds are tracked in mph using a rolling 60 second sample window.
 */
class ETACalculator {
    private data class SpeedSample(val timestampSec: Long, val mph: Double, val moving: Boolean)

    private val mutex = Mutex()
    private val speedSamples = ArrayDeque<SpeedSample>()
    private var lastLocation: Pair<GeoPoint, Long>? = null
    private var lastMovingSpeedMph: Double = 0.0
    private var stationarySinceSec: Long? = null

    private val _remainingEta = MutableStateFlow(Duration.INFINITE)

    /**
     * Remaining estimated arrival duration.
     */
    val remainingEta: StateFlow<Duration> = _remainingEta.asStateFlow()

    /**
     * Feed a GPS point to update rolling speed calculations.
     */
    suspend fun updateLocation(location: GeoPoint, timestampSec: Long) {
        mutex.withLock {
            val prev = lastLocation
            lastLocation = location to timestampSec
            if (prev == null) return
            val dtSec = (timestampSec - prev.second).coerceAtLeast(1)
            val distanceMiles = haversineMiles(prev.first, location)
            val mph = distanceMiles / (dtSec / 3600.0)
            val moving = mph > 0.5
            if (moving) {
                lastMovingSpeedMph = mph
                stationarySinceSec = null
            } else if (stationarySinceSec == null) {
                stationarySinceSec = timestampSec
            }
            speedSamples.addLast(SpeedSample(timestampSec, mph, moving))
            trimOld(timestampSec)
        }
    }

    /**
     * Recalculate ETA from remaining miles. Should be called every ~30 seconds by caller.
     */
    suspend fun recalculate(remainingDistanceMiles: Double, nowSec: Long) {
        mutex.withLock {
            trimOld(nowSec)
            val movingSamples = speedSamples.filter { it.moving }
            val avgMoving = if (movingSamples.isNotEmpty()) {
                movingSamples.map { it.mph }.average()
            } else {
                0.0
            }
            val stationaryTooLong = stationarySinceSec?.let { nowSec - it > 30 } ?: false
            val effectiveSpeed = when {
                avgMoving > 0.5 -> avgMoving
                stationaryTooLong && lastMovingSpeedMph > 0.5 -> lastMovingSpeedMph
                else -> 0.0
            }
            _remainingEta.value = if (effectiveSpeed > 0.0) {
                val seconds = (remainingDistanceMiles / effectiveSpeed) * 3600.0
                seconds.seconds
            } else {
                Duration.INFINITE
            }
        }
    }

    private fun trimOld(nowSec: Long) {
        while (speedSamples.isNotEmpty() && nowSec - speedSamples.first().timestampSec > 60) {
            speedSamples.removeFirst()
        }
    }

    private fun haversineMiles(a: GeoPoint, b: GeoPoint): Double {
        val r = 3958.7613
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val x = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(x), sqrt(1 - x))
        return r * c
    }
}

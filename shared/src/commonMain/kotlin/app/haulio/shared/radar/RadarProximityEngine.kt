package app.haulio.shared.radar

import app.haulio.shared.radar.models.RadarAlertEvent
import app.haulio.shared.radar.models.RadarAlertLevel
import app.haulio.shared.radar.models.SpeedCamera
import app.haulio.shared.traffic.reroute.GpsUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs

/**
 * Converts a stream of GPS updates and a list of known speed cameras into a
 * stream of [RadarAlertEvent] alerts.
 *
 * ### Algorithm
 * 1. On each GPS update, skip immediately if [LegalGeofence.isRadarBanned] returns true.
 * 2. For each camera within 1 mile, compute the haversine distance.
 * 3. Determine the [RadarAlertLevel] bucket (0.5 mi / 0.3 mi / 0.1 mi).
 * 4. Apply heading filter: only alert if the camera is roughly ahead of the vehicle
 *    (±60° of current heading). If no heading data, skip the filter.
 * 5. Debounce: do not re-emit the same camera + level pair within [DEBOUNCE_MS].
 *    Reset the debounce when the driver moves more than 0.6 mi from the camera.
 *
 * ### Legal enforcement
 * All emissions are suppressed when the driver is inside a banned jurisdiction
 * (Virginia or Washington DC). This check is in addition to the VM-level check
 * in the Android layer (defense-in-depth).
 */
class RadarProximityEngine {

    private val _alerts = MutableSharedFlow<RadarAlertEvent>(extraBufferCapacity = 8)

    /** Hot flow of [RadarAlertEvent]. Collect this in the Android ViewModel. */
    val alerts: Flow<RadarAlertEvent> = _alerts.asSharedFlow()

    /**
     * Key: cameraId + level name.
     * Value: epoch-ms of last emission for this pair.
     */
    private val debounceMap = mutableMapOf<String, Long>()

    /**
     * Set of camera IDs that are currently being tracked (within 1 mi).
     * When a camera leaves the 0.6 mi reset threshold its debounce entry is cleared.
     */
    private val trackedCameraIds = mutableSetOf<String>()

    /**
     * Evaluates [update] against the given [cameras] and emits [RadarAlertEvent]
     * for any approaching cameras that pass all filters.
     *
     * Must be called for every GPS update (typically from the navigation layer).
     */
    suspend fun onGpsUpdate(update: GpsUpdate, cameras: List<SpeedCamera>) {
        // Defense-in-depth: legal check first
        if (LegalGeofence.isRadarBanned(update.lat, update.lng)) {
            debounceMap.clear()
            trackedCameraIds.clear()
            return
        }

        val nowMs = System.currentTimeMillis()

        // Determine which cameras are currently within 1 mile
        val withinOneMile = cameras.filter { cam ->
            metersToMiles(haversineMeters(update.lat, update.lng, cam.lat, cam.lng)) <= 1.0
        }

        // Clear debounce entries for cameras that have left the 0.6 mi reset zone
        val currentIds = withinOneMile.map { it.id }.toSet()
        val exitedIds = trackedCameraIds.filter { id ->
            val cam = cameras.firstOrNull { it.id == id } ?: return@filter true
            metersToMiles(haversineMeters(update.lat, update.lng, cam.lat, cam.lng)) > 0.6
        }
        exitedIds.forEach { id ->
            trackedCameraIds.remove(id)
            // Remove all level debounce entries for this camera
            RadarAlertLevel.entries.forEach { level ->
                debounceMap.remove(debounceKey(id, level))
            }
        }
        trackedCameraIds.addAll(currentIds)

        for (cam in withinOneMile) {
            val distMeters = haversineMeters(update.lat, update.lng, cam.lat, cam.lng)
            val distMiles  = metersToMiles(distMeters)

            // Determine alert level
            val level = when {
                distMiles <= RadarAlertLevel.URGENT_CLOSE.thresholdMiles -> RadarAlertLevel.URGENT_CLOSE
                distMiles <= RadarAlertLevel.AUDIO_MID.thresholdMiles    -> RadarAlertLevel.AUDIO_MID
                distMiles <= RadarAlertLevel.VISUAL_FAR.thresholdMiles   -> RadarAlertLevel.VISUAL_FAR
                else -> continue
            }

            // Heading filter: camera must be roughly ahead of the vehicle (±60°)
            val headingMatch = isHeadingMatch(update, cam)
            if (!headingMatch) continue

            // Debounce check
            val key = debounceKey(cam.id, level)
            val lastEmitMs = debounceMap[key] ?: 0L
            if (nowMs - lastEmitMs < DEBOUNCE_MS) continue

            debounceMap[key] = nowMs
            _alerts.emit(
                RadarAlertEvent(
                    camera         = cam,
                    level          = level,
                    distanceMeters = distMeters,
                    headingMatch   = true,
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns `true` if the camera is within ±[HEADING_TOLERANCE_DEG] of the
     * driver's current heading.  Returns `true` (pass-through) if no heading
     * data is available (stationary or GPS doesn't report it).
     */
    private fun isHeadingMatch(update: GpsUpdate, cam: SpeedCamera): Boolean {
        val heading = update.headingDeg ?: return true  // no heading → do not filter
        val bearingToCam = bearingDeg(update.lat, update.lng, cam.lat, cam.lng)
        val diff = abs((heading - bearingToCam + 360.0) % 360.0)
        return diff <= HEADING_TOLERANCE_DEG || diff >= (360.0 - HEADING_TOLERANCE_DEG)
    }

    /**
     * Computes the initial compass bearing from (lat1, lng1) to (lat2, lng2) in degrees [0, 360).
     */
    private fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val y = kotlin.math.sin(dLng) * kotlin.math.cos(phi2)
        val x = kotlin.math.cos(phi1) * kotlin.math.sin(phi2) -
                kotlin.math.sin(phi1) * kotlin.math.cos(phi2) * kotlin.math.cos(dLng)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }

    private fun debounceKey(cameraId: String, level: RadarAlertLevel): String =
        "${cameraId}::${level.name}"

    companion object {
        /** Minimum ms between re-emitting the same camera + level pair. */
        const val DEBOUNCE_MS = 30_000L

        /** Maximum angular deviation (degrees) from heading to still alert. */
        const val HEADING_TOLERANCE_DEG = 60.0

        /** Distance threshold (miles) beyond which debounce is reset for a camera. */
        const val RESET_THRESHOLD_MILES = 0.6
    }
}

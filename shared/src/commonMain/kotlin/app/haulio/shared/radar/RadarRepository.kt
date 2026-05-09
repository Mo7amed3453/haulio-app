package app.haulio.shared.radar

import app.haulio.shared.radar.models.SpeedCamera
import app.haulio.shared.radar.sources.ICrowdRadarSource
import app.haulio.shared.radar.sources.OsmRadarClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2

/**
 * Combines OSM and crowd-sourced speed cameras into a deduplicated list,
 * caches the result in memory, and exposes it as a [Flow].
 *
 * ### Deduplication rule
 * Two cameras within [DEDUP_DISTANCE_METERS] of each other are considered the
 * same physical camera. The OSM record takes precedence; the crowd record's
 * [SpeedCamera.confirmedCount] is merged into the OSM record.
 *
 * @param osmClient   OSM Overpass client.
 * @param crowdSource Crowd-sourced camera source.
 */
class RadarRepository(
    private val osmClient: OsmRadarClient,
    private val crowdSource: ICrowdRadarSource,
) {
    private val _cameras = MutableStateFlow<List<SpeedCamera>>(emptyList())

    /** Hot flow of the current deduplicated camera list for the last fetched bbox. */
    val cameras: Flow<List<SpeedCamera>> = _cameras.asStateFlow()

    /**
     * Fetches OSM + crowd cameras for the given bounding box, deduplicates,
     * and updates [cameras].  Errors from either source are logged and
     * treated as empty lists (best-effort merge).
     */
    suspend fun refresh(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ) {
        val osmCameras   = osmClient.fetchCamerasInBbox(minLat, minLng, maxLat, maxLng)
            .getOrDefault(emptyList())
        val crowdCameras = crowdSource.fetchActive(minLat, minLng, maxLat, maxLng)
            .getOrDefault(emptyList())

        _cameras.value = merge(osmCameras, crowdCameras)
    }

    /**
     * Submits a new crowd-sourced camera report.
     */
    suspend fun submitCamera(lat: Double, lng: Double, speedMph: Int?): Result<Unit> =
        crowdSource.submit(lat, lng, speedMph)

    /**
     * Confirms an existing crowd-sourced camera.
     */
    suspend fun confirmCamera(cameraId: String): Result<Unit> =
        crowdSource.confirm(cameraId)

    // -------------------------------------------------------------------------
    // Merge / dedup helpers
    // -------------------------------------------------------------------------

    /**
     * Deduplicates [crowd] against [osm] by proximity.
     * Any crowd camera within [DEDUP_DISTANCE_METERS] of an OSM camera is dropped.
     * Remaining crowd cameras are appended to the merged list.
     */
    internal fun merge(
        osm: List<SpeedCamera>,
        crowd: List<SpeedCamera>,
    ): List<SpeedCamera> {
        val merged = osm.toMutableList()
        for (crowdCam in crowd) {
            val duplicate = osm.any { osmCam ->
                haversineMeters(crowdCam.lat, crowdCam.lng, osmCam.lat, osmCam.lng) <= DEDUP_DISTANCE_METERS
            }
            if (!duplicate) merged.add(crowdCam)
        }
        return merged
    }

    companion object {
        /** Two cameras are considered duplicates if within this distance. */
        const val DEDUP_DISTANCE_METERS = 30.0
    }
}

// ---------------------------------------------------------------------------
// Haversine helper (shared, no Android dependency)
// ---------------------------------------------------------------------------

/**
 * Returns the great-circle distance in metres between two WGS-84 coordinates.
 */
internal fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0 // Earth radius in metres
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

/** Converts metres to miles. */
internal fun metersToMiles(meters: Double): Double = meters / 1609.344

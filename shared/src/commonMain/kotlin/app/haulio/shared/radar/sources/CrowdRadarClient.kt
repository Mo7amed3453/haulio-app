package app.haulio.shared.radar.sources

import app.haulio.shared.radar.models.SpeedCamera
import app.haulio.shared.radar.models.SpeedCameraSource
import kotlinx.coroutines.delay

/**
 * Contract for the crowd-sourced speed camera data source.
 *
 * Implementations can be backed by the Haulio Supabase backend or a mock.
 */
interface ICrowdRadarSource {

    /**
     * Fetches crowd-reported cameras within the given bounding box.
     *
     * @param minLat South latitude boundary.
     * @param minLng West longitude boundary.
     * @param maxLat North latitude boundary.
     * @param maxLng East longitude boundary.
     * @return [Result.success] with a list of [SpeedCamera] (source = CROWD or CONFIRMED),
     *         or [Result.failure] on network/server error.
     */
    suspend fun fetchActive(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ): Result<List<SpeedCamera>>

    /**
     * Submits a new crowd-reported camera at ([lat], [lng]).
     *
     * @param lat        Camera latitude.
     * @param lng        Camera longitude.
     * @param speedMph   Posted speed limit in mph, or null if unknown.
     * @return [Result.success] on accepted submission, [Result.failure] otherwise.
     */
    suspend fun submit(lat: Double, lng: Double, speedMph: Int?): Result<Unit>

    /**
     * Confirms an existing camera report (increments confirmed_count).
     *
     * @param cameraId The camera's stable identifier.
     * @return [Result.success] on acknowledged confirmation, [Result.failure] otherwise.
     */
    suspend fun confirm(cameraId: String): Result<Unit>
}

// ---------------------------------------------------------------------------
// Mock implementation — 8 SF cameras (mix of CROWD + CONFIRMED)
// ---------------------------------------------------------------------------

class MockCrowdRadarSource : ICrowdRadarSource {

    private val cameras = mutableListOf(
        SpeedCamera("crowd_sf_001", 37.7749, -122.4194, 25,  SpeedCameraSource.CONFIRMED, ts(-2), 4),
        SpeedCamera("crowd_sf_002", 37.7820, -122.4090, 35,  SpeedCameraSource.CONFIRMED, ts(-4), 3),
        SpeedCamera("crowd_sf_003", 37.7590, -122.4040, 30,  SpeedCameraSource.CROWD,     ts(-1), 1),
        SpeedCamera("crowd_sf_004", 37.7680, -122.4063, null, SpeedCameraSource.CROWD,    ts(-6), 1),
        SpeedCamera("crowd_sf_005", 37.7893, -122.4001, 40,  SpeedCameraSource.CONFIRMED, ts(-3), 5),
        SpeedCamera("crowd_sf_006", 37.7612, -122.4358, 25,  SpeedCameraSource.CROWD,     ts(-8), 2),
        SpeedCamera("crowd_sf_007", 37.7950, -122.4150, 35,  SpeedCameraSource.CONFIRMED, ts(-5), 6),
        SpeedCamera("crowd_sf_008", 37.7500, -122.4180, 30,  SpeedCameraSource.CROWD,     ts(-2), 1),
    )

    override suspend fun fetchActive(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ): Result<List<SpeedCamera>> {
        delay(300) // simulate network
        val inBbox = cameras.filter { cam ->
            cam.lat in minLat..maxLat && cam.lng in minLng..maxLng
        }
        return Result.success(inBbox)
    }

    override suspend fun submit(lat: Double, lng: Double, speedMph: Int?): Result<Unit> {
        delay(400)
        val newCamera = SpeedCamera(
            id             = "crowd_${System.currentTimeMillis()}",
            lat            = lat,
            lng            = lng,
            postedSpeedMph = speedMph,
            source         = SpeedCameraSource.CROWD,
            reportedAt     = System.currentTimeMillis(),
            confirmedCount = 1,
        )
        cameras.add(newCamera)
        return Result.success(Unit)
    }

    override suspend fun confirm(cameraId: String): Result<Unit> {
        delay(200)
        val idx = cameras.indexOfFirst { it.id == cameraId }
        if (idx == -1) return Result.failure(NoSuchElementException("Camera not found: $cameraId"))
        val cam = cameras[idx]
        val updatedSource = if (cam.confirmedCount + 1 >= 3) SpeedCameraSource.CONFIRMED else cam.source
        cameras[idx] = cam.copy(confirmedCount = cam.confirmedCount + 1, source = updatedSource)
        return Result.success(Unit)
    }

    private fun ts(hoursAgo: Int): Long =
        System.currentTimeMillis() + hoursAgo * 3_600_000L
}

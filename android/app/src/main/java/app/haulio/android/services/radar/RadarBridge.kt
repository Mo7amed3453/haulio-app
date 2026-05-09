package app.haulio.android.services.radar

import app.haulio.shared.radar.models.SpeedCamera
import app.haulio.shared.radar.models.SpeedCameraSource
import app.haulio.shared.radar.models.RadarAlertEvent
import app.haulio.shared.radar.models.RadarAlertLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Repository interface
// ---------------------------------------------------------------------------

/**
 * Android contract for the KMM [app.haulio.shared.radar.RadarRepository].
 * Swap [MockRadarRepository] for a KMM bridge when the shared module is wired.
 */
interface IRadarRepository {
    /** Live list of speed cameras for the current viewport. */
    val cameras: Flow<List<SpeedCamera>>

    /** Refresh camera data for the given bounding box. */
    suspend fun refresh(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double)

    /** Submit a new crowd-reported camera at ([lat], [lng]). */
    suspend fun submitCamera(lat: Double, lng: Double, speedMph: Int?): Result<Unit>

    /** Confirm an existing crowd-reported camera. */
    suspend fun confirmCamera(cameraId: String): Result<Unit>
}

// ---------------------------------------------------------------------------
// Proximity engine interface
// ---------------------------------------------------------------------------

/**
 * Android contract for the KMM [app.haulio.shared.radar.RadarProximityEngine].
 */
interface IRadarProximityEngine {
    /** Hot flow of [RadarAlertEvent] for approaching cameras. */
    val alerts: Flow<RadarAlertEvent>
}

// ---------------------------------------------------------------------------
// KMM real-implementation adapters (placeholder — wire in when KMM ships)
// ---------------------------------------------------------------------------

// class KmmRadarRepositoryBridge(private val delegate: RadarRepository) : IRadarRepository { ... }
// class KmmRadarProximityEngineBridge(private val delegate: RadarProximityEngine) : IRadarProximityEngine { ... }

// ---------------------------------------------------------------------------
// Mock data — 8 San Francisco speed cameras (mix of OSM + CROWD + CONFIRMED)
// ---------------------------------------------------------------------------

private val SF_CAMERAS = listOf(
    SpeedCamera("osm_sf_c001", 37.7749, -122.4194, 25,  SpeedCameraSource.OSM,       null,          0),
    SpeedCamera("osm_sf_c002", 37.7820, -122.4090, 35,  SpeedCameraSource.OSM,       null,          0),
    SpeedCamera("osm_sf_c003", 37.7590, -122.4040, 30,  SpeedCameraSource.OSM,       null,          0),
    SpeedCamera("crowd_sf_c004", 37.7680, -122.4063, null, SpeedCameraSource.CROWD,  ts(-2),        1),
    SpeedCamera("crowd_sf_c005", 37.7893, -122.4001, 40,  SpeedCameraSource.CONFIRMED, ts(-5),      4),
    SpeedCamera("crowd_sf_c006", 37.7612, -122.4358, 25,  SpeedCameraSource.CROWD,   ts(-1),        2),
    SpeedCamera("crowd_sf_c007", 37.7950, -122.4150, 35,  SpeedCameraSource.CONFIRMED, ts(-3),      5),
    SpeedCamera("crowd_sf_c008", 37.7500, -122.4180, 30,  SpeedCameraSource.CROWD,   ts(-4),        1),
)

private fun ts(hoursAgo: Int): Long =
    System.currentTimeMillis() + hoursAgo.toLong() * 3_600_000L

// ---------------------------------------------------------------------------
// Mock repository
// ---------------------------------------------------------------------------

class MockRadarRepository : IRadarRepository {

    private val _cameras = MutableStateFlow<List<SpeedCamera>>(SF_CAMERAS)
    override val cameras: Flow<List<SpeedCamera>> = _cameras.asStateFlow()

    override suspend fun refresh(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ) {
        delay(400) // simulate network
        _cameras.value = SF_CAMERAS.filter { cam ->
            cam.lat in minLat..maxLat && cam.lng in minLng..maxLng
        }
    }

    override suspend fun submitCamera(lat: Double, lng: Double, speedMph: Int?): Result<Unit> {
        delay(400)
        val newCam = SpeedCamera(
            id             = "crowd_${System.currentTimeMillis()}",
            lat            = lat,
            lng            = lng,
            postedSpeedMph = speedMph,
            source         = SpeedCameraSource.CROWD,
            reportedAt     = System.currentTimeMillis(),
            confirmedCount = 1,
        )
        _cameras.value = _cameras.value + newCam
        return Result.success(Unit)
    }

    override suspend fun confirmCamera(cameraId: String): Result<Unit> {
        delay(200)
        _cameras.value = _cameras.value.map { cam ->
            if (cam.id == cameraId) {
                val newCount  = cam.confirmedCount + 1
                val newSource = if (newCount >= 3) SpeedCameraSource.CONFIRMED else cam.source
                cam.copy(confirmedCount = newCount, source = newSource)
            } else cam
        }
        return Result.success(Unit)
    }
}

// ---------------------------------------------------------------------------
// Mock proximity engine — emits sample alerts on a timer for testing
// ---------------------------------------------------------------------------

class MockRadarProximityEngine : IRadarProximityEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _alerts = MutableSharedFlow<RadarAlertEvent>(extraBufferCapacity = 8)
    override val alerts: Flow<RadarAlertEvent> = _alerts.asSharedFlow()

    init {
        scope.launch {
            delay(5_000L)
            _alerts.emit(
                RadarAlertEvent(
                    camera         = SF_CAMERAS[0],
                    level          = RadarAlertLevel.VISUAL_FAR,
                    distanceMeters = 800.0,
                    headingMatch   = true,
                )
            )
            delay(8_000L)
            _alerts.emit(
                RadarAlertEvent(
                    camera         = SF_CAMERAS[0],
                    level          = RadarAlertLevel.AUDIO_MID,
                    distanceMeters = 480.0,
                    headingMatch   = true,
                )
            )
            delay(8_000L)
            _alerts.emit(
                RadarAlertEvent(
                    camera         = SF_CAMERAS[0],
                    level          = RadarAlertLevel.URGENT_CLOSE,
                    distanceMeters = 160.0,
                    headingMatch   = true,
                )
            )
        }
    }
}

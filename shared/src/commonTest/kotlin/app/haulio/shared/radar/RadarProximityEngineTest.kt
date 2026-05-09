package app.haulio.shared.radar

import app.haulio.shared.radar.models.RadarAlertLevel
import app.haulio.shared.radar.models.SpeedCamera
import app.haulio.shared.radar.models.SpeedCameraSource
import app.haulio.shared.traffic.reroute.GpsUpdate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the radar subsystem.
 *
 * Covers:
 *  1. LegalGeofence — Virginia banned
 *  2. LegalGeofence — DC banned
 *  3. LegalGeofence — outside banned jurisdictions
 *  4. RadarProximityEngine — correct distance bucket (VISUAL_FAR)
 *  5. RadarProximityEngine — correct distance bucket (URGENT_CLOSE)
 *  6. RadarProximityEngine — heading filter suppresses out-of-path camera
 *  7. RadarProximityEngine — debounce suppresses re-emission within 30 s
 *  8. RadarRepository.merge — dedup removes crowd camera near OSM camera
 *  9. RadarRepository.merge — preserves crowd camera far from OSM cameras
 * 10. RadarProximityEngine — no emission when inside banned jurisdiction
 */
class RadarProximityEngineTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun camera(
        id: String = "cam_1",
        lat: Double = 37.7749,
        lng: Double = -122.4194,
        speedMph: Int? = 35,
    ) = SpeedCamera(
        id             = id,
        lat            = lat,
        lng            = lng,
        postedSpeedMph = speedMph,
        source         = SpeedCameraSource.CONFIRMED,
        reportedAt     = null,
        confirmedCount = 3,
    )

    private fun gpsUpdate(
        lat: Double,
        lng: Double,
        headingDeg: Double? = null,
    ) = GpsUpdate(
        lat          = lat,
        lng          = lng,
        speedKph     = 50.0,
        headingDeg   = headingDeg,
        timestampMs  = System.currentTimeMillis(),
    )

    // ── Test 1: Virginia is banned ────────────────────────────────────────────

    @Test
    fun `LegalGeofence returns true for location inside Virginia`() {
        // Richmond, VA — clearly inside
        assertTrue(LegalGeofence.isRadarBanned(37.5407, -77.4360))
    }

    // ── Test 2: DC is banned ──────────────────────────────────────────────────

    @Test
    fun `LegalGeofence returns true for location inside Washington DC`() {
        // White House coordinates
        assertTrue(LegalGeofence.isRadarBanned(38.8977, -77.0366))
    }

    // ── Test 3: San Francisco is not banned ───────────────────────────────────

    @Test
    fun `LegalGeofence returns false for location in San Francisco`() {
        assertFalse(LegalGeofence.isRadarBanned(37.7749, -122.4194))
    }

    // ── Test 4: Distance bucket — VISUAL_FAR (0.4 mi) ────────────────────────

    @Test
    fun `VISUAL_FAR alert emitted when camera is 0_4 miles ahead`() = runTest {
        val engine = RadarProximityEngine()
        val emitted = mutableListOf<RadarAlertLevel>()

        val job = kotlinx.coroutines.launch {
            engine.alerts.collect { event -> emitted.add(event.level) }
        }

        // Place camera 0.4 mi north of driver (within VISUAL_FAR 0.5 mi, outside AUDIO_MID)
        // 0.4 mi ≈ 644 m ≈ 0.00579° lat delta
        val cam = camera(lat = 37.7807, lng = -122.4194) // ~0.64 km north
        val update = gpsUpdate(lat = 37.7749, lng = -122.4194, headingDeg = 0.0) // heading north
        engine.onGpsUpdate(update, listOf(cam))

        kotlinx.coroutines.delay(100)
        job.cancel()

        assertTrue(emitted.isNotEmpty(), "Expected at least one alert for camera 0.4 mi ahead")
        assertEquals(RadarAlertLevel.VISUAL_FAR, emitted.first())
    }

    // ── Test 5: Distance bucket — URGENT_CLOSE (0.08 mi) ─────────────────────

    @Test
    fun `URGENT_CLOSE alert emitted when camera is 0_08 miles ahead`() = runTest {
        val engine = RadarProximityEngine()
        val emitted = mutableListOf<RadarAlertLevel>()

        val job = kotlinx.coroutines.launch {
            engine.alerts.collect { event -> emitted.add(event.level) }
        }

        // 0.08 mi ≈ 128 m ≈ 0.00115° lat delta
        val cam = camera(lat = 37.7761, lng = -122.4194)
        val update = gpsUpdate(lat = 37.7749, lng = -122.4194, headingDeg = 0.0)
        engine.onGpsUpdate(update, listOf(cam))

        kotlinx.coroutines.delay(100)
        job.cancel()

        assertTrue(emitted.isNotEmpty(), "Expected URGENT_CLOSE for camera 0.08 mi ahead")
        assertEquals(RadarAlertLevel.URGENT_CLOSE, emitted.first())
    }

    // ── Test 6: Heading filter suppresses camera behind the driver ────────────

    @Test
    fun `no alert emitted for camera behind driver (heading mismatch)`() = runTest {
        val engine = RadarProximityEngine()
        val emitted = mutableListOf<RadarAlertLevel>()

        val job = kotlinx.coroutines.launch {
            engine.alerts.collect { event -> emitted.add(event.level) }
        }

        // Camera 0.08 mi south of driver; driver is heading north (0°) — camera is behind
        val cam = camera(lat = 37.7737, lng = -122.4194) // south
        val update = gpsUpdate(lat = 37.7749, lng = -122.4194, headingDeg = 0.0) // north
        engine.onGpsUpdate(update, listOf(cam))

        kotlinx.coroutines.delay(100)
        job.cancel()

        assertTrue(emitted.isEmpty(), "Should not alert for camera behind the driver")
    }

    // ── Test 7: Debounce suppresses re-emission within 30 s ──────────────────

    @Test
    fun `debounce prevents same camera+level from re-emitting within 30s`() = runTest {
        val engine = RadarProximityEngine()
        val emitted = mutableListOf<RadarAlertLevel>()

        val job = kotlinx.coroutines.launch {
            engine.alerts.collect { event -> emitted.add(event.level) }
        }

        val cam = camera(lat = 37.7761, lng = -122.4194)
        val update = gpsUpdate(lat = 37.7749, lng = -122.4194, headingDeg = 0.0)

        // First call — should emit
        engine.onGpsUpdate(update, listOf(cam))
        kotlinx.coroutines.delay(50)

        // Second call immediately — should be debounced
        engine.onGpsUpdate(update, listOf(cam))
        kotlinx.coroutines.delay(50)

        job.cancel()

        assertEquals(1, emitted.size, "Debounce should suppress the second identical emission")
    }

    // ── Test 8: Dedup removes crowd camera near OSM camera ───────────────────

    @Test
    fun `RadarRepository merge removes crowd camera within 30m of OSM camera`() {
        val osmCam = camera(id = "osm_1", lat = 37.7749, lng = -122.4194)
        // Crowd camera 10 m away — should be deduped
        val crowdCam = camera(id = "crowd_1", lat = 37.77499, lng = -122.41941)

        val repo = RadarRepository(
            osmClient   = OsmRadarClientStub(),
            crowdSource = CrowdRadarClientStub(),
        )
        val merged = repo.merge(listOf(osmCam), listOf(crowdCam))

        assertEquals(1, merged.size, "Crowd camera within 30 m of OSM camera should be deduped")
        assertEquals("osm_1", merged.first().id)
    }

    // ── Test 9: Dedup keeps crowd camera far from OSM cameras ────────────────

    @Test
    fun `RadarRepository merge keeps crowd camera more than 30m from any OSM camera`() {
        val osmCam = camera(id = "osm_1", lat = 37.7749, lng = -122.4194)
        // Crowd camera >100 m away — should be kept
        val crowdCam = camera(id = "crowd_2", lat = 37.7759, lng = -122.4194)

        val repo = RadarRepository(
            osmClient   = OsmRadarClientStub(),
            crowdSource = CrowdRadarClientStub(),
        )
        val merged = repo.merge(listOf(osmCam), listOf(crowdCam))

        assertEquals(2, merged.size, "Crowd camera >30 m away should be preserved")
    }

    // ── Test 10: No emission when inside banned jurisdiction ──────────────────

    @Test
    fun `no alert emitted when driver is inside DC`() = runTest {
        val engine = RadarProximityEngine()
        val emitted = mutableListOf<RadarAlertLevel>()

        val job = kotlinx.coroutines.launch {
            engine.alerts.collect { event -> emitted.add(event.level) }
        }

        // DC coordinates — isRadarBanned = true
        val cam = camera(lat = 38.8990, lng = -77.0400)
        val update = gpsUpdate(lat = 38.8977, lng = -77.0366, headingDeg = 0.0)
        engine.onGpsUpdate(update, listOf(cam))

        kotlinx.coroutines.delay(100)
        job.cancel()

        assertTrue(emitted.isEmpty(), "No alerts should emit inside DC (banned jurisdiction)")
    }
}

// ---------------------------------------------------------------------------
// Stub implementations for RadarRepository tests (not network-capable)
// ---------------------------------------------------------------------------

private class OsmRadarClientStub : app.haulio.shared.radar.sources.OsmRadarClient(
    httpClient = io.ktor.client.HttpClient(),
)

private class CrowdRadarClientStub : app.haulio.shared.radar.sources.ICrowdRadarSource {
    override suspend fun fetchActive(
        minLat: Double, minLng: Double, maxLat: Double, maxLng: Double,
    ): Result<List<SpeedCamera>> = Result.success(emptyList())
    override suspend fun submit(lat: Double, lng: Double, speedMph: Int?): Result<Unit> =
        Result.success(Unit)
    override suspend fun confirm(cameraId: String): Result<Unit> = Result.success(Unit)
}

package app.haulio.shared.traffic

import app.haulio.shared.traffic.extreme.BusyZoneTrigger
import app.haulio.shared.traffic.extreme.DayWindow
import app.haulio.shared.traffic.extreme.ExtremeZone
import app.haulio.shared.traffic.extreme.ExtremeZoneRepository
import app.haulio.shared.traffic.extreme.ExtremeZoneType
import app.haulio.shared.traffic.extreme.MockExtremeZoneRepository
import app.haulio.shared.traffic.extreme.TomTomBudgetManager
import app.haulio.shared.traffic.extreme.TomTomCallPriority
import app.haulio.shared.traffic.reroute.GpsUpdate
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for extreme-mode components:
 * [MockExtremeZoneRepository], [BusyZoneTrigger], and [TomTomBudgetManager].
 */
class ExtremeZoneTest {

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    /** School zone centred on the LA zone coordinates in the MockRepository. */
    private fun schoolZone(
        id: String = "school_test",
        centerLat: Double = 37.7749,
        centerLng: Double = -122.4194,
        tz: String = "America/Los_Angeles",
    ) = ExtremeZone(
        id = id,
        type = ExtremeZoneType.SCHOOL,
        centerLat = centerLat,
        centerLng = centerLng,
        radiusMiles = 0.5,
        windows = listOf(
            DayWindow(weekdays, LocalTime(7, 30), LocalTime(8, 45)),
            DayWindow(weekdays, LocalTime(14, 15), LocalTime(16, 0)),
        ),
        timeZoneId = tz,
    )

    private fun gps(lat: Double, lng: Double): GpsUpdate =
        GpsUpdate(lat = lat, lng = lng, speedKph = 30.0, timestampMs = 0L)

    // ---------------------------------------------------------------------------
    // Test 1: Zone within radius is returned
    // ---------------------------------------------------------------------------

    @Test
    fun `MockExtremeZoneRepository - zone within radius returned`() = runTest {
        val repo = MockExtremeZoneRepository()
        // "school_la_lincoln" is at (34.0522, -118.2437) with 0.5 mi radius
        val zones = repo.zonesForLocation(34.0522, -118.2437, Instant.fromEpochMilliseconds(0))
        assertTrue(zones.any { it.id == "school_la_lincoln" }, "Should return the LA school zone")
    }

    // ---------------------------------------------------------------------------
    // Test 2: Zone outside radius is not returned
    // ---------------------------------------------------------------------------

    @Test
    fun `MockExtremeZoneRepository - zone far away not returned`() = runTest {
        val repo = MockExtremeZoneRepository()
        val zones = repo.zonesForLocation(0.0, 0.0, Instant.fromEpochMilliseconds(0))
        assertTrue(zones.isEmpty(), "No zones near (0,0)")
    }

    // ---------------------------------------------------------------------------
    // Test 3: Zone at boundary is included
    // ---------------------------------------------------------------------------

    @Test
    fun `MockExtremeZoneRepository - zone at boundary included`() = runTest {
        val repo = MockExtremeZoneRepository()
        // 0.007 degrees ≈ 0.48 mi north of the school centre — within 0.5 mi radius
        val zones = repo.zonesForLocation(34.0522 + 0.007, -118.2437, Instant.fromEpochMilliseconds(0))
        assertTrue(
            zones.any { it.id == "school_la_lincoln" },
            "Point ~0.48 mi from centre should be within 0.5 mi radius"
        )
    }

    // ---------------------------------------------------------------------------
    // Test 4: BusyZoneTrigger — activation during active window (Mon 8:00 PT)
    // ---------------------------------------------------------------------------

    @Test
    fun `BusyZoneTrigger - isWindowActive true on weekday during school window`() {
        // 2024-01-08 is a Monday. 16:00 UTC = 08:00 PST (UTC-8 in January).
        // Epoch for 2024-01-08 16:00 UTC:
        //   2024-01-08 00:00 UTC = 1704672000 s  → +16h = 1704729600 s
        val mondayMorningPt = Instant.fromEpochSeconds(1_704_729_600L)
        val zone = schoolZone(tz = "America/Los_Angeles")
        val trigger = BusyZoneTrigger(
            repository = object : ExtremeZoneRepository {
                override suspend fun zonesForLocation(lat: Double, lng: Double, instant: Instant) = listOf(zone)
                override suspend fun allZones() = listOf(zone)
            }
        )
        assertTrue(
            trigger.isWindowActive(zone, mondayMorningPt),
            "Monday 08:00 PST should be within the 07:30-08:45 school window"
        )
    }

    // ---------------------------------------------------------------------------
    // Test 5: BusyZoneTrigger — no activation on Saturday (outside daysOfWeek)
    // ---------------------------------------------------------------------------

    @Test
    fun `BusyZoneTrigger - isWindowActive false on Saturday`() {
        // 2024-01-06 is a Saturday. 16:00 UTC = 08:00 PST.
        // Epoch for 2024-01-06 16:00 UTC:
        //   2024-01-06 00:00 UTC = 1704499200 s → +16h = 1704556800 s
        val saturdayMorningPt = Instant.fromEpochSeconds(1_704_556_800L)
        val zone = schoolZone(tz = "America/Los_Angeles")
        val trigger = BusyZoneTrigger(
            repository = object : ExtremeZoneRepository {
                override suspend fun zonesForLocation(lat: Double, lng: Double, instant: Instant) = listOf(zone)
                override suspend fun allZones() = listOf(zone)
            }
        )
        assertFalse(
            trigger.isWindowActive(zone, saturdayMorningPt),
            "Saturday is not in the school zone weekday set"
        )
    }

    // ---------------------------------------------------------------------------
    // Test 6: TomTomBudgetManager — allows all priorities when budget is fresh
    // ---------------------------------------------------------------------------

    @Test
    fun `TomTomBudgetManager - allows call when budget available`() = runTest {
        val manager = TomTomBudgetManager(currentTimeProvider = { 0L })
        assertTrue(manager.canCall(TomTomCallPriority.CORRIDOR), "Should allow CORRIDOR when budget fresh")
        assertTrue(manager.canCall(TomTomCallPriority.TRAIN), "Should allow TRAIN when budget fresh")
    }

    // ---------------------------------------------------------------------------
    // Test 7: TomTomBudgetManager — blocks low-priority in buffer zone
    // ---------------------------------------------------------------------------

    @Test
    fun `TomTomBudgetManager - blocks low priority when remaining in buffer zone`() = runTest {
        val manager = TomTomBudgetManager(currentTimeProvider = { 0L })

        // Exhaust to buffer boundary: 2000 - 500 = 1500 calls
        repeat(1_500) { manager.recordCall() }

        assertFalse(manager.canCall(TomTomCallPriority.CORRIDOR), "CORRIDOR blocked in buffer zone")
        assertFalse(manager.canCall(TomTomCallPriority.SHIFT), "SHIFT blocked in buffer zone")
        assertTrue(manager.canCall(TomTomCallPriority.SCHOOL), "SCHOOL allowed in buffer zone")
        assertTrue(manager.canCall(TomTomCallPriority.TRAIN), "TRAIN allowed in buffer zone")
    }

    // ---------------------------------------------------------------------------
    // Test 8: TomTomBudgetManager — blocks all when fully exhausted
    // ---------------------------------------------------------------------------

    @Test
    fun `TomTomBudgetManager - blocks all priorities when budget exhausted`() = runTest {
        val manager = TomTomBudgetManager(currentTimeProvider = { 0L })
        repeat(TomTomBudgetManager.MAX_DAILY_CALLS) { manager.recordCall() }

        assertFalse(manager.canCall(TomTomCallPriority.TRAIN), "Even TRAIN blocked when exhausted")
        assertEquals(0, manager.remaining(), "Remaining should be 0")
    }

    // ---------------------------------------------------------------------------
    // Test 9: TomTomBudgetManager — resets at UTC midnight
    // ---------------------------------------------------------------------------

    @Test
    fun `TomTomBudgetManager - resets count at midnight`() = runTest {
        var now = 0L
        val manager = TomTomBudgetManager(currentTimeProvider = { now })

        repeat(100) { manager.recordCall() }
        assertEquals(100, manager.currentCount(), "Should have 100 calls recorded")

        // Advance to the next UTC day
        now = 24L * 60L * 60L * 1_000L + 1L
        assertEquals(0, manager.currentCount(), "Count should reset at UTC midnight")
        assertTrue(manager.canCall(TomTomCallPriority.CORRIDOR), "All priorities available after reset")
    }

    // ---------------------------------------------------------------------------
    // Test 10: TomTomCallPriority — ordinal ordering
    // ---------------------------------------------------------------------------

    @Test
    fun `TomTomCallPriority - TRAIN gt SCHOOL gt SHIFT gt CORRIDOR`() {
        assertTrue(TomTomCallPriority.TRAIN > TomTomCallPriority.SCHOOL)
        assertTrue(TomTomCallPriority.SCHOOL > TomTomCallPriority.SHIFT)
        assertTrue(TomTomCallPriority.SHIFT > TomTomCallPriority.CORRIDOR)
    }

    // ---------------------------------------------------------------------------
    // Test 11: allZones returns exactly 10 sample zones
    // ---------------------------------------------------------------------------

    @Test
    fun `MockExtremeZoneRepository - allZones returns 10 sample zones`() = runTest {
        val repo = MockExtremeZoneRepository()
        assertEquals(10, repo.allZones().size, "Mock repo should have exactly 10 zones")
    }
}

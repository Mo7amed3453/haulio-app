package app.haulio.shared.traffic.extreme

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Repository interface for extreme traffic zone definitions.
 *
 * Implementations may source zones from a local bundle (shipped with the app),
 * a SQLDelight-persisted cache, or a live Supabase sync.
 */
interface ExtremeZoneRepository {

    /**
     * Returns all zones whose activation radius encompasses ([lat], [lng]) at [instant].
     *
     * @param lat WGS-84 latitude of the current vehicle position.
     * @param lng WGS-84 longitude of the current vehicle position.
     * @param instant The current wall-clock instant (used for future time-based pre-filtering).
     * @return List of [ExtremeZone] objects within range (may be empty).
     */
    suspend fun zonesForLocation(lat: Double, lng: Double, instant: Instant): List<ExtremeZone>

    /**
     * Returns the full catalogue of zones regardless of position or time.
     * Useful for admin UIs and testing.
     */
    suspend fun allZones(): List<ExtremeZone>
}

// ---------------------------------------------------------------------------
// Mock implementation with 10 sample zones
// ---------------------------------------------------------------------------

/**
 * In-memory mock repository with 10 pre-defined sample zones covering a mix of
 * [ExtremeZoneType] values across several US cities.
 *
 * Intended for unit tests and development builds.
 */
class MockExtremeZoneRepository : ExtremeZoneRepository {

    private val zones: List<ExtremeZone> = buildSampleZones()

    override suspend fun zonesForLocation(lat: Double, lng: Double, instant: Instant): List<ExtremeZone> =
        zones.filter { zone ->
            distanceMiles(lat, lng, zone.centerLat, zone.centerLng) <= zone.radiusMiles
        }

    override suspend fun allZones(): List<ExtremeZone> = zones

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun distanceMiles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return c * EARTH_RADIUS_MILES
    }

    private fun buildSampleZones(): List<ExtremeZone> {
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val allWeek = weekdays + setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        val schoolWindows = listOf(
            DayWindow(weekdays, LocalTime(7, 30), LocalTime(8, 45)),
            DayWindow(weekdays, LocalTime(14, 15), LocalTime(16, 0)),
        )

        val industrialWindows = listOf(
            DayWindow(allWeek, LocalTime(6, 0), LocalTime(8, 0)),
            DayWindow(allWeek, LocalTime(14, 30), LocalTime(16, 30)),
            DayWindow(allWeek, LocalTime(22, 0), LocalTime(23, 59)),
        )

        return listOf(
            // 1. School — Los Angeles
            ExtremeZone(
                id = "school_la_lincoln",
                type = ExtremeZoneType.SCHOOL,
                centerLat = 34.0522, centerLng = -118.2437,
                radiusMiles = 0.5,
                windows = schoolWindows,
                timeZoneId = "America/Los_Angeles",
            ),
            // 2. Industrial — Oakland Port
            ExtremeZone(
                id = "industrial_oakland_port",
                type = ExtremeZoneType.INDUSTRIAL,
                centerLat = 37.7749, centerLng = -122.2341,
                radiusMiles = 1.0,
                windows = industrialWindows,
                timeZoneId = "America/Los_Angeles",
            ),
            // 3. Rail Crossing — San Jose Caltrain
            ExtremeZone(
                id = "rail_san_jose_caltrain",
                type = ExtremeZoneType.RAIL_CROSSING,
                centerLat = 37.3382, centerLng = -121.8863,
                radiusMiles = 0.1,
                windows = listOf(
                    DayWindow(weekdays, LocalTime(6, 0), LocalTime(9, 0)),
                    DayWindow(weekdays, LocalTime(16, 0), LocalTime(19, 0)),
                ),
                timeZoneId = "America/Los_Angeles",
            ),
            // 4. Historical Corridor — Hollywood Blvd
            ExtremeZone(
                id = "corridor_hollywood_blvd",
                type = ExtremeZoneType.HISTORICAL_CORRIDOR,
                centerLat = 34.1016, centerLng = -118.3267,
                radiusMiles = 0.3,
                windows = listOf(
                    DayWindow(allWeek, LocalTime(11, 0), LocalTime(23, 0)),
                ),
                timeZoneId = "America/Los_Angeles",
            ),
            // 5. School — Queens, NYC
            ExtremeZone(
                id = "school_nyc_queens_ps123",
                type = ExtremeZoneType.SCHOOL,
                centerLat = 40.7128, centerLng = -73.9502,
                radiusMiles = 0.5,
                windows = schoolWindows,
                timeZoneId = "America/New_York",
            ),
            // 6. Industrial — Houston Ship Channel
            ExtremeZone(
                id = "industrial_houston_ship_channel",
                type = ExtremeZoneType.INDUSTRIAL,
                centerLat = 29.7604, centerLng = -95.0734,
                radiusMiles = 1.0,
                windows = industrialWindows,
                timeZoneId = "America/Chicago",
            ),
            // 7. Rail Crossing — Chicago Union Station
            ExtremeZone(
                id = "rail_chicago_union",
                type = ExtremeZoneType.RAIL_CROSSING,
                centerLat = 41.8786, centerLng = -87.6400,
                radiusMiles = 0.1,
                windows = listOf(
                    DayWindow(weekdays, LocalTime(6, 30), LocalTime(9, 30)),
                    DayWindow(weekdays, LocalTime(15, 30), LocalTime(18, 30)),
                ),
                timeZoneId = "America/Chicago",
            ),
            // 8. Historical Corridor — French Quarter, New Orleans
            ExtremeZone(
                id = "corridor_new_orleans_french_quarter",
                type = ExtremeZoneType.HISTORICAL_CORRIDOR,
                centerLat = 29.9584, centerLng = -90.0644,
                radiusMiles = 0.4,
                windows = listOf(
                    DayWindow(allWeek, LocalTime(10, 0), LocalTime(23, 59)),
                ),
                timeZoneId = "America/Chicago",
            ),
            // 9. School — Seattle Eastside
            ExtremeZone(
                id = "school_seattle_eastside_ms",
                type = ExtremeZoneType.SCHOOL,
                centerLat = 47.6062, centerLng = -122.3321,
                radiusMiles = 0.5,
                windows = schoolWindows,
                timeZoneId = "America/Los_Angeles",
            ),
            // 10. Industrial — Port of Long Beach
            ExtremeZone(
                id = "industrial_long_beach_port",
                type = ExtremeZoneType.INDUSTRIAL,
                centerLat = 33.7701, centerLng = -118.1937,
                radiusMiles = 1.0,
                windows = industrialWindows,
                timeZoneId = "America/Los_Angeles",
            ),
        )
    }

    companion object {
        private const val EARTH_RADIUS_MILES: Double = 3_958.8
    }
}

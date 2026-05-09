package app.haulio.shared.radar

import kotlin.math.abs

/**
 * Enforces the legal ban on radar/speed-detector features in jurisdictions
 * where such devices are prohibited by law.
 *
 * ### Banned jurisdictions (as of 2024)
 * - **Virginia** — Va. Code Ann. § 46.2-1079
 * - **Washington D.C.** — D.C. Code § 50-2302.07
 *
 * ### Implementation strategy
 * - DC is handled with a simple bounding-box (the city is compact and rectangular).
 * - Virginia uses a 12-vertex polygon with a point-in-polygon ray-casting test
 *   to exclude the many surrounding states that share the broad bounding box.
 *
 * This check is performed in **both** the KMM proximity engine (Layer A) and the
 * Android RadarViewModel (Layer B) for defense-in-depth.
 */
object LegalGeofence {

    // ── Virginia polygon (12-vertex simplified outline, WGS-84) ──────────────
    // Vertices trace the state border clockwise.  Sufficient for geofencing;
    // error margin ≈ 10–20 km near irregular borders.
    private val VA_POLYGON: List<Pair<Double, Double>> = listOf(
        Pair(39.466, -77.719), // NW corner near WV/MD tri-state
        Pair(39.222, -77.469), // NE along Potomac
        Pair(38.634, -77.039), // approaches DC area
        Pair(38.468, -76.921), // SE tip of Northern VA
        Pair(37.898, -76.300), // Eastern Shore approach
        Pair(37.296, -76.002), // Tidewater / Hampton Roads
        Pair(36.554, -75.868), // VA Beach / OBX boundary
        Pair(36.540, -80.040), // SW along NC border
        Pair(36.540, -83.680), // SW tip near TN/KY
        Pair(37.200, -83.420), // NW along KY border
        Pair(38.150, -82.600), // WV border mid-west
        Pair(39.466, -77.719), // close polygon
    )

    // ── DC bounding box ───────────────────────────────────────────────────────
    private const val DC_LAT_MIN = 38.791
    private const val DC_LAT_MAX = 38.995
    private const val DC_LNG_MIN = -77.120
    private const val DC_LNG_MAX = -76.909

    // ── Virginia coarse bounding box (fast pre-check) ─────────────────────────
    private const val VA_LAT_MIN = 36.54
    private const val VA_LAT_MAX = 39.47
    private const val VA_LNG_MIN = -83.68
    private const val VA_LNG_MAX = -75.24

    /**
     * Returns `true` when [lat]/[lng] falls inside a jurisdiction that bans
     * radar/speed-detector devices.  All radar features MUST be suppressed when
     * this function returns `true`.
     */
    fun isRadarBanned(lat: Double, lng: Double): Boolean =
        isInDC(lat, lng) || isInVirginia(lat, lng)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun isInDC(lat: Double, lng: Double): Boolean =
        lat in DC_LAT_MIN..DC_LAT_MAX && lng in DC_LNG_MIN..DC_LNG_MAX

    private fun isInVirginia(lat: Double, lng: Double): Boolean {
        // Fast coarse bbox rejection
        if (lat !in VA_LAT_MIN..VA_LAT_MAX || lng !in VA_LNG_MIN..VA_LNG_MAX) return false
        // Precise polygon test (ray casting)
        return pointInPolygon(lat, lng, VA_POLYGON)
    }

    /**
     * Ray-casting point-in-polygon test.
     * Counts how many times a horizontal ray from (lat, lng) crosses a polygon edge.
     * Odd = inside, Even = outside.
     */
    internal fun pointInPolygon(
        lat: Double,
        lng: Double,
        polygon: List<Pair<Double, Double>>,
    ): Boolean {
        var inside = false
        val n = polygon.size
        var j = n - 1
        for (i in 0 until n) {
            val (iLat, iLng) = polygon[i]
            val (jLat, jLng) = polygon[j]
            if ((iLng > lng) != (jLng > lng) &&
                lat < (jLat - iLat) * (lng - iLng) / (jLng - iLng) + iLat
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}

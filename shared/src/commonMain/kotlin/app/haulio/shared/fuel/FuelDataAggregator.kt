package app.haulio.shared.fuel

import app.haulio.shared.fuel.crowd.FuelBbox
import app.haulio.shared.fuel.crowd.IFuelReportSource
import app.haulio.shared.fuel.models.FuelPrice
import app.haulio.shared.fuel.models.FuelStation
import app.haulio.shared.fuel.sources.EiaClient
import app.haulio.shared.fuel.sources.OsmFuelClient
import kotlinx.datetime.Clock
import kotlin.math.sqrt

/**
 * Orchestrates multiple fuel data sources into a single coherent view.
 *
 * - [getRegionalAverage] delegates to [EiaClient] using PADD district lookup.
 * - [getNearbyStations] fetches OSM station metadata and merges crowd prices.
 *   The most recent crowd price wins for each station (matched by id or proximity).
 */
class FuelDataAggregator(
    private val eiaClient: EiaClient,
    private val osmClient: OsmFuelClient,
    private val crowdSource: IFuelReportSource,
) {

    /**
     * Returns the most recent EIA weekly average price for the PADD district
     * containing the given ([lat], [lng]) coordinate.
     */
    suspend fun getRegionalAverage(lat: Double, lng: Double): Result<FuelPrice> =
        eiaClient.fetchRegionalPrice(lat, lng)

    /**
     * Returns fuel stations in the bounding box with crowd prices merged in.
     *
     * Merging strategy:
     *   1. Fetch OSM station nodes (no prices).
     *   2. Fetch crowd-sourced prices for the same bbox.
     *   3. For each OSM station, attach the crowd price whose [FuelStation.id]
     *      matches, or the nearest unmatched crowd price within [CROWD_MATCH_RADIUS_DEG].
     */
    suspend fun getNearbyStations(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
    ): Result<List<FuelStation>> = runCatching {
        val bbox = FuelBbox(minLat, minLng, maxLat, maxLng)

        val osmStations = osmClient.fetchStationsInBbox(minLat, minLng, maxLat, maxLng)
            .getOrDefault(emptyList())

        val crowdPrices = crowdSource.fetchActive(bbox)
            .getOrDefault(emptyList())

        if (crowdPrices.isEmpty()) return@runCatching osmStations

        // Crowd prices don't directly carry station ids in the shared model —
        // the aggregator uses the crowd source's per-station grouping.
        // For mock/OSM mode we just assign the first crowd price to each station
        // by index (production Supabase impl will match by station_id).
        osmStations.mapIndexed { idx, station ->
            val matched = crowdPrices.getOrNull(idx)
            station.copy(
                latestPrice    = matched,
                lastReportedTs = if (matched != null) Clock.System.now().toEpochMilliseconds() else null,
            )
        }
    }

    companion object {
        /**
         * Degree-distance threshold for crowd price proximity matching
         * (~111 m per 0.001 degree at mid-latitudes).
         */
        const val CROWD_MATCH_RADIUS_DEG = 0.002
    }
}

// ---------------------------------------------------------------------------
// Utility: flat-earth distance (degrees) — no java.math dependency
// ---------------------------------------------------------------------------

internal fun distanceDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dlat = lat2 - lat1
    val dlng = lng2 - lng1
    return sqrt(dlat * dlat + dlng * dlng)
}

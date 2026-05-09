package app.haulio.shared.traffic.sources

import app.haulio.shared.traffic.models.TrafficEvent

/**
 * Axis-aligned bounding box for spatial queries.
 *
 * @property minLat Southern latitude boundary (decimal degrees).
 * @property minLng Western longitude boundary (decimal degrees).
 * @property maxLat Northern latitude boundary (decimal degrees).
 * @property maxLng Eastern longitude boundary (decimal degrees).
 */
data class BoundingBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)

/**
 * Interface for a crowd-sourced incident data provider.
 *
 * The production implementation streams live reports from Supabase Realtime.
 * A stub (or mock) can be injected for testing and CI.
 */
interface IIncidentSource {

    /**
     * Fetches all active incident reports within the given bounding box since [sinceTs].
     *
     * @param bbox Geographic bounding box restricting results.
     * @param sinceTs Epoch-millisecond lower bound; only incidents reported after this
     *                timestamp are returned.
     * @return [Result.success] with a (possibly empty) list of [TrafficEvent],
     *         or [Result.failure] on connectivity or parse errors.
     */
    suspend fun fetchActive(bbox: BoundingBox, sinceTs: Long): Result<List<TrafficEvent>>
}

/**
 * Stub crowd-report client that always returns an empty list.
 *
 * Replace with the Supabase Realtime implementation in the platform-specific module.
 */
class CrowdReportClient : IIncidentSource {

    override suspend fun fetchActive(bbox: BoundingBox, sinceTs: Long): Result<List<TrafficEvent>> =
        Result.success(emptyList())
}

package app.haulio.shared.fuel.models

/**
 * A fuel station with optional crowd-sourced price data.
 *
 * @property id              Stable identifier — OpenStreetMap node ID or Supabase UUID.
 * @property name            Human-readable station name, e.g. "Shell on Broadway".
 * @property brand           Brand name, e.g. "Shell", "Chevron", "Costco".
 * @property lat             WGS-84 latitude.
 * @property lng             WGS-84 longitude.
 * @property latestPrice     Most recent [FuelPrice] from crowd reports, or null if unknown.
 * @property lastReportedTs  Epoch-millisecond timestamp of the latest crowd price report, or null.
 */
data class FuelStation(
    val id: String,
    val name: String?,
    val brand: String?,
    val lat: Double,
    val lng: Double,
    val latestPrice: FuelPrice?,
    val lastReportedTs: Long?,
)

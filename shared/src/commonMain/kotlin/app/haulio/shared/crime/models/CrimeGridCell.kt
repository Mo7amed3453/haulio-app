package app.haulio.shared.crime.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A pre-computed 0.5 km grid cell from the Haulio backend crime heatmap.
 *
 * @param cellId        Unique cell identifier — format "{lat6}_{lng6}" rounded to 0.005 deg.
 * @param lat           Cell centre latitude.
 * @param lng           Cell centre longitude.
 * @param riskScore     Aggregate risk score in [0, 10]; higher = more dangerous.
 * @param incidentCount Number of crime incidents in this cell (last 90 days).
 * @param topCrimeType  Most frequently occurring crime type in this cell, or null if none.
 * @param computedAt    ISO 8601 timestamp when the cell was last aggregated by the backend.
 */
@Serializable
data class CrimeGridCell(
    @SerialName("cell_id")        val cellId: String,
    @SerialName("lat")            val lat: Double,
    @SerialName("lng")            val lng: Double,
    @SerialName("risk_score")     val riskScore: Double,
    @SerialName("incident_count") val incidentCount: Int,
    @SerialName("top_crime_type") val topCrimeType: String?,
    @SerialName("computed_at")    val computedAt: String,
)

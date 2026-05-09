package app.haulio.shared.crime.models

/**
 * Local stub for the KMM shared CrimeGridCell model.
 * Replace with actual KMM artifact when the shared module is wired.
 */
data class CrimeGridCell(
    val cellId: String,
    val lat: Double,
    val lng: Double,
    val riskScore: Double,
    val incidentCount: Int,
    val topCrimeType: String?,
    val computedAt: String,
)

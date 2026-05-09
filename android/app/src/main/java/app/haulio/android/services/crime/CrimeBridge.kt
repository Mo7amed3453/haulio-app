package app.haulio.android.services.crime

import app.haulio.shared.crime.HighRiskAlertEvent
import app.haulio.shared.crime.models.CrimeGridCell
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

// ---------------------------------------------------------------------------
// Repository interface
// ---------------------------------------------------------------------------

/**
 * Android contract for the crime grid data source.
 *
 * Swap [MockCrimeRepository] for a KMM bridge when the shared module is
 * wired to the live Haulio backend.
 */
interface ICrimeRepository {

    /**
     * Reactive grid of [CrimeGridCell] for the given bounding box.
     * Implementations should refresh data on collection and on demand.
     */
    fun gridForBbox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): Flow<List<CrimeGridCell>>

    /**
     * Flow of [HighRiskAlertEvent] triggered when the driver enters a
     * new high-risk cell. Null emissions indicate no current alert.
     *
     * @param gpsFlow Flow of (latitude, longitude) pairs from the GPS service.
     */
    fun alerts(gpsFlow: Flow<Pair<Double, Double>>): Flow<HighRiskAlertEvent?>
}

// ---------------------------------------------------------------------------
// KMM bridge placeholder
// ---------------------------------------------------------------------------

// class KmmCrimeRepositoryBridge(
//     private val crimeGridClient: CrimeGridClient,
//     private val alertEngine: CrimeAlertEngine,
// ) : ICrimeRepository { ... }

// ---------------------------------------------------------------------------
// Mock data — 10 San Francisco grid cells with varying risk scores 2–9
// ---------------------------------------------------------------------------

private val SF_CRIME_CELLS = listOf(
    CrimeGridCell("37775_-122420", 37.775, -122.420, riskScore = 8.5,  incidentCount = 42, topCrimeType = "robbery",          computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37780_-122415", 37.780, -122.415, riskScore = 7.2,  incidentCount = 35, topCrimeType = "assault",          computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37770_-122430", 37.770, -122.430, riskScore = 6.0,  incidentCount = 28, topCrimeType = "burglary",         computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37760_-122408", 37.760, -122.408, riskScore = 5.3,  incidentCount = 22, topCrimeType = "theft",            computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37785_-122440", 37.785, -122.440, riskScore = 4.1,  incidentCount = 18, topCrimeType = "motor vehicle theft", computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37755_-122395", 37.755, -122.395, riskScore = 9.0,  incidentCount = 51, topCrimeType = "robbery",          computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37790_-122450", 37.790, -122.450, riskScore = 3.8,  incidentCount = 15, topCrimeType = "theft",            computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37765_-122425", 37.765, -122.425, riskScore = 2.5,  incidentCount =  9, topCrimeType = "burglary",         computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37750_-122435", 37.750, -122.435, riskScore = 7.8,  incidentCount = 40, topCrimeType = "assault",          computedAt = "2024-01-15T03:00:00Z"),
    CrimeGridCell("37745_-122410", 37.745, -122.410, riskScore = 6.5,  incidentCount = 30, topCrimeType = "larceny",          computedAt = "2024-01-15T03:00:00Z"),
)

// ---------------------------------------------------------------------------
// Mock implementation
// ---------------------------------------------------------------------------

class MockCrimeRepository : ICrimeRepository {

    private val _grid = MutableStateFlow<List<CrimeGridCell>>(SF_CRIME_CELLS)

    override fun gridForBbox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): Flow<List<CrimeGridCell>> = _grid.map { cells ->
        cells.filter { cell ->
            cell.lat in south..north && cell.lng in west..east
        }
    }

    override fun alerts(gpsFlow: Flow<Pair<Double, Double>>): Flow<HighRiskAlertEvent?> {
        // In the mock, simulate an alert after the driver moves near a high-risk cell.
        // Real implementation wires GPS through CrimeAlertEngine.
        return MutableStateFlow<HighRiskAlertEvent?>(null).asStateFlow()
    }
}

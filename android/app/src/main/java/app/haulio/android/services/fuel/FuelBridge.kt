package app.haulio.android.services.fuel

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------------------------------------------------------------------------
// Domain models (Android-side, mirrors KMM shared models)
// ---------------------------------------------------------------------------

data class FuelPrice(
    val regularUsd: Double,
    val midGradeUsd: Double?,
    val premiumUsd: Double?,
    val dieselUsd: Double?,
    val weekOf: String,       // ISO date string "YYYY-MM-DD"
    val source: String,
)

data class FuelStation(
    val id: String,
    val name: String?,
    val brand: String?,
    val lat: Double,
    val lng: Double,
    val latestPrice: FuelPrice?,
    val lastReportedTs: Long?,
    val distanceMiles: Double = 0.0,  // set by UI layer based on driver location
)

enum class FuelGrade { REGULAR, MID_GRADE, PREMIUM, DIESEL }

// ---------------------------------------------------------------------------
// Bridge interfaces
// ---------------------------------------------------------------------------

/**
 * Android contract for the shared KMM FuelDataAggregator.
 * Swap [MockFuelDataAggregator] for a KMM bridge when the shared module is wired.
 */
interface IFuelDataAggregator {
    val stations: Flow<List<FuelStation>>
    val regionalAverage: Flow<FuelPrice?>
    suspend fun refresh(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double)
    suspend fun submit(stationId: String, price: Double, grade: FuelGrade): Result<Unit>
}

// ---------------------------------------------------------------------------
// KMM real bridge (placeholder)
// ---------------------------------------------------------------------------

// class KmmFuelDataAggregatorBridge(private val delegate: ...) : IFuelDataAggregator { ... }

// ---------------------------------------------------------------------------
// Mock data — 5 Bay Area fuel stations
// ---------------------------------------------------------------------------

private val TODAY = "2024-01-15"

private val MOCK_STATIONS = listOf(
    FuelStation(
        id              = "osm_111001",
        name            = "Shell Mission St",
        brand           = "Shell",
        lat             = 37.7749,
        lng             = -122.4194,
        latestPrice     = FuelPrice(4.49, 4.69, 4.89, 4.99, TODAY, "crowd"),
        lastReportedTs  = System.currentTimeMillis() - 2 * 60 * 60 * 1000L,
        distanceMiles   = 0.4,
    ),
    FuelStation(
        id              = "osm_111002",
        name            = "Chevron Van Ness",
        brand           = "Chevron",
        lat             = 37.7820,
        lng             = -122.4090,
        latestPrice     = FuelPrice(4.55, 4.75, 4.95, 5.05, TODAY, "crowd"),
        lastReportedTs  = System.currentTimeMillis() - 45 * 60 * 1000L,
        distanceMiles   = 0.8,
    ),
    FuelStation(
        id              = "osm_111003",
        name            = "Costco Gas",
        brand           = "Costco",
        lat             = 37.7590,
        lng             = -122.4040,
        latestPrice     = FuelPrice(4.18, null, null, 4.59, TODAY, "crowd"),
        lastReportedTs  = System.currentTimeMillis() - 5 * 60 * 60 * 1000L,
        distanceMiles   = 1.2,
    ),
    FuelStation(
        id              = "osm_111004",
        name            = "76 Potrero Ave",
        brand           = "76",
        lat             = 37.7680,
        lng             = -122.4063,
        latestPrice     = FuelPrice(4.39, 4.59, 4.79, null, TODAY, "crowd"),
        lastReportedTs  = System.currentTimeMillis() - 30 * 60 * 1000L,
        distanceMiles   = 1.5,
    ),
    FuelStation(
        id              = "osm_111005",
        name            = "Arco Cesar Chavez",
        brand           = "Arco",
        lat             = 37.7497,
        lng             = -122.4185,
        latestPrice     = FuelPrice(4.35, null, null, null, TODAY, "crowd"),
        lastReportedTs  = System.currentTimeMillis() - 10 * 60 * 60 * 1000L,
        distanceMiles   = 1.9,
    ),
)

private val REGIONAL_AVERAGE = FuelPrice(
    regularUsd  = 4.32,
    midGradeUsd = 4.52,
    premiumUsd  = 4.72,
    dieselUsd   = 4.85,
    weekOf      = TODAY,
    source      = "EIA",
)

// ---------------------------------------------------------------------------
// Mock implementation
// ---------------------------------------------------------------------------

class MockFuelDataAggregator : IFuelDataAggregator {

    private val _stations = MutableStateFlow<List<FuelStation>>(MOCK_STATIONS)
    override val stations: Flow<List<FuelStation>> = _stations.asStateFlow()

    private val _regionalAverage = MutableStateFlow<FuelPrice?>(REGIONAL_AVERAGE)
    override val regionalAverage: Flow<FuelPrice?> = _regionalAverage.asStateFlow()

    override suspend fun refresh(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double) {
        delay(600) // simulate network
        _stations.value = MOCK_STATIONS
        _regionalAverage.value = REGIONAL_AVERAGE
    }

    override suspend fun submit(stationId: String, price: Double, grade: FuelGrade): Result<Unit> {
        delay(400) // simulate POST
        val updated = _stations.value.map { station ->
            if (station.id == stationId) {
                val existing = station.latestPrice ?: FuelPrice(0.0, null, null, null, TODAY, "crowd")
                val updated = when (grade) {
                    FuelGrade.REGULAR   -> existing.copy(regularUsd = price, source = "crowd")
                    FuelGrade.MID_GRADE -> existing.copy(midGradeUsd = price, source = "crowd")
                    FuelGrade.PREMIUM   -> existing.copy(premiumUsd = price, source = "crowd")
                    FuelGrade.DIESEL    -> existing.copy(dieselUsd = price, source = "crowd")
                }
                station.copy(latestPrice = updated, lastReportedTs = System.currentTimeMillis())
            } else {
                station
            }
        }
        _stations.value = updated
        return Result.success(Unit)
    }
}

package app.haulio.shared.fuel.crowd

import app.haulio.shared.fuel.models.FuelPrice
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Bounding box for spatial crowd-report queries.
 */
data class FuelBbox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)

/**
 * Fuel grade for crowd-reported prices.
 */
enum class FuelGrade { REGULAR, MID_GRADE, PREMIUM, DIESEL }

/**
 * Interface for a crowd-sourced fuel price data source.
 *
 * The production implementation reads from Supabase; [MockFuelReportClient] is used
 * for standalone development and testing.
 */
interface IFuelReportSource {

    /**
     * Fetches active (recent) crowd-sourced fuel prices within [bbox].
     *
     * @return [Result.success] with a (possibly empty) list of [FuelPrice],
     *         or [Result.failure] on connectivity or parse errors.
     */
    suspend fun fetchActive(bbox: FuelBbox): Result<List<FuelPrice>>

    /**
     * Submits a driver-reported price for a station.
     *
     * @param stationId  Stable station identifier (OSM node ID or Supabase UUID).
     * @param price      Price per gallon in USD.
     * @param gradeType  [FuelGrade] being reported.
     * @return [Result.success] on successful submission, or [Result.failure] on error.
     */
    suspend fun submit(stationId: String, price: Double, gradeType: FuelGrade): Result<Unit>
}

/**
 * Abstract base for Supabase wiring.
 * Override [fetchActive] and [submit] to delegate to the Supabase client.
 */
abstract class SupabaseFuelReportSource : IFuelReportSource

/**
 * Mock implementation of [IFuelReportSource] with Bay Area fake data.
 */
class MockFuelReportClient : IFuelReportSource {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val mockPrices = mutableListOf(
        FuelPrice(
            regularUsd  = 4.49,
            midGradeUsd = 4.69,
            premiumUsd  = 4.89,
            dieselUsd   = 4.99,
            weekOf      = today,
            source      = "crowd",
        ),
        FuelPrice(
            regularUsd  = 4.55,
            midGradeUsd = 4.75,
            premiumUsd  = 4.95,
            dieselUsd   = 5.05,
            weekOf      = today,
            source      = "crowd",
        ),
        FuelPrice(
            regularUsd  = 4.18,
            midGradeUsd = null,
            premiumUsd  = null,
            dieselUsd   = null,
            weekOf      = today,
            source      = "crowd",
        ),
    )

    override suspend fun fetchActive(bbox: FuelBbox): Result<List<FuelPrice>> =
        Result.success(mockPrices.toList())

    override suspend fun submit(stationId: String, price: Double, gradeType: FuelGrade): Result<Unit> {
        // Simulate submission by noting it; real impl would POST to Supabase
        val newEntry = when (gradeType) {
            FuelGrade.REGULAR   -> FuelPrice(price, null, null, null, today, "crowd")
            FuelGrade.MID_GRADE -> FuelPrice(0.0, price, null, null, today, "crowd")
            FuelGrade.PREMIUM   -> FuelPrice(0.0, null, price, null, today, "crowd")
            FuelGrade.DIESEL    -> FuelPrice(0.0, null, null, price, today, "crowd")
        }
        mockPrices.add(newEntry)
        return Result.success(Unit)
    }
}

package app.haulio.android.services.address

import app.haulio.shared.navigation.models.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Supabase table DTOs
// ---------------------------------------------------------------------------

/**
 * Row shape for the `deliveries` Supabase table.
 */
data class DeliveryRow(
    val deliveryId: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val unitNumber: String?,
    val photoUrl: String?,
    val driverId: String,
    val timestampMs: Long,
)

/**
 * Row shape for the `address_corrections` Supabase table.
 */
data class CorrectionRow(
    val originalInput: String,
    val correctedAddress: String,
    val lat: Double,
    val lon: Double,
    val driverId: String,
    val timestampMs: Long,
)

// ---------------------------------------------------------------------------
// DeliveryLogger interface implementation
// ---------------------------------------------------------------------------

/**
 * Logs deliveries and address corrections to Supabase.
 *
 * Wire [supabaseClient] from the real `io.github.jan-tennert.supabase:postgrest-kt` client
 * at DI time. Pass [MockDeliveryLogger] during standalone development.
 *
 * @param driverId  Authenticated driver identifier from Supabase Auth.
 */
class DeliveryLogger(
    private val driverId: String = "driver-unknown",
) : IDeliveryLogger {

    // In production replace these stubs with real Supabase postgrest calls:
    //   supabase.from("deliveries").insert(row)

    override suspend fun logDelivery(
        address: String,
        coordinates: GeoPoint,
        unit: String?,
        photoUri: String?,
    ) {
        withContext(Dispatchers.IO) {
            val row = DeliveryRow(
                deliveryId  = java.util.UUID.randomUUID().toString(),
                address     = address,
                lat         = coordinates.lat,
                lon         = coordinates.lon,
                unitNumber  = unit,
                photoUrl    = photoUri,
                driverId    = driverId,
                timestampMs = System.currentTimeMillis(),
            )
            // TODO: supabase.from("deliveries").insert(row)
            android.util.Log.d("DeliveryLogger", "logDelivery: $row")
        }
    }

    override suspend fun logCorrection(
        original: String,
        corrected: String,
        coordinates: GeoPoint,
    ) {
        withContext(Dispatchers.IO) {
            val row = CorrectionRow(
                originalInput    = original,
                correctedAddress = corrected,
                lat              = coordinates.lat,
                lon              = coordinates.lon,
                driverId         = driverId,
                timestampMs      = System.currentTimeMillis(),
            )
            // TODO: supabase.from("address_corrections").insert(row)
            android.util.Log.d("DeliveryLogger", "logCorrection: $row")
        }
    }

    override suspend fun getCorrectionCount(coordinates: GeoPoint, radiusMeters: Double): Int =
        withContext(Dispatchers.IO) {
            // TODO: supabase RPC call — select count(*) from address_corrections
            //       where ST_DWithin(point, coordinates, radiusMeters)
            //       and driver_id != driverId
            0
        }
}

// ---------------------------------------------------------------------------
// Mock implementation — used for standalone development / Compose Previews
// ---------------------------------------------------------------------------

/**
 * In-memory [IDeliveryLogger] that accumulates logs without a Supabase dependency.
 */
class MockDeliveryLogger : IDeliveryLogger {

    private val deliveries  = mutableListOf<DeliveryRow>()
    private val corrections = mutableListOf<CorrectionRow>()

    override suspend fun logDelivery(
        address: String,
        coordinates: GeoPoint,
        unit: String?,
        photoUri: String?,
    ) {
        deliveries += DeliveryRow(
            deliveryId  = java.util.UUID.randomUUID().toString(),
            address     = address,
            lat         = coordinates.lat,
            lon         = coordinates.lon,
            unitNumber  = unit,
            photoUrl    = photoUri,
            driverId    = "mock-driver",
            timestampMs = System.currentTimeMillis(),
        )
    }

    override suspend fun logCorrection(
        original: String,
        corrected: String,
        coordinates: GeoPoint,
    ) {
        corrections += CorrectionRow(
            originalInput    = original,
            correctedAddress = corrected,
            lat              = coordinates.lat,
            lon              = coordinates.lon,
            driverId         = "mock-driver",
            timestampMs      = System.currentTimeMillis(),
        )
    }

    override suspend fun getCorrectionCount(coordinates: GeoPoint, radiusMeters: Double): Int {
        // Return a mock count based on how many corrections are near the coordinates
        return corrections.count { row ->
            val dLat = row.lat - coordinates.lat
            val dLon = row.lon - coordinates.lon
            val distMeters = kotlin.math.sqrt(
                (dLat * 111_000).let { it * it } + (dLon * 85_000).let { it * it }
            )
            distMeters <= radiusMeters
        }.coerceAtMost(7) // cap at 7 for realistic mock data
    }
}

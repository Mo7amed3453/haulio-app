package app.haulio.shared.fuel.sources

import app.haulio.shared.fuel.models.FuelPrice
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * HTTP client for the U.S. Energy Information Administration (EIA) Weekly Retail Fuel Price API.
 *
 * Endpoint (v2):
 *   GET https://api.eia.gov/v2/petroleum/pri/gnd/data/
 *     ?frequency=weekly
 *     &data[0]=value
 *     &facets[duoarea][]={districtCode}
 *     &facets[product][]={productCode}
 *     &sort[0][column]=period
 *     &sort[0][direction]=desc
 *     &offset=0
 *     &length=1
 *
 * No API key is required for the public free tier; pass a non-blank [apiKey] to
 * increase rate limits.
 *
 * @param httpClient Ktor [HttpClient] configured with JSON content negotiation.
 * @param apiKey     Optional EIA API key for higher rate limits.
 */
class EiaClient(
    private val httpClient: HttpClient,
    private val apiKey: String = "",
) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches the most recent weekly regional gasoline price for the PADD district
     * that contains ([lat], [lng]).
     *
     * @return [Result.success] with a [FuelPrice], or [Result.failure] on error.
     */
    suspend fun fetchRegionalPrice(lat: Double, lng: Double): Result<FuelPrice> = runCatching {
        val districtCode = petroleumDistrictForLocation(lat, lng)
        fetchPriceForDistrict(districtCode)
    }

    /**
     * Fetches the most recent weekly price for a specific PADD district code
     * (e.g. "R1X" for PADD 1A / New England).
     */
    suspend fun fetchPriceForDistrict(districtCode: String): FuelPrice {
        val regular  = fetchSingleValue(districtCode, PRODUCT_REGULAR)
        val midGrade = runCatching { fetchSingleValue(districtCode, PRODUCT_MIDGRADE) }.getOrNull()
        val premium  = runCatching { fetchSingleValue(districtCode, PRODUCT_PREMIUM) }.getOrNull()
        val diesel   = runCatching { fetchSingleValue(districtCode, PRODUCT_DIESEL) }.getOrNull()

        return FuelPrice(
            regularUsd  = regular.value,
            midGradeUsd = midGrade?.value,
            premiumUsd  = premium?.value,
            dieselUsd   = diesel?.value,
            weekOf      = parsePeriodDate(regular.period),
            source      = "EIA",
        )
    }

    /**
     * Returns the EIA PADD district code for a given lat/lng by looking up the US state.
     *
     * District codes:
     *   R1X = PADD 1A (New England)
     *   R1Y = PADD 1B (Central Atlantic)
     *   R1Z = PADD 1C (Lower Atlantic)
     *   R20 = PADD 2  (Midwest)
     *   R30 = PADD 3  (Gulf Coast)
     *   R40 = PADD 4  (Rocky Mountain)
     *   R50 = PADD 5  (West Coast incl. AK, HI)
     *
     * Falls back to "R10" (all East Coast / national) when the state cannot be determined.
     */
    fun petroleumDistrictForLocation(lat: Double, lng: Double): String {
        val state = approximateStateFromLatLng(lat, lng) ?: return "R10"
        return STATE_TO_DISTRICT[state] ?: "R10"
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun fetchSingleValue(districtCode: String, productCode: String): EiaDataPoint {
        val response: EiaResponse = httpClient.get(BASE_URL) {
            parameter("frequency", "weekly")
            parameter("data[0]", "value")
            parameter("facets[duoarea][]", districtCode)
            parameter("facets[product][]", productCode)
            parameter("sort[0][column]", "period")
            parameter("sort[0][direction]", "desc")
            parameter("offset", 0)
            parameter("length", 1)
            if (apiKey.isNotBlank()) parameter("api_key", apiKey)
        }.body()

        return response.response.data.firstOrNull()
            ?: throw IllegalStateException("No EIA data for district=$districtCode product=$productCode")
    }

    /**
     * Parses an EIA period string like "2024-01-15" into a [LocalDate].
     */
    private fun parsePeriodDate(period: String): LocalDate = try {
        LocalDate.parse(period)
    } catch (_: Exception) {
        LocalDate(2000, 1, 1)
    }

    // -------------------------------------------------------------------------
    // US state → PADD district lookup
    // -------------------------------------------------------------------------

    companion object {
        private const val BASE_URL = "https://api.eia.gov/v2/petroleum/pri/gnd/data/"

        // Product codes used by EIA petroleum/pri/gnd dataset
        const val PRODUCT_REGULAR  = "EPM0"
        const val PRODUCT_MIDGRADE = "EPMP"
        const val PRODUCT_PREMIUM  = "EPPR"
        const val PRODUCT_DIESEL   = "EPD2D"

        /**
         * Maps two-letter US state codes to EIA PADD district codes.
         * Covers all 50 states + DC.
         */
        val STATE_TO_DISTRICT: Map<String, String> = mapOf(
            // PADD 1A — New England
            "CT" to "R1X", "ME" to "R1X", "MA" to "R1X",
            "NH" to "R1X", "RI" to "R1X", "VT" to "R1X",
            // PADD 1B — Central Atlantic
            "DE" to "R1Y", "DC" to "R1Y", "MD" to "R1Y",
            "NJ" to "R1Y", "NY" to "R1Y", "PA" to "R1Y",
            // PADD 1C — Lower Atlantic
            "FL" to "R1Z", "GA" to "R1Z", "NC" to "R1Z",
            "SC" to "R1Z", "VA" to "R1Z", "WV" to "R1Z",
            // PADD 2 — Midwest
            "IL" to "R20", "IN" to "R20", "IA" to "R20",
            "KS" to "R20", "KY" to "R20", "MI" to "R20",
            "MN" to "R20", "MO" to "R20", "NE" to "R20",
            "ND" to "R20", "OH" to "R20", "OK" to "R20",
            "SD" to "R20", "TN" to "R20", "WI" to "R20",
            // PADD 3 — Gulf Coast
            "AL" to "R30", "AR" to "R30", "LA" to "R30",
            "MS" to "R30", "NM" to "R30", "TX" to "R30",
            // PADD 4 — Rocky Mountain
            "CO" to "R40", "ID" to "R40", "MT" to "R40",
            "UT" to "R40", "WY" to "R40",
            // PADD 5 — West Coast (incl. Alaska and Hawaii)
            "AK" to "R50", "AZ" to "R50", "CA" to "R50",
            "HI" to "R50", "NV" to "R50", "OR" to "R50",
            "WA" to "R50",
        )

        /**
         * Very coarse bounding-box lookup: returns a two-letter state code for a
         * lat/lng within the continental US, Alaska, or Hawaii.
         * Accuracy is sufficient for PADD district assignment (districts are large regions).
         */
        fun approximateStateFromLatLng(lat: Double, lng: Double): String? {
            // Alaska
            if (lat >= 54.0 && lng <= -130.0) return "AK"
            // Hawaii
            if (lat in 18.0..23.0 && lng in -162.0..-154.0) return "HI"
            // Coarse CONUS bounding boxes (ordered roughly west-to-east / north-to-south)
            return when {
                lat >= 47.5 && lng <= -116.0                         -> "WA"
                lat >= 44.0 && lng in -124.5..-116.0                 -> "OR"
                lat in 32.5..42.0 && lng in -124.5..-114.0           -> "CA"
                lat in 31.3..37.0 && lng in -114.8..-109.0           -> "AZ"
                lat in 31.3..37.0 && lng in -109.0..-103.0           -> "NM"
                lat in 36.9..41.0 && lng in -109.0..-102.0           -> "UT"
                lat in 41.0..49.0 && lng in -117.0..-104.0           -> "MT"
                lat in 41.0..45.0 && lng in -111.0..-104.0           -> "ID"
                lat in 37.0..41.0 && lng in -109.0..-102.0           -> "CO"
                lat in 41.0..45.0 && lng in -104.0..-96.0            -> "WY"
                lat in 43.0..49.0 && lng in -104.0..-96.5            -> "ND"
                lat in 43.0..49.0 && lng in -104.0..-96.5            -> "MT" // overlap — MT handled above
                lat in 43.5..46.5 && lng in -97.0..-96.5             -> "SD"
                lat in 40.0..43.5 && lng in -104.0..-95.3            -> "NE"
                lat in 37.0..40.0 && lng in -102.0..-94.6            -> "KS"
                lat in 33.6..37.0 && lng in -103.0..-94.4            -> "OK"
                lat in 25.8..36.5 && lng in -106.6..-93.5            -> "TX"
                lat in 44.0..49.4 && lng in -97.2..-89.5             -> "MN"
                lat in 42.5..46.7 && lng in -92.9..-86.8             -> "WI"
                lat in 36.0..42.5 && lng in -91.5..-87.0             -> "IL"
                lat in 37.8..42.0 && lng in -85.2..-84.8             -> "IN"
                lat in 36.5..42.0 && lng in -84.8..-80.5             -> "OH"
                lat in 36.5..39.1 && lng in -89.6..-81.9             -> "KY"
                lat in 34.9..36.7 && lng in -90.3..-81.6             -> "TN"
                lat in 29.0..35.0 && lng in -91.7..-88.0             -> "MS"
                lat in 29.0..35.0 && lng in -94.1..-88.4             -> "LA"
                lat in 29.0..35.2 && lng in -94.1..-88.0             -> "AL"  // overlaps LA/MS
                lat in 29.5..35.8 && lng in -94.5..-90.0             -> "AR"
                lat in 44.0..49.0 && lng in -89.5..-82.0             -> "MI"
                lat in 42.5..45.0 && lng in -87.0..-82.0             -> "MI"  // lower peninsula
                lat in 38.0..43.0 && lng in -82.0..-74.7             -> "PA"
                lat in 38.9..42.0 && lng in -79.8..-74.0             -> "NY"
                lat in 38.9..41.4 && lng in -74.2..-71.8             -> "NJ"
                lat in 38.4..39.8 && lng in -75.8..-75.0             -> "DE"
                lat in 37.9..39.7 && lng in -79.5..-75.0             -> "MD"
                lat in 36.5..38.6 && lng in -77.5..-75.2             -> "VA"
                lat in 37.2..39.7 && lng in -82.6..-77.7             -> "WV"
                lat in 33.8..36.6 && lng in -84.3..-75.5             -> "NC"
                lat in 32.0..35.2 && lng in -83.4..-78.5             -> "SC"
                lat in 30.3..35.0 && lng in -85.6..-80.9             -> "GA"
                lat in 25.0..31.0 && lng in -87.6..-80.0             -> "FL"
                lat in 42.7..45.0 && lng in -73.5..-71.5             -> "VT"
                lat in 42.7..45.2 && lng in -71.5..-70.6             -> "NH"
                lat in 42.7..47.5 && lng in -70.6..-67.0             -> "ME"
                lat in 41.5..42.9 && lng in -73.5..-71.4             -> "CT" // small
                lat in 41.1..42.0 && lng in -71.9..-71.1             -> "RI"
                lat in 41.5..42.9 && lng in -73.5..-70.0             -> "MA"
                lat >= 38.7 && lat <= 39.0 && lng in -77.2..-76.9   -> "DC"
                lat in 36.5..39.7 && lng in -80.9..-75.2             -> "VA" // catch-all VA
                else -> null
            }
        }
    }
}

// ---------------------------------------------------------------------------
// EIA API v2 response models
// ---------------------------------------------------------------------------

@Serializable
internal data class EiaResponse(
    val response: EiaResponseBody = EiaResponseBody(),
)

@Serializable
internal data class EiaResponseBody(
    val data: List<EiaDataPoint> = emptyList(),
)

@Serializable
internal data class EiaDataPoint(
    val period: String = "",
    val duoarea: String = "",
    @SerialName("area-name") val areaName: String = "",
    val product: String = "",
    @SerialName("product-name") val productName: String = "",
    val value: Double = 0.0,
    val units: String = "",
)

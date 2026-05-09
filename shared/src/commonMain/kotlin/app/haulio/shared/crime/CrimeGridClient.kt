package app.haulio.shared.crime

import app.haulio.shared.crime.models.CrimeGridCell
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/**
 * Bounding box passed to [CrimeGridClient.fetchGrid].
 */
data class CrimeBbox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    /** Formats as "south,west,north,east" for the bbox query param. */
    fun toQueryParam(): String = "$south,$west,$north,$east"
}

// ---------------------------------------------------------------------------
// Response wrapper
// ---------------------------------------------------------------------------

@Serializable
internal data class CrimeGridResponse(
    val cells: List<CrimeGridCell> = emptyList(),
)

// ---------------------------------------------------------------------------
// Client
// ---------------------------------------------------------------------------

/**
 * Ktor-based HTTP client for the Haulio backend crime grid endpoint.
 *
 * GET {baseUrl}/v1/crime/grid?bbox=south,west,north,east
 *
 * @param httpClient Ktor [HttpClient] configured with JSON content negotiation.
 * @param baseUrl    Base URL of the Haulio backend (e.g. "https://api.haulio.app").
 */
class CrimeGridClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    /**
     * Fetches pre-computed crime grid cells within [bbox].
     *
     * @return [Result.success] with a list of [CrimeGridCell], or [Result.failure] on error.
     */
    suspend fun fetchGrid(bbox: CrimeBbox): Result<List<CrimeGridCell>> = runCatching {
        val response: CrimeGridResponse = httpClient.get("$baseUrl/v1/crime/grid") {
            parameter("bbox", bbox.toQueryParam())
        }.body()
        response.cells
    }
}

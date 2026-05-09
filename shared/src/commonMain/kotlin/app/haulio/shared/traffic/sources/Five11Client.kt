package app.haulio.shared.traffic.sources

import app.haulio.shared.traffic.models.TrafficEvent
import app.haulio.shared.traffic.models.TrafficEventType
import app.haulio.shared.traffic.models.TrafficSeverity
import app.haulio.shared.traffic.models.TrafficSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * US 511 Traffic Events API client.
 *
 * Fetches real-time traffic incidents from the USDOT 511 GeoJSON feed.
 * Endpoint: GET {baseUrl}/traffic/events?api_key={key}&format=json&jurisdiction={stateCode}
 *
 * @param httpClient Ktor HttpClient configured with JSON content negotiation.
 * @param apiKey 511 API key (issued at https://511.org/developers/list/tokens/create).
 */
class Five11Client(
    private val httpClient: HttpClient,
    private val apiKey: String,
) {

    /**
     * Supported US states with their 511 feed base URLs.
     *
     * Most states share the national API gateway at api.511.org;
     * a few operate independent endpoints.
     */
    enum class State(val code: String, val baseUrl: String) {
        CA("CA", "https://api.511.org"),
        NY("NY", "https://api.511.org"),
        TX("TX", "https://api.511.org"),
        WA("WA", "https://api.511.org"),
        OR("OR", "https://api.511.org"),
        CO("CO", "https://api.511.org"),
        MN("MN", "https://api.511.org"),
        FL("FL", "https://api.511.org"),
        GA("GA", "https://api.511.org"),
        AZ("AZ", "https://api.511.org"),
    }

    /**
     * Fetches and parses traffic events for [state] into [TrafficEvent] domain objects.
     *
     * @param state The [State] whose feed to query.
     * @return [Result.success] with a (possibly empty) list of [TrafficEvent],
     *         or [Result.failure] wrapping the underlying exception.
     */
    suspend fun fetchEvents(state: State): Result<List<TrafficEvent>> = runCatching {
        require(apiKey.isNotBlank()) { "511 API key must not be blank" }

        val response: Five11FeatureCollection = httpClient.get("${state.baseUrl}/traffic/events") {
            parameter("api_key", apiKey)
            parameter("format", "json")
            parameter("jurisdiction", state.code)
        }.body()

        response.features.mapNotNull { it.toTrafficEventOrNull() }
    }

    private fun Five11Feature.toTrafficEventOrNull(): TrafficEvent? {
        val coords = geometry?.coordinates ?: return null
        val lng = coords.getOrNull(0) ?: return null
        val lat = coords.getOrNull(1) ?: return null
        val props = properties ?: return null

        return TrafficEvent(
            sourceId = id,
            type = mapEventType(props.eventType),
            severity = mapSeverity(props.severity),
            lat = lat,
            lng = lng,
            headingDeg = null,
            startTs = parseIso8601ToEpochMs(props.created),
            expiresTs = parseIso8601ToEpochMs(props.updated) + DEFAULT_EXPIRY_MS,
            source = TrafficSource.USDOT_511,
        )
    }

    private fun mapEventType(raw: String?): TrafficEventType = when {
        raw == null -> TrafficEventType.CONGESTION
        raw.contains("accident", ignoreCase = true) ||
            raw.contains("crash", ignoreCase = true) ||
            raw.contains("collision", ignoreCase = true) -> TrafficEventType.ACCIDENT
        raw.contains("construction", ignoreCase = true) ||
            raw.contains("roadwork", ignoreCase = true) -> TrafficEventType.CONSTRUCTION
        raw.contains("closure", ignoreCase = true) ||
            raw.contains("closed", ignoreCase = true) -> TrafficEventType.CLOSURE
        raw.contains("police", ignoreCase = true) ||
            raw.contains("law enforcement", ignoreCase = true) -> TrafficEventType.POLICE
        raw.contains("pothole", ignoreCase = true) ||
            raw.contains("hazard", ignoreCase = true) -> TrafficEventType.POTHOLE
        else -> TrafficEventType.CONGESTION
    }

    private fun mapSeverity(raw: String?): TrafficSeverity = when {
        raw == null -> TrafficSeverity.LOW
        raw.equals("Major", ignoreCase = true) ||
            raw.equals("High", ignoreCase = true) ||
            raw.equals("Critical", ignoreCase = true) -> TrafficSeverity.HIGH
        raw.equals("Moderate", ignoreCase = true) ||
            raw.equals("Medium", ignoreCase = true) -> TrafficSeverity.MED
        else -> TrafficSeverity.LOW
    }

    companion object {
        /** Default expiry added to the updated timestamp when no explicit end time is provided. */
        private const val DEFAULT_EXPIRY_MS: Long = 2L * 60L * 60L * 1000L // 2 hours
    }
}

// ---------------------------------------------------------------------------
// GeoJSON response models
// ---------------------------------------------------------------------------

@Serializable
internal data class Five11FeatureCollection(
    val type: String = "",
    val features: List<Five11Feature> = emptyList(),
)

@Serializable
internal data class Five11Feature(
    val id: String = "",
    val type: String = "",
    val geometry: Five11Geometry? = null,
    val properties: Five11Properties? = null,
)

@Serializable
internal data class Five11Geometry(
    val type: String = "",
    val coordinates: List<Double> = emptyList(),
)

@Serializable
internal data class Five11Properties(
    @SerialName("event_type") val eventType: String? = null,
    val severity: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val created: String? = null,
    val updated: String? = null,
)

// ---------------------------------------------------------------------------
// Shared ISO-8601 parser (no java.time; pure commonMain)
// ---------------------------------------------------------------------------

/**
 * Parses a subset of ISO-8601 date-time strings ("2024-01-15T14:30:00Z") to epoch milliseconds.
 * Returns 0 when the string is null or cannot be parsed.
 */
internal fun parseIso8601ToEpochMs(dateStr: String?): Long {
    if (dateStr == null) return 0L
    return try {
        val s = dateStr.trimEnd('Z')
        val tIdx = s.indexOf('T')
        if (tIdx < 0) return 0L
        val datePart = s.substring(0, tIdx)
        val timePart = s.substring(tIdx + 1)
        val dateParts = datePart.split("-")
        val timeParts = timePart.split(":")
        if (dateParts.size < 3 || timeParts.size < 3) return 0L
        val year = dateParts[0].toIntOrNull() ?: return 0L
        val month = dateParts[1].toIntOrNull() ?: return 0L
        val day = dateParts[2].toIntOrNull() ?: return 0L
        val hour = timeParts[0].toIntOrNull() ?: return 0L
        val min = timeParts[1].toIntOrNull() ?: return 0L
        val sec = timeParts[2].substringBefore('.').toIntOrNull() ?: return 0L
        val daysSinceEpoch = civilDaysFromEpoch(year, month, day)
        (daysSinceEpoch * 86_400L + hour * 3_600L + min * 60L + sec) * 1_000L
    } catch (_: Exception) {
        0L
    }
}

/** Days from 1970-01-01 using Howard Hinnant's civil algorithm. */
private fun civilDaysFromEpoch(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val m = if (month <= 2) month + 9 else month - 3
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * m + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
}

package app.haulio.android.services.traffic

import app.haulio.shared.navigation.models.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

enum class CongestionLevel { CLEAR, MODERATE, HEAVY }

enum class IncidentType {
    CONSTRUCTION, ACCIDENT, ROAD_CLOSED, POLICE, POTHOLE;

    fun label(): String = when (this) {
        CONSTRUCTION -> "Road Work"
        ACCIDENT     -> "Accident"
        ROAD_CLOSED  -> "Road Closed"
        POLICE       -> "Police"
        POTHOLE      -> "Pothole"
    }

    fun iconLetter(): String = when (this) {
        CONSTRUCTION -> "C"
        ACCIDENT     -> "A"
        ROAD_CLOSED  -> "X"
        POLICE       -> "P"
        POTHOLE      -> "H"
    }
}

data class BoundingBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
)

data class TrafficEvent(
    val id: String,
    val type: IncidentType,
    val lat: Double,
    val lon: Double,
    val description: String,
    val timestampMs: Long,
    val isCrowdSourced: Boolean,
    val jamFactor: Float,         // 0.0 = free flow, 1.0 = standstill
    val expiresAtMs: Long,
    val isUserReported: Boolean = false,
)

data class RouteOption(
    val id: String,
    val label: String,            // "Fastest" / "Avoid Highway" / "Shortest"
    val etaMinutes: Int,
    val delayMinutes: Int,        // extra delay due to traffic
    val distanceMiles: Double,
    val congestionLevel: CongestionLevel,
    val costing: String,
)

data class RerouteSuggestion(
    val savesMinutes: Int,
    val route: RouteOption,
)

// ---------------------------------------------------------------------------
// Bridge interfaces — mirror the KMM traffic layer surface
// ---------------------------------------------------------------------------

interface ITrafficAggregator {
    val events: Flow<List<TrafficEvent>>
    suspend fun refresh()
}

interface IRouteClient {
    suspend fun fetchAlternatives(
        origin: GeoPoint,
        destination: GeoPoint,
        alts: Int,
        costing: String,
    ): Result<List<RouteOption>>
}

interface IIncidentRepository {
    suspend fun report(type: IncidentType, lat: Double, lon: Double): Result<Unit>
    fun nearbyIncidents(bbox: BoundingBox): Flow<List<TrafficEvent>>
}

interface IRerouteListener {
    val suggestions: Flow<RerouteSuggestion>
}

// ---------------------------------------------------------------------------
// KMM real-implementation adapters (placeholder — wire in when KMM ships)
// ---------------------------------------------------------------------------

// class KmmTrafficAggregatorBridge(private val delegate: ...) : ITrafficAggregator { ... }
// class KmmRouteClientBridge(private val delegate: ...) : IRouteClient { ... }
// class KmmIncidentRepositoryBridge(private val delegate: ...) : IIncidentRepository { ... }
// class KmmRerouteListenerBridge(private val delegate: ...) : IRerouteListener { ... }

// ---------------------------------------------------------------------------
// Mock data — 5 realistic incidents around San Francisco
// ---------------------------------------------------------------------------

private val SF_INCIDENTS = listOf(
    TrafficEvent(
        id            = "incident_1",
        type          = IncidentType.CONSTRUCTION,
        lat           = 37.7749,
        lon           = -122.4194,
        description   = "Road construction on Market St — expect delays",
        timestampMs   = System.currentTimeMillis() - 1_800_000L,
        isCrowdSourced = false,
        jamFactor     = 0.7f,
        expiresAtMs   = System.currentTimeMillis() + 3_600_000L,
    ),
    TrafficEvent(
        id            = "incident_2",
        type          = IncidentType.ACCIDENT,
        lat           = 37.7831,
        lon           = -122.4090,
        description   = "Multi-vehicle accident on Van Ness Ave, right lane blocked",
        timestampMs   = System.currentTimeMillis() - 600_000L,
        isCrowdSourced = true,
        jamFactor     = 0.9f,
        expiresAtMs   = System.currentTimeMillis() + 1_800_000L,
    ),
    TrafficEvent(
        id            = "incident_3",
        type          = IncidentType.ROAD_CLOSED,
        lat           = 37.7697,
        lon           = -122.4269,
        description   = "Mission Street fully closed between 16th and 18th",
        timestampMs   = System.currentTimeMillis() - 3_600_000L,
        isCrowdSourced = false,
        jamFactor     = 1.0f,
        expiresAtMs   = System.currentTimeMillis() + 7_200_000L,
    ),
    TrafficEvent(
        id            = "incident_4",
        type          = IncidentType.POLICE,
        lat           = 37.7893,
        lon           = -122.4001,
        description   = "Police activity near Bay Bridge on-ramp",
        timestampMs   = System.currentTimeMillis() - 300_000L,
        isCrowdSourced = true,
        jamFactor     = 0.5f,
        expiresAtMs   = System.currentTimeMillis() + 1_200_000L,
    ),
    TrafficEvent(
        id            = "incident_5",
        type          = IncidentType.POTHOLE,
        lat           = 37.7612,
        lon           = -122.4358,
        description   = "Large pothole on Cesar Chavez near Dolores",
        timestampMs   = System.currentTimeMillis() - 7_200_000L,
        isCrowdSourced = true,
        jamFactor     = 0.2f,
        expiresAtMs   = System.currentTimeMillis() + 86_400_000L,
    ),
)

private val MOCK_ROUTES = listOf(
    RouteOption(
        id             = "route_fastest",
        label          = "Fastest",
        etaMinutes     = 32,
        delayMinutes   = 8,
        distanceMiles  = 7.2,
        congestionLevel = CongestionLevel.MODERATE,
        costing        = "auto",
    ),
    RouteOption(
        id             = "route_avoid_highway",
        label          = "Avoid Highway",
        etaMinutes     = 38,
        delayMinutes   = 2,
        distanceMiles  = 6.8,
        congestionLevel = CongestionLevel.CLEAR,
        costing        = "auto",
    ),
    RouteOption(
        id             = "route_shortest",
        label          = "Shortest",
        etaMinutes     = 29,
        delayMinutes   = 15,
        distanceMiles  = 5.9,
        congestionLevel = CongestionLevel.HEAVY,
        costing        = "auto",
    ),
)

// ---------------------------------------------------------------------------
// Mock implementations
// ---------------------------------------------------------------------------

class MockTrafficAggregator : ITrafficAggregator {
    private val _events = MutableStateFlow<List<TrafficEvent>>(SF_INCIDENTS)
    override val events: Flow<List<TrafficEvent>> = _events.asStateFlow()

    override suspend fun refresh() {
        delay(500)
        _events.value = SF_INCIDENTS
    }
}

class MockRouteClient : IRouteClient {
    override suspend fun fetchAlternatives(
        origin: GeoPoint,
        destination: GeoPoint,
        alts: Int,
        costing: String,
    ): Result<List<RouteOption>> {
        delay(800)
        return Result.success(MOCK_ROUTES.take(alts.coerceIn(1, MOCK_ROUTES.size)))
    }
}

class MockIncidentRepository : IIncidentRepository {
    private val _reported = MutableStateFlow<List<TrafficEvent>>(emptyList())

    override suspend fun report(type: IncidentType, lat: Double, lon: Double): Result<Unit> {
        delay(400)
        val event = TrafficEvent(
            id             = "user_${System.currentTimeMillis()}",
            type           = type,
            lat            = lat,
            lon            = lon,
            description    = "Reported by Haulio driver",
            timestampMs    = System.currentTimeMillis(),
            isCrowdSourced = true,
            jamFactor      = when (type) {
                IncidentType.ROAD_CLOSED  -> 1.0f
                IncidentType.ACCIDENT     -> 0.9f
                IncidentType.CONSTRUCTION -> 0.7f
                IncidentType.POLICE       -> 0.5f
                IncidentType.POTHOLE      -> 0.2f
            },
            expiresAtMs    = System.currentTimeMillis() + 3_600_000L,
            isUserReported = true,
        )
        _reported.value = _reported.value + event
        return Result.success(Unit)
    }

    override fun nearbyIncidents(bbox: BoundingBox): Flow<List<TrafficEvent>> =
        MutableStateFlow(SF_INCIDENTS + _reported.value)
}

class MockRerouteListener : IRerouteListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _suggestions = MutableSharedFlow<RerouteSuggestion>(extraBufferCapacity = 4)
    override val suggestions: Flow<RerouteSuggestion> = _suggestions.asSharedFlow()

    init {
        scope.launch {
            delay(15_000)
            _suggestions.emit(
                RerouteSuggestion(
                    savesMinutes = 6,
                    route        = MOCK_ROUTES.first { it.id == "route_avoid_highway" },
                )
            )
        }
    }
}

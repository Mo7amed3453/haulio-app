package app.haulio.shared.address

import app.haulio.shared.address.cache.AddressCache
import app.haulio.shared.address.geocoding.GeocodingManager
import app.haulio.shared.address.geocoding.GeocodioClient
import app.haulio.shared.address.geocoding.PeliasClient
import app.haulio.shared.address.geocoding.models.GeocodioFields
import app.haulio.shared.address.geocoding.models.GeocodioLocation
import app.haulio.shared.address.geocoding.models.GeocodioResponse
import app.haulio.shared.address.geocoding.models.GeocodioResult
import app.haulio.shared.address.geocoding.models.GeocodioZip4
import app.haulio.shared.address.geocoding.models.PeliasFeature
import app.haulio.shared.address.geocoding.models.PeliasGeometry
import app.haulio.shared.address.geocoding.models.PeliasProperties
import app.haulio.shared.address.geocoding.models.PeliasResponse
import app.haulio.shared.address.models.ConfidenceLevel
import app.haulio.shared.address.models.GeocodingSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeocodingManagerTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var currentTime = 1_000_000_000L

    @BeforeTest
    fun setup() {
        currentTime = 1_000_000_000L
    }

    private fun createMockPeliasResponse(confidence: Double, label: String): String {
        val response = PeliasResponse(
            features = listOf(
                PeliasFeature(
                    geometry = PeliasGeometry(coordinates = listOf(-93.62, 42.03)),
                    properties = PeliasProperties(
                        confidence = confidence,
                        label = label,
                        houseNumber = "123",
                        street = "North Main Street",
                        locality = "Ames",
                        regionAbbr = "IA",
                        postalcode = "50010",
                    ),
                )
            )
        )
        return json.encodeToString(response)
    }

    private fun createMockGeocodioResponse(accuracy: Double, zip4: String?): String {
        val response = GeocodioResponse(
            results = listOf(
                GeocodioResult(
                    location = GeocodioLocation(lat = 42.03, lng = -93.62),
                    formattedAddress = "123 North Main Street, Ames, IA 50010",
                    accuracy = accuracy,
                    accuracyType = "rooftop",
                    fields = if (zip4 != null) GeocodioFields(zip4 = GeocodioZip4(zip4 = zip4)) else null,
                )
            )
        )
        return json.encodeToString(response)
    }

    private fun createHttpClient(responseBody: String, statusCode: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = responseBody,
                        status = statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Test
    fun peliasHighConfidenceReturnsVerified() = runTest {
        val mockClient = createHttpClient(createMockPeliasResponse(0.9, "123 N Main St, Ames, IA 50010"))
        val peliasClient = PeliasClient(mockClient, "https://geo.haulio.app")
        val geocodioClient = GeocodioClient(mockClient, "test-key")
        val cache = AddressCache(currentTimeProvider = { currentTime })

        val manager = GeocodingManager(peliasClient, geocodioClient, cache)
        val result = manager.geocode("123 N Main St, Ames IA 50010")

        assertNotNull(result)
        assertEquals(GeocodingSource.PELIAS, result.source)
        assertTrue(result.confidence >= 0.7)
    }

    @Test
    fun peliasLowConfidenceFallsBackToGeocodio() = runTest {
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            val responseBody = if (request.url.host == "geo.haulio.app") {
                createMockPeliasResponse(0.3, "123 Main St, Ames, IA")
            } else {
                createMockGeocodioResponse(0.9, "4516")
            }
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val peliasClient = PeliasClient(client, "https://geo.haulio.app")
        val geocodioClient = GeocodioClient(client, "test-key", "https://api.geocod.io/v1.7")
        val cache = AddressCache(currentTimeProvider = { currentTime })

        val manager = GeocodingManager(peliasClient, geocodioClient, cache)
        val result = manager.geocode("123 Main St, Ames IA 50010")

        assertNotNull(result)
        assertEquals(GeocodingSource.GEOCODIO, result.source)
        assertEquals("4516", result.zip4)
    }

    @Test
    fun cachedResultReturnedWithoutNetworkCall() = runTest {
        val mockClient = createHttpClient(createMockPeliasResponse(0.9, "123 N Main St, Ames, IA 50010"))
        val peliasClient = PeliasClient(mockClient, "https://geo.haulio.app")
        val geocodioClient = GeocodioClient(mockClient, "test-key")
        val cache = AddressCache(currentTimeProvider = { currentTime })

        val manager = GeocodingManager(peliasClient, geocodioClient, cache)

        // First call - populates cache
        val result1 = manager.geocode("123 N Main St, Ames IA 50010")
        assertNotNull(result1)

        // Second call - should return from cache
        val result2 = manager.geocode("123 N Main St, Ames IA 50010")
        assertNotNull(result2)
        assertEquals(GeocodingSource.CACHE, result2.source)
    }

    @Test
    fun expiredCacheDoesNotReturnStaleResult() = runTest {
        val mockClient = createHttpClient(createMockPeliasResponse(0.9, "123 N Main St, Ames, IA 50010"))
        val peliasClient = PeliasClient(mockClient, "https://geo.haulio.app")
        val geocodioClient = GeocodioClient(mockClient, "test-key")
        val cache = AddressCache(
            ttlMillis = 1000L, // 1 second TTL for testing
            currentTimeProvider = { currentTime },
        )

        val manager = GeocodingManager(peliasClient, geocodioClient, cache)

        // First call
        val result1 = manager.geocode("123 N Main St, Ames IA 50010")
        assertNotNull(result1)

        // Advance time past TTL
        currentTime += 2000L

        // Second call - cache expired, should hit network
        val result2 = manager.geocode("123 N Main St, Ames IA 50010")
        assertNotNull(result2)
        assertEquals(GeocodingSource.PELIAS, result2.source)
    }

    @Test
    fun bothFailReturnNotFound() = runTest {
        val emptyPelias = json.encodeToString(PeliasResponse(features = emptyList()))
        val emptyGeocodio = json.encodeToString(GeocodioResponse(results = emptyList()))

        val mockEngine = MockEngine { request ->
            val body = if (request.url.host == "geo.haulio.app") emptyPelias else emptyGeocodio
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val peliasClient = PeliasClient(client, "https://geo.haulio.app")
        val geocodioClient = GeocodioClient(client, "test-key", "https://api.geocod.io/v1.7")
        val cache = AddressCache(currentTimeProvider = { currentTime })

        val manager = GeocodingManager(peliasClient, geocodioClient, cache)
        val result = manager.geocode("zzzzz invalid address 99999")

        assertNotNull(result)
        assertEquals(ConfidenceLevel.NOT_FOUND, result.badge)
    }
}

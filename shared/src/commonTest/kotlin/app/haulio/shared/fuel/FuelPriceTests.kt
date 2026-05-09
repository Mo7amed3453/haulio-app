package app.haulio.shared.fuel

import app.haulio.shared.fuel.crowd.FuelBbox
import app.haulio.shared.fuel.crowd.FuelGrade
import app.haulio.shared.fuel.crowd.IFuelReportSource
import app.haulio.shared.fuel.crowd.MockFuelReportClient
import app.haulio.shared.fuel.models.FuelPrice
import app.haulio.shared.fuel.models.FuelStation
import app.haulio.shared.fuel.sources.EiaClient
import app.haulio.shared.fuel.sources.OsmFuelClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FuelPriceTests {

    // -------------------------------------------------------------------------
    // Test 1: EiaClient.petroleumDistrictForLocation — Bay Area → PADD 5
    // -------------------------------------------------------------------------

    @Test
    fun `EiaClient - Bay Area maps to PADD 5 (West Coast)`() {
        val mockEngine = MockEngine { respond("{}") }
        val client = EiaClient(buildHttpClient(mockEngine))

        val district = client.petroleumDistrictForLocation(lat = 37.77, lng = -122.42) // San Francisco
        assertEquals("R50", district, "San Francisco should be PADD 5 (West Coast)")
    }

    // -------------------------------------------------------------------------
    // Test 2: EiaClient.petroleumDistrictForLocation — New York → PADD 1B
    // -------------------------------------------------------------------------

    @Test
    fun `EiaClient - New York maps to PADD 1B (Central Atlantic)`() {
        val mockEngine = MockEngine { respond("{}") }
        val client = EiaClient(buildHttpClient(mockEngine))

        val district = client.petroleumDistrictForLocation(lat = 40.71, lng = -74.01) // NYC
        assertEquals("R1Y", district, "New York City should be PADD 1B (Central Atlantic)")
    }

    // -------------------------------------------------------------------------
    // Test 3: EiaClient — parses a well-formed API response
    // -------------------------------------------------------------------------

    @Test
    fun `EiaClient - parses valid EIA response into FuelPrice`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = EIA_REGULAR_RESPONSE,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = EiaClient(buildHttpClient(mockEngine))

        val result = client.fetchRegionalPrice(lat = 37.77, lng = -122.42)
        val price = result.getOrThrow()

        assertEquals(4.329, price.regularUsd)
        assertEquals("EIA", price.source)
    }

    // -------------------------------------------------------------------------
    // Test 4: OsmFuelClient — parses Overpass response into FuelStation list
    // -------------------------------------------------------------------------

    @Test
    fun `OsmFuelClient - parses Overpass response into stations`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = OVERPASS_RESPONSE,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OsmFuelClient(buildHttpClient(mockEngine))

        val result = client.fetchStationsInBbox(37.7, -122.5, 37.8, -122.3)
        val stations = result.getOrThrow()

        assertEquals(2, stations.size)
        assertEquals("osm_123456", stations[0].id)
        assertEquals("Shell", stations[0].brand)
        assertEquals(37.7749, stations[0].lat)
    }

    // -------------------------------------------------------------------------
    // Test 5: MockFuelReportClient — fetchActive returns non-empty list
    // -------------------------------------------------------------------------

    @Test
    fun `MockFuelReportClient - fetchActive returns mock prices`() = runTest {
        val client = MockFuelReportClient()
        val bbox = FuelBbox(37.7, -122.5, 37.8, -122.3)

        val result = client.fetchActive(bbox)
        val prices = result.getOrThrow()

        assertTrue(prices.isNotEmpty(), "Mock client should return at least one price")
        assertTrue(prices.first().regularUsd > 0.0)
    }

    // -------------------------------------------------------------------------
    // Test 6: MockFuelReportClient — submit adds a new price entry
    // -------------------------------------------------------------------------

    @Test
    fun `MockFuelReportClient - submit succeeds and reflects in fetchActive`() = runTest {
        val client = MockFuelReportClient()
        val bbox = FuelBbox(37.7, -122.5, 37.8, -122.3)

        val before = client.fetchActive(bbox).getOrThrow().size
        val submitResult = client.submit("station_1", 4.79, FuelGrade.PREMIUM)
        val after = client.fetchActive(bbox).getOrThrow().size

        assertTrue(submitResult.isSuccess)
        assertEquals(before + 1, after, "Submit should add one new entry to the mock store")
    }

    // -------------------------------------------------------------------------
    // Test 7: FuelDataAggregator — merges OSM stations with crowd prices
    // -------------------------------------------------------------------------

    @Test
    fun `FuelDataAggregator - merges OSM stations with available crowd prices`() = runTest {
        val osmEngine = MockEngine {
            respond(
                content = OVERPASS_RESPONSE,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val eiaEngine = MockEngine {
            respond(
                content = EIA_REGULAR_RESPONSE,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val osmClient = OsmFuelClient(buildHttpClient(osmEngine))
        val eiaClient = EiaClient(buildHttpClient(eiaEngine))
        val crowdClient = MockFuelReportClient()

        val aggregator = FuelDataAggregator(eiaClient, osmClient, crowdClient)

        val result = aggregator.getNearbyStations(37.7, -122.5, 37.8, -122.3)
        val stations = result.getOrThrow()

        assertTrue(stations.isNotEmpty())
        // First station should have crowd price merged in (MockFuelReportClient has data)
        assertNotNull(stations.firstOrNull()?.latestPrice)
    }

    // -------------------------------------------------------------------------
    // Test 8: distanceDeg — zero distance for identical points
    // -------------------------------------------------------------------------

    @Test
    fun `distanceDeg - returns zero for identical coordinates`() {
        val d = distanceDeg(37.77, -122.42, 37.77, -122.42)
        assertEquals(0.0, d, "Distance to self should be zero")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildHttpClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    companion object {
        private val EIA_REGULAR_RESPONSE = """
            {
              "response": {
                "data": [
                  {
                    "period": "2024-01-15",
                    "duoarea": "R50",
                    "area-name": "West Coast",
                    "product": "EPM0",
                    "product-name": "Regular Gasoline",
                    "value": 4.329,
                    "units": "$/gal"
                  }
                ]
              }
            }
        """.trimIndent()

        private val OVERPASS_RESPONSE = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 123456,
                  "lat": 37.7749,
                  "lon": -122.4194,
                  "tags": { "amenity": "fuel", "brand": "Shell", "name": "Shell Mission St" }
                },
                {
                  "type": "node",
                  "id": 789012,
                  "lat": 37.7820,
                  "lon": -122.4090,
                  "tags": { "amenity": "fuel", "brand": "Chevron", "name": "Chevron Van Ness" }
                }
              ]
            }
        """.trimIndent()
    }
}

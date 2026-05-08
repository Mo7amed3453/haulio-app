package app.haulio.shared

import app.haulio.shared.navigation.mapmatching.MeiliClient
import app.haulio.shared.navigation.mapmatching.TimedGeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MeiliClientTest {
    @Test
    fun snapsPointsToRoadNetwork() = runTest {
        val responseJson = """
            {
              "matchedPoints": [
                {"lat": 40.7130, "lon": -74.0058},
                {"lat": 40.7135, "lon": -74.0055}
              ]
            }
        """.trimIndent()
        val engine = MockEngine { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                ),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val client = MeiliClient(httpClient)
        val result = client.traceRoute(
            listOf(
                TimedGeoPoint(40.7128, -74.0060, 1000),
                TimedGeoPoint(40.7132, -74.0057, 1001),
            )
        )
        assertEquals(2, result.size)
        assertEquals(40.7130, result[0].lat)
        assertEquals(-74.0058, result[0].lon)
    }

    @Test
    fun returnsEmptyForEmptyInput() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"matchedPoints": []}""",
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                ),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val client = MeiliClient(httpClient)
        val result = client.traceRoute(emptyList())
        assertEquals(0, result.size)
    }
}

package app.haulio.shared

import app.haulio.shared.navigation.ValhallaRouteClient
import app.haulio.shared.navigation.models.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValhallaRouteClientTest {
    @Test
    suspend fun parsesRouteManeuvers() {
        val jsonPayload = """
            {
              "trip": {
                "legs": [{
                  "shape": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                  "maneuvers": [
                    {
                      "type": 9,
                      "instruction": "Turn left",
                      "length": 1.2,
                      "street_names": ["Main St"],
                      "begin_shape_index": 0,
                      "end_shape_index": 1
                    },
                    {
                      "type": 4,
                      "instruction": "Turn right",
                      "length": 0.8,
                      "street_names": ["2nd Ave"],
                      "begin_shape_index": 1,
                      "end_shape_index": 2
                    }
                  ]
                }]
              }
            }
        """.trimIndent()
        val engine = MockEngine { _ ->
            respond(
                content = jsonPayload,
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val routeClient = ValhallaRouteClient(client, "https://routing.haulio.app")
        val route = routeClient.getRoute(listOf(GeoPoint(1.0, 1.0), GeoPoint(2.0, 2.0)))
        assertEquals(2, route.steps.size)
        assertEquals(2.0, route.totalDistanceMiles)
        assertTrue(route.decodedShape.isNotEmpty())
    }
}

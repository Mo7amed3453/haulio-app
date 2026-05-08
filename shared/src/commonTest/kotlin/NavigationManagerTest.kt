package app.haulio.shared

import app.haulio.shared.navigation.NavigationManager
import app.haulio.shared.navigation.ValhallaRouteClient
import app.haulio.shared.navigation.models.GeoPoint
import app.haulio.shared.navigation.models.NavigationState
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
import kotlin.test.assertTrue

class NavigationManagerTest {
    @Test
    fun transitionsToNavigatingAndArrived() = runTest {
        val payload = """
            {
              "trip": {
                "legs": [{
                  "shape": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                  "maneuvers": [{
                    "type": 2,
                    "instruction": "Continue",
                    "length": 1.0,
                    "street_names": ["Road"],
                    "begin_shape_index": 0,
                    "end_shape_index": 2
                  }]
                }]
              }
            }
        """.trimIndent()
        val engine = MockEngine { _ ->
            respond(
                payload,
                HttpStatusCode.OK,
                io.ktor.http.headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val manager = NavigationManager(routeClient = ValhallaRouteClient(httpClient))
        val destination = GeoPoint(1.0, 1.0)
        manager.startNavigation(GeoPoint(0.999, 0.999), destination)
        assertTrue(manager.state.value is NavigationState.Navigating)
        manager.updateLocation(destination, 1)
        assertTrue(manager.state.value is NavigationState.Arrived)
    }
}

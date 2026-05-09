package app.haulio.shared.traffic.reroute

/**
 * Abstraction over the Valhalla routing engine.
 *
 * Implement this interface per platform by injecting the appropriate Ktor client.
 */
interface IRouteClient {

    /**
     * Requests a driving route between two coordinates.
     *
     * @param fromLat Origin latitude in decimal degrees.
     * @param fromLng Origin longitude in decimal degrees.
     * @param toLat Destination latitude in decimal degrees.
     * @param toLng Destination longitude in decimal degrees.
     * @return [Result.success] wrapping a [RouteResponse], or [Result.failure] with a
     *         [RouteError] describing what went wrong.
     */
    suspend fun route(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
    ): Result<RouteResponse>
}

/**
 * Structured error types returned by [IRouteClient].
 */
sealed class RouteError(override val message: String) : Exception(message) {
    data class NetworkError(val cause: Throwable) : RouteError("Network error: ${cause.message}")
    data class ServerError(val code: Int) : RouteError("Server error HTTP $code")
    data class NoRouteFound(val reason: String = "") : RouteError("No route found: $reason")
    data class ParseError(val cause: Throwable) : RouteError("Parse error: ${cause.message}")
}

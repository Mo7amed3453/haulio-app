package app.haulio.shared.traffic.reroute

/**
 * Emitted by [AutoRerouteEngine] when a faster route is available.
 *
 * @property reason Human-readable explanation of why the reroute was triggered.
 * @property savedMinutes Estimated time saved compared to the current route (minutes).
 * @property newRoute The freshly computed [RouteResponse] from Valhalla.
 * @property triggeredAtMs Epoch-millisecond when the suggestion was generated.
 */
data class RerouteSuggestion(
    val reason: String,
    val savedMinutes: Double,
    val newRoute: RouteResponse,
    val triggeredAtMs: Long,
)

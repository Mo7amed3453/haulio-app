package app.haulio.shared.traffic.models

/**
 * Classification of a traffic event observed on the road network.
 */
enum class TrafficEventType {
    ACCIDENT,
    CONSTRUCTION,
    CLOSURE,
    POLICE,
    POTHOLE,
    CONGESTION,
}

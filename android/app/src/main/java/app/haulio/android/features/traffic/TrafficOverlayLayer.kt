package app.haulio.android.features.traffic

import android.graphics.Color
import app.haulio.android.services.traffic.TrafficEvent
import com.google.gson.JsonObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Manages a MapLibre [LineLayer] that colours road segments by traffic
 * congestion level (data-driven interpolation on the `jam_factor` property).
 *
 * Call [setup] once after the style is loaded, then [update] on every data
 * change to refresh the GeoJSON source.
 */
object TrafficOverlayLayer {

    const val SOURCE_ID = "haulio-traffic-lines"
    const val LAYER_ID  = "haulio-traffic-layer"

    /**
     * Adds the GeoJSON source and styled [LineLayer] to [style].
     * No-ops if they already exist (safe to call multiple times).
     */
    fun setup(style: Style) {
        if (style.getSource(SOURCE_ID) != null) return

        style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))

        val layer = LineLayer(LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(buildColorExpression()),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.85f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        style.addLayer(layer)
    }

    /**
     * Updates the GeoJSON source with current [events] and toggles layer
     * visibility according to [visible].
     */
    fun update(style: Style, events: List<TrafficEvent>, visible: Boolean) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return

        val features = events.map { event -> buildLineFeature(event) }
        source.setGeoJson(FeatureCollection.fromFeatures(features))

        style.getLayer(LAYER_ID)?.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
        )
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Generates a short (~600 m) east-west line segment centred on the
     * incident's location, tagged with the event's [TrafficEvent.jamFactor].
     * In production this would use real road-segment geometry from the API.
     */
    private fun buildLineFeature(event: TrafficEvent): Feature {
        val offset = 0.003 // ~300 m longitude offset
        val line = LineString.fromLngLats(
            listOf(
                Point.fromLngLat(event.lon - offset, event.lat),
                Point.fromLngLat(event.lon + offset, event.lat),
            )
        )
        val props = JsonObject().apply {
            addProperty("jam_factor",   event.jamFactor.toDouble())
            addProperty("incident_id",  event.id)
        }
        return Feature.fromGeometry(line, props)
    }

    /**
     * Data-driven interpolation: 0 → green, 0.5 → yellow, 0.7 → orange, 0.9 → red.
     */
    private fun buildColorExpression(): Expression =
        Expression.interpolate(
            Expression.linear(),
            Expression.get("jam_factor"),
            Expression.literal(0.0),  Expression.color(Color.parseColor("#00e676")),
            Expression.literal(0.5),  Expression.color(Color.parseColor("#ffd740")),
            Expression.literal(0.7),  Expression.color(Color.parseColor("#ff6b35")),
            Expression.literal(0.9),  Expression.color(Color.parseColor("#ff5252")),
        )
}

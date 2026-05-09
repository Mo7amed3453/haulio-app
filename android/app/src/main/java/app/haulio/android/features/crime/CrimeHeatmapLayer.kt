package app.haulio.android.features.crime

import android.graphics.Color
import app.haulio.shared.crime.models.CrimeGridCell
import com.google.gson.JsonObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Manages a MapLibre [HeatmapLayer] that renders the crime risk heatmap.
 *
 * Color interpolation on [risk_score]:
 *   0  → #00e676 (green)
 *   3  → #ffeb3b (yellow)
 *   6  → #ff9800 (orange)
 *   9  → #ff1744 (red)
 *
 * Call [setup] once after the style is ready, then [update] on each data change.
 */
object CrimeHeatmapLayer {

    const val SOURCE_ID = "haulio-crime-source"
    const val LAYER_ID  = "haulio-crime-heatmap"

    /** Heatmap weight driven by the risk_score property (0–10 range). */
    private val WEIGHT_EXPRESSION: Expression = Expression.interpolate(
        Expression.linear(),
        Expression.get("risk_score"),
        Expression.literal(0.0), Expression.literal(0.0),
        Expression.literal(10.0), Expression.literal(1.0),
    )

    /** Data-driven colour stops mapped to risk levels. */
    private val COLOR_EXPRESSION: Expression = Expression.interpolate(
        Expression.linear(),
        Expression.heatmapDensity(),
        Expression.literal(0.0), Expression.color(Color.parseColor("#00e676")),
        Expression.literal(0.3), Expression.color(Color.parseColor("#ffeb3b")),
        Expression.literal(0.6), Expression.color(Color.parseColor("#ff9800")),
        Expression.literal(1.0), Expression.color(Color.parseColor("#ff1744")),
    )

    /**
     * Adds the GeoJSON source and [HeatmapLayer] to [style].
     * No-ops safely if the source already exists.
     */
    fun setup(style: Style) {
        if (style.getSource(SOURCE_ID) != null) return

        style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))

        val layer = HeatmapLayer(LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.heatmapWeight(WEIGHT_EXPRESSION),
                PropertyFactory.heatmapRadius(30f),
                PropertyFactory.heatmapOpacity(0.6f),
                PropertyFactory.heatmapColor(COLOR_EXPRESSION),
            )
        }
        style.addLayer(layer)
    }

    /**
     * Updates the GeoJSON source with the current [cells] and toggles
     * layer visibility according to [visible].
     */
    fun update(style: Style, cells: List<CrimeGridCell>, visible: Boolean) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(cells.map { buildFeature(it) }))
        style.getLayer(LAYER_ID)?.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
        )
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun buildFeature(cell: CrimeGridCell): Feature {
        val props = JsonObject().apply {
            addProperty("cell_id",        cell.cellId)
            addProperty("risk_score",     cell.riskScore)
            addProperty("incident_count", cell.incidentCount)
            cell.topCrimeType?.let { addProperty("top_crime_type", it) }
        }
        return Feature.fromGeometry(Point.fromLngLat(cell.lng, cell.lat), props)
    }
}

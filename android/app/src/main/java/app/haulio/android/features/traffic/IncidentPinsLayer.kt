package app.haulio.android.features.traffic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import app.haulio.android.services.traffic.IncidentType
import app.haulio.android.services.traffic.TrafficEvent
import com.google.gson.JsonObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Manages a MapLibre [SymbolLayer] that renders incident pins on the map.
 *
 * Each incident type gets a distinct coloured circle icon with a letter
 * label (C / A / X / P / H), added as bitmap images to the style.
 * Tap detection is handled externally via [queryAtPoint].
 */
object IncidentPinsLayer {

    const val SOURCE_ID = "haulio-incident-source"
    const val LAYER_ID  = "haulio-incident-layer"

    private val ICON_SPEC: Map<IncidentType, Pair<Int, String>> = mapOf(
        IncidentType.CONSTRUCTION to (Color.parseColor("#FF9800") to "C"),
        IncidentType.ACCIDENT     to (Color.parseColor("#F44336") to "A"),
        IncidentType.ROAD_CLOSED  to (Color.parseColor("#9C27B0") to "X"),
        IncidentType.POLICE       to (Color.parseColor("#1565C0") to "P"),
        IncidentType.POTHOLE      to (Color.parseColor("#795548") to "H"),
    )

    private fun iconId(type: IncidentType) = "haulio-pin-${type.name.lowercase()}"

    /**
     * Adds bitmap icons, the GeoJSON source, and a [SymbolLayer] to [style].
     * No-ops if the source already exists.
     */
    fun setup(style: Style) {
        if (style.getSource(SOURCE_ID) != null) return

        // Register a unique bitmap image per incident type
        ICON_SPEC.forEach { (type, spec) ->
            val (iconColor, letter) = spec
            style.addImage(iconId(type), createPinBitmap(iconColor, letter))
        }

        style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))

        val layer = SymbolLayer(LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("icon_id")),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        }
        style.addLayer(layer)
    }

    /**
     * Updates the GeoJSON source with the current list of [events].
     */
    fun update(style: Style, events: List<TrafficEvent>) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val features = events.map { event -> buildPointFeature(event) }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /**
     * Queries rendered features at [screenPoint] and returns the incident ID
     * of the first hit, or null if no incident pin was tapped.
     */
    fun queryAtPoint(map: MapLibreMap, screenPoint: PointF): String? =
        map.queryRenderedFeatures(screenPoint, LAYER_ID)
            .firstOrNull()
            ?.getStringProperty("incident_id")

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun buildPointFeature(event: TrafficEvent): Feature {
        val props = JsonObject().apply {
            addProperty("incident_id",   event.id)
            addProperty("incident_type", event.type.name)
            addProperty("description",   event.description)
            addProperty("timestamp_ms",  event.timestampMs)
            addProperty("crowd_sourced", event.isCrowdSourced)
            addProperty("icon_id",       iconId(event.type))
        }
        return Feature.fromGeometry(Point.fromLngLat(event.lon, event.lat), props)
    }

    /**
     * Creates a 64×64 coloured circle bitmap with a white letter centred in it.
     * No [android.content.Context] is required — uses raw [Canvas] + [Paint].
     */
    private fun createPinBitmap(bgColor: Int, letter: String): Bitmap {
        val size   = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val radius = size / 2f - 4f
        canvas.drawCircle(size / 2f, size / 2f, radius, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = Color.WHITE
            style       = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(size / 2f, size / 2f, radius, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.WHITE
            textSize  = size * 0.38f
            textAlign = Paint.Align.CENTER
            typeface  = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(letter, size / 2f, size / 2f + textPaint.textSize / 3f, textPaint)

        return bitmap
    }
}

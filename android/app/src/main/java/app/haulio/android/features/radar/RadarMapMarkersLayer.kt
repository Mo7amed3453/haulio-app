package app.haulio.android.features.radar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import app.haulio.shared.radar.models.SpeedCamera
import com.google.gson.JsonObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Manages a MapLibre [SymbolLayer] that renders speed camera pins on the map.
 *
 * Each camera is represented as a red circle with "MPH" text (or the posted speed
 * limit if known). Icon bitmaps are drawn programmatically without Context.
 *
 * Pattern mirrors [app.haulio.android.features.fuel.FuelMapMarkersLayer] and
 * [app.haulio.android.features.traffic.IncidentPinsLayer].
 */
object RadarMapMarkersLayer {

    const val SOURCE_ID = "haulio-radar-source"
    const val LAYER_ID  = "haulio-radar-layer"

    private const val ICON_ID_DEFAULT = "haulio-radar-pin-default"
    private const val ICON_COLOR      = 0xFFD32F2F.toInt() // Material Red 700

    /**
     * Adds the camera icon, GeoJSON source, and [SymbolLayer] to [style].
     * No-ops if the source already exists.
     */
    fun setup(style: Style) {
        if (style.getSource(SOURCE_ID) != null) return
        style.addImage(ICON_ID_DEFAULT, createCameraPinBitmap(null))
        // Pre-register speed icons for common limits
        listOf(25, 30, 35, 40, 45, 55, 65).forEach { mph ->
            style.addImage(iconIdForSpeed(mph), createCameraPinBitmap(mph))
        }
        style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        val layer = SymbolLayer(LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage(
                    org.maplibre.android.style.expressions.Expression.get("icon_id")
                ),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        }
        style.addLayer(layer)
    }

    /** Updates the GeoJSON source with the current [cameras]. */
    fun update(style: Style, cameras: List<SpeedCamera>, visible: Boolean) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val features = if (visible) cameras.map { buildFeature(it) } else emptyList()
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /**
     * Returns the camera ID of the first radar pin hit at [screenPoint], or null.
     */
    fun queryAtPoint(map: MapLibreMap, screenPoint: PointF): String? =
        map.queryRenderedFeatures(screenPoint, LAYER_ID)
            .firstOrNull()
            ?.getStringProperty("camera_id")

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun buildFeature(camera: SpeedCamera): Feature {
        val iconId = if (camera.postedSpeedMph != null) {
            // Use nearest pre-registered speed icon
            val rounded = roundToNearestSpeed(camera.postedSpeedMph)
            if (rounded != null) iconIdForSpeed(rounded) else ICON_ID_DEFAULT
        } else {
            ICON_ID_DEFAULT
        }
        val props = JsonObject().apply {
            addProperty("camera_id",  camera.id)
            addProperty("source",     camera.source.name)
            addProperty("speed_mph",  camera.postedSpeedMph ?: 0)
            addProperty("icon_id",    iconId)
        }
        return Feature.fromGeometry(Point.fromLngLat(camera.lng, camera.lat), props)
    }

    private fun iconIdForSpeed(mph: Int) = "haulio-radar-pin-$mph"

    private fun roundToNearestSpeed(mph: Int): Int? {
        val speeds = listOf(25, 30, 35, 40, 45, 55, 65)
        return speeds.minByOrNull { kotlin.math.abs(it - mph) }
    }

    /**
     * Creates a 64×64 red circle bitmap with "MPH" or the speed limit centred in it.
     * No [android.content.Context] required — raw [Canvas] + [Paint].
     */
    private fun createCameraPinBitmap(speedMph: Int?): Bitmap {
        val size   = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ICON_COLOR
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

        val label = if (speedMph != null) speedMph.toString() else "CAM"
        val textSize = if (speedMph != null) size * 0.32f else size * 0.26f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.WHITE
            this.textSize  = textSize
            textAlign = Paint.Align.CENTER
            typeface  = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(label, size / 2f, size / 2f + textPaint.textSize / 3f, textPaint)

        return bitmap
    }
}

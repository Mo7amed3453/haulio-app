package app.haulio.android.features.fuel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import app.haulio.android.services.fuel.FuelStation
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
 * Manages a MapLibre [SymbolLayer] that renders fuel station pins (⛽) on the map.
 *
 * Each station is represented as a circular bitmap icon with a fuel pump icon
 * added to the style. Tap detection delegates back to [MapLibreView] via [queryAtPoint].
 */
object FuelMapMarkersLayer {

    const val SOURCE_ID = "haulio-fuel-source"
    const val LAYER_ID  = "haulio-fuel-layer"

    private const val ICON_ID   = "haulio-fuel-pin"
    private const val ICON_COLOR = 0xFF2196F3.toInt()  // Material Blue 500

    /**
     * Adds the fuel icon, GeoJSON source, and [SymbolLayer] to [style].
     * No-ops if the source already exists.
     */
    fun setup(style: Style) {
        if (style.getSource(SOURCE_ID) != null) return
        style.addImage(ICON_ID, createFuelPinBitmap())
        style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        val layer = SymbolLayer(LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.iconImage(ICON_ID),
                PropertyFactory.iconSize(1.0f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        }
        style.addLayer(layer)
    }

    /** Updates the GeoJSON source with current [stations]. */
    fun update(style: Style, stations: List<FuelStation>, visible: Boolean) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val features = if (visible) stations.map { buildFeature(it) } else emptyList()
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /**
     * Returns the station id of the first fuel pin hit at [screenPoint], or null.
     */
    fun queryAtPoint(map: MapLibreMap, screenPoint: PointF): String? =
        map.queryRenderedFeatures(screenPoint, LAYER_ID)
            .firstOrNull()
            ?.getStringProperty("station_id")

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun buildFeature(station: FuelStation): Feature {
        val props = JsonObject().apply {
            addProperty("station_id",  station.id)
            addProperty("name",        station.name ?: "")
            addProperty("brand",       station.brand ?: "")
            addProperty("price_usd",   station.latestPrice?.regularUsd ?: 0.0)
        }
        return Feature.fromGeometry(Point.fromLngLat(station.lng, station.lat), props)
    }

    /**
     * Creates a 64×64 blue circle bitmap with "⛽" (fuel pump emoji) centred in it.
     */
    private fun createFuelPinBitmap(): Bitmap {
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

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.WHITE
            textSize  = size * 0.35f
            textAlign = Paint.Align.CENTER
            typeface  = Typeface.DEFAULT_BOLD
        }
        // Draw "F" as a fallback text symbol (emoji rendering on Canvas is unreliable)
        canvas.drawText("F", size / 2f, size / 2f + textPaint.textSize / 3f, textPaint)

        return bitmap
    }
}

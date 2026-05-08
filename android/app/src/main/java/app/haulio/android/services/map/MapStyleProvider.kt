package app.haulio.android.services.map

import android.content.Context
import app.haulio.android.R

class MapStyleProvider(
    val context: Context
) {
    fun loadDarkStyleJson(): String {
        return context.resources.openRawResource(R.raw.dark_style).bufferedReader().use {
            it.readText()
        }
    }
}

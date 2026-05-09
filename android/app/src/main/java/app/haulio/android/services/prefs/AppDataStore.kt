package app.haulio.android.services.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/** Singleton DataStore for all Haulio app preferences. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "haulio_prefs")

object AppPrefKeys {
    val ALWAYS_SHOW_ALTERNATIVES    = booleanPreferencesKey("pref_always_show_alternatives")
    val TRAFFIC_OVERLAY_VISIBLE     = booleanPreferencesKey("pref_traffic_overlay_visible")

    // Crime heatmap — suppress re-showing the same cell alert within 1 hour
    val CRIME_LAST_ALERT_CELL_ID    = stringPreferencesKey("pref_crime_last_alert_cell_id")
    val CRIME_LAST_ALERT_TS_MS      = longPreferencesKey("pref_crime_last_alert_ts_ms")
}

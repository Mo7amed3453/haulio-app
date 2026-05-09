package app.haulio.android.services.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/** Singleton DataStore for all Haulio app preferences. */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "haulio_prefs")

object AppPrefKeys {
    val ALWAYS_SHOW_ALTERNATIVES = booleanPreferencesKey("pref_always_show_alternatives")
    val TRAFFIC_OVERLAY_VISIBLE   = booleanPreferencesKey("pref_traffic_overlay_visible")
}

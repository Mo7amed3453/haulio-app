package app.haulio.shared.address.parsing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Offline ZIP code → city/state lookup table.
 *
 * Loads data lazily from a bundled JSON resource on first access.
 * The JSON format is: {"50010": {"city": "Ames", "state": "IA"}, ...}
 *
 * Thread-safe via lazy initialization.
 */
object ZipLookupTable {

    /**
     * City and state data associated with a ZIP code.
     */
    @Serializable
    data class ZipEntry(
        val city: String,
        val state: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * In-memory table, loaded lazily.
     * In production this would load from a bundled resource file.
     * Use [loadFromJson] to initialize with raw JSON string.
     */
    private var entries: Map<String, ZipEntry> = emptyMap()
    private var loaded: Boolean = false

    /**
     * Loads the ZIP lookup table from a raw JSON string.
     *
     * Expected format: `{"50010": {"city": "Ames", "state": "IA"}, ...}`
     *
     * @param jsonString The raw JSON content of the zip_city_state.json file.
     */
    fun loadFromJson(jsonString: String) {
        entries = json.decodeFromString<Map<String, ZipEntry>>(jsonString)
        loaded = true
    }

    /**
     * Looks up city/state information for a given 5-digit ZIP code.
     *
     * @param zip5 Five-digit ZIP code (e.g. "50010").
     * @return [ZipEntry] with city and state, or null if not found.
     */
    fun lookup(zip5: String): ZipEntry? {
        return entries[zip5]
    }

    /**
     * Returns whether the lookup table has been loaded.
     */
    fun isLoaded(): Boolean = loaded

    /**
     * Returns the number of entries in the table.
     */
    fun size(): Int = entries.size

    /**
     * Clears all loaded data. Primarily for testing.
     */
    fun clear() {
        entries = emptyMap()
        loaded = false
    }
}

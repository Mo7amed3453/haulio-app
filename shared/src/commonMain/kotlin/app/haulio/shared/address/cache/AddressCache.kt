package app.haulio.shared.address.cache

import app.haulio.shared.address.models.AddressSuggestion
import app.haulio.shared.address.models.ConfidenceLevel
import app.haulio.shared.address.models.GeocodingSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * In-memory address cache with 30-day TTL.
 *
 * In production, this would be backed by SQLDelight with the schema:
 * ```sql
 * CREATE TABLE address_cache (
 *     normalized_key TEXT NOT NULL PRIMARY KEY,
 *     response_json TEXT NOT NULL,
 *     cached_at INTEGER NOT NULL
 * );
 *
 * selectByKey:
 * SELECT * FROM address_cache
 * WHERE normalized_key = ? AND cached_at > ?;
 *
 * insertOrReplace:
 * INSERT OR REPLACE INTO address_cache
 * VALUES (?, ?, ?);
 * ```
 *
 * This implementation provides the same interface using an in-memory map,
 * suitable for common code and testing. Platform-specific SQLDelight drivers
 * can be injected for persistence.
 *
 * @param ttlMillis Time-to-live for cache entries in milliseconds (default: 30 days).
 * @param currentTimeProvider Function returning current time in epoch millis (injectable for testing).
 */
class AddressCache(
    private val ttlMillis: Long = TTL_30_DAYS,
    private val currentTimeProvider: () -> Long = { currentTimeMillis() },
) {

    private val mutex = Mutex()
    private val store = mutableMapOf<String, CacheEntry>()

    private data class CacheEntry(
        val responseJson: String,
        val cachedAt: Long,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Retrieves a cached address suggestion by its normalized key.
     *
     * @param normalizedKey The cache key (produced by [AddressNormalizer.toCacheKey]).
     * @return The cached [AddressSuggestion] if found and not expired, null otherwise.
     */
    suspend fun get(normalizedKey: String): AddressSuggestion? = mutex.withLock {
        val entry = store[normalizedKey] ?: return@withLock null
        val now = currentTimeProvider()
        if (entry.cachedAt + ttlMillis < now) {
            // Entry expired, remove it
            store.remove(normalizedKey)
            return@withLock null
        }
        return@withLock try {
            json.decodeFromString<AddressSuggestion>(entry.responseJson)
        } catch (_: Exception) {
            store.remove(normalizedKey)
            null
        }
    }

    /**
     * Stores an address suggestion in the cache.
     *
     * @param normalizedKey The cache key (produced by [AddressNormalizer.toCacheKey]).
     * @param suggestion The [AddressSuggestion] to cache.
     */
    suspend fun put(normalizedKey: String, suggestion: AddressSuggestion) = mutex.withLock {
        val responseJson = json.encodeToString(suggestion)
        store[normalizedKey] = CacheEntry(
            responseJson = responseJson,
            cachedAt = currentTimeProvider(),
        )
    }

    /**
     * Removes a specific entry from the cache.
     *
     * @param normalizedKey The cache key to remove.
     */
    suspend fun remove(normalizedKey: String) = mutex.withLock {
        store.remove(normalizedKey)
    }

    /**
     * Clears all entries from the cache.
     */
    suspend fun clear() = mutex.withLock {
        store.clear()
    }

    /**
     * Returns the number of entries currently in the cache (including expired).
     */
    suspend fun size(): Int = mutex.withLock {
        store.size
    }

    companion object {
        /** 30 days in milliseconds. */
        const val TTL_30_DAYS: Long = 30L * 24L * 60L * 60L * 1000L
    }
}

/**
 * Platform-agnostic current time provider.
 * Returns current epoch time in milliseconds.
 */
internal expect fun currentTimeMillis(): Long

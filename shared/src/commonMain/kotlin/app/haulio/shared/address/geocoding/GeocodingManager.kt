package app.haulio.shared.address.geocoding

import app.haulio.shared.address.cache.AddressCache
import app.haulio.shared.address.models.AddressSuggestion
import app.haulio.shared.address.models.ConfidenceLevel
import app.haulio.shared.address.models.GeocodingSource
import app.haulio.shared.address.parsing.AddressNormalizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates geocoding by coordinating between Pelias (primary) and Geocodio (fallback).
 *
 * Geocoding flow:
 * 1. Normalize the input address
 * 2. Check the local cache (SQLDelight, 30-day TTL)
 * 3. Query Pelias at geo.haulio.app
 * 4. If Pelias confidence >= 0.7 → cache and return with VERIFIED badge
 * 5. If Pelias confidence < 0.7 or no result → query Geocodio as fallback
 * 6. If Geocodio returns a result → cache and return
 * 7. If both fail → return NOT_FOUND
 *
 * @param peliasClient The Pelias geocoding client.
 * @param geocodioClient The Geocodio geocoding client (fallback).
 * @param cache The address cache for storing/retrieving geocoded results.
 */
class GeocodingManager(
    private val peliasClient: PeliasClient,
    private val geocodioClient: GeocodioClient,
    private val cache: AddressCache,
) {

    private val mutex = Mutex()

    /** Minimum confidence threshold to accept a Pelias result without fallback. */
    private val peliasConfidenceThreshold = 0.7

    /**
     * Geocodes an address, using cache → Pelias → Geocodio fallback strategy.
     *
     * @param rawAddress The raw user-entered address text.
     * @return The best [AddressSuggestion] available, or a NOT_FOUND result.
     */
    suspend fun geocode(rawAddress: String): AddressSuggestion = mutex.withLock {
        val normResult = AddressNormalizer.normalize(rawAddress)
        val normalizedAddress = normResult.normalizedText
        val cacheKey = AddressNormalizer.toCacheKey(normalizedAddress)

        // Step 1: Check cache
        val cached = cache.get(cacheKey)
        if (cached != null) {
            return@withLock cached.copy(source = GeocodingSource.CACHE)
        }

        // Step 2: Try Pelias
        val peliasResults = try {
            peliasClient.search(normalizedAddress)
        } catch (_: Exception) {
            emptyList()
        }

        val bestPelias = peliasResults.firstOrNull()
        if (bestPelias != null && bestPelias.confidence >= peliasConfidenceThreshold) {
            cache.put(cacheKey, bestPelias)
            return@withLock bestPelias
        }

        // Step 3: Fallback to Geocodio
        val geocodioResults = try {
            geocodioClient.geocode(normalizedAddress)
        } catch (_: Exception) {
            emptyList()
        }

        val bestGeocodio = geocodioResults.firstOrNull()
        if (bestGeocodio != null) {
            cache.put(cacheKey, bestGeocodio)
            return@withLock bestGeocodio
        }

        // Step 4: If Pelias had a low-confidence result, return it as APPROXIMATE
        if (bestPelias != null) {
            val approximate = bestPelias.copy(badge = ConfidenceLevel.APPROXIMATE)
            cache.put(cacheKey, approximate)
            return@withLock approximate
        }

        // Step 5: Both failed
        return@withLock AddressSuggestion(
            formattedAddress = normalizedAddress,
            latitude = 0.0,
            longitude = 0.0,
            confidence = 0.0,
            badge = ConfidenceLevel.NOT_FOUND,
            source = GeocodingSource.PELIAS,
        )
    }

    /**
     * Geocodes an address and returns multiple suggestions for user selection.
     *
     * @param rawAddress The raw user-entered address text.
     * @param maxResults Maximum number of suggestions to return.
     * @return List of [AddressSuggestion] ranked by confidence.
     */
    suspend fun suggest(rawAddress: String, maxResults: Int = 5): List<AddressSuggestion> =
        mutex.withLock {
            val normResult = AddressNormalizer.normalize(rawAddress)
            val normalizedAddress = normResult.normalizedText

            val peliasResults = try {
                peliasClient.search(normalizedAddress, size = maxResults)
            } catch (_: Exception) {
                emptyList()
            }

            if (peliasResults.isNotEmpty() &&
                peliasResults.first().confidence >= peliasConfidenceThreshold
            ) {
                return@withLock peliasResults.take(maxResults)
            }

            // Supplement with Geocodio results
            val geocodioResults = try {
                geocodioClient.geocode(normalizedAddress)
            } catch (_: Exception) {
                emptyList()
            }

            return@withLock (peliasResults + geocodioResults)
                .distinctBy { it.formattedAddress }
                .sortedByDescending { it.confidence }
                .take(maxResults)
        }
}

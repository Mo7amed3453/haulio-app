package app.haulio.shared.traffic.extreme

import app.haulio.shared.address.cache.currentTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Priority levels for TomTom API call requests.
 *
 * Ordinal order determines priority (higher ordinal = higher priority):
 * CORRIDOR (0) < SHIFT (1) < SCHOOL (2) < TRAIN (3).
 *
 * When the daily budget is in the buffer zone (remaining < [TomTomBudgetManager.BUFFER_CALLS]),
 * only SCHOOL and TRAIN requests are permitted.
 */
enum class TomTomCallPriority {
    CORRIDOR,   // lowest — historical corridors
    SHIFT,      // industrial shift-change zones
    SCHOOL,     // school zones
    TRAIN,      // highest — rail crossings
}

/**
 * Tracks and enforces the daily TomTom Traffic API call budget.
 *
 * ### Budget rules
 * - Maximum daily calls: [MAX_DAILY_CALLS] (2 000).
 * - Buffer: [BUFFER_CALLS] (500). When `remaining < BUFFER_CALLS`, only priorities
 *   ≥ [TomTomCallPriority.SCHOOL] are allowed.
 * - Counter resets automatically at UTC midnight.
 *
 * The in-memory counter is the authoritative source during a single app session.
 * For cross-session persistence, pair this class with the `tomtom_budget` SQLDelight
 * table (see `TomTomBudget.sq`).
 *
 * @param currentTimeProvider Injectable wall-clock (epoch ms) for testing.
 */
class TomTomBudgetManager(
    private val currentTimeProvider: () -> Long = { currentTimeMillis() },
) {
    private val mutex = Mutex()
    private var dailyCount: Int = 0
    private var lastResetDayMs: Long = startOfDayMs(currentTimeProvider())

    /**
     * Returns `true` if a call at [priority] may be made without exceeding the daily budget.
     */
    suspend fun canCall(priority: TomTomCallPriority): Boolean = mutex.withLock {
        maybeResetDay()
        val remaining = MAX_DAILY_CALLS - dailyCount
        when {
            remaining <= 0 -> false
            remaining > BUFFER_CALLS -> true
            else -> priority >= TomTomCallPriority.SCHOOL
        }
    }

    /**
     * Records one successful TomTom API call. Must be called after [canCall] returns `true`
     * and the HTTP request is dispatched.
     */
    suspend fun recordCall(): Unit = mutex.withLock {
        maybeResetDay()
        dailyCount += 1
    }

    /**
     * Returns the number of calls made today (diagnostic / testing helper).
     */
    suspend fun currentCount(): Int = mutex.withLock {
        maybeResetDay()
        dailyCount
    }

    /**
     * Returns the number of calls remaining for today.
     */
    suspend fun remaining(): Int = mutex.withLock {
        maybeResetDay()
        (MAX_DAILY_CALLS - dailyCount).coerceAtLeast(0)
    }

    private fun maybeResetDay() {
        val todayStartMs = startOfDayMs(currentTimeProvider())
        if (todayStartMs > lastResetDayMs) {
            dailyCount = 0
            lastResetDayMs = todayStartMs
        }
    }

    companion object {
        /** Maximum allowed TomTom API calls per UTC day. */
        const val MAX_DAILY_CALLS: Int = 2_000

        /**
         * Buffer reserved for high-priority callers (SCHOOL and TRAIN).
         * Low-priority callers are blocked when `remaining < BUFFER_CALLS`.
         */
        const val BUFFER_CALLS: Int = 500

        /**
         * Returns the epoch-millisecond timestamp of the start of the UTC day
         * that contains [nowMs].
         */
        fun startOfDayMs(nowMs: Long): Long {
            val dayMs = 24L * 60L * 60L * 1_000L
            return (nowMs / dayMs) * dayMs
        }
    }
}

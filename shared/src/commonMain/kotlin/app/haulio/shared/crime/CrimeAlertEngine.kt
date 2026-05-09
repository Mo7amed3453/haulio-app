package app.haulio.shared.crime

import app.haulio.shared.crime.models.CrimeGridCell
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

/**
 * Emitted when the driver enters a high-risk crime grid cell (riskScore > 7).
 *
 * @param cellId    Cell that triggered the alert.
 * @param riskScore The risk score of the triggering cell.
 * @param topCrimeType Most frequent crime type in the cell, if known.
 */
data class HighRiskAlertEvent(
    val cellId: String,
    val riskScore: Double,
    val topCrimeType: String?,
)

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Risk score threshold above which a high-risk alert is emitted. */
const val HIGH_RISK_THRESHOLD = 7.0

/**
 * Grid cell resolution in degrees (~0.005 deg ≈ 0.5 km).
 * Used to snap a lat/lng to the nearest grid cell centre.
 */
const val GRID_RESOLUTION = 0.005

// ---------------------------------------------------------------------------
// CrimeAlertEngine
// ---------------------------------------------------------------------------

/**
 * Pure flow-based engine that monitors the driver's GPS position against
 * the crime grid and emits [HighRiskAlertEvent] when they enter a new
 * high-risk cell.
 *
 * Debounce rule: an alert is only emitted when the driver enters a
 * *new* high-risk cell (different [cellId]) after having previously
 * left a high-risk cell. Staying inside the same cell never re-triggers.
 *
 * All logic is pure and synchronous — suitable for unit testing.
 */
class CrimeAlertEngine {

    // ── Internal state ──────────────────────────────────────────────────────

    /** The cell ID of the last high-risk cell that produced an alert. */
    private var lastAlertedCellId: String? = null

    /** Whether the driver is currently inside any high-risk cell. */
    private var isInsideHighRiskCell: Boolean = false

    private val _alert = MutableStateFlow<HighRiskAlertEvent?>(null)

    /**
     * Hot flow of [HighRiskAlertEvent].
     * Downstream collectors see only non-null emissions; use [filterNotNull].
     */
    val alerts: Flow<HighRiskAlertEvent?> = _alert

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Called on every GPS update. Looks up the cell containing ([lat], [lng])
     * inside [cells] and emits a [HighRiskAlertEvent] if the driver has
     * entered a new high-risk cell.
     *
     * @param lat   Current latitude.
     * @param lng   Current longitude.
     * @param cells The latest set of grid cells (from CrimeGridClient or mock).
     */
    fun onLocationUpdate(lat: Double, lng: Double, cells: List<CrimeGridCell>) {
        val cell = findCellAt(lat, lng, cells)

        if (cell == null || cell.riskScore <= HIGH_RISK_THRESHOLD) {
            // Outside any high-risk cell — reset tracking
            isInsideHighRiskCell = false
            return
        }

        // Inside a high-risk cell — check if it's a new one
        if (!isInsideHighRiskCell || cell.cellId != lastAlertedCellId) {
            isInsideHighRiskCell = true
            lastAlertedCellId    = cell.cellId
            _alert.value = HighRiskAlertEvent(
                cellId       = cell.cellId,
                riskScore    = cell.riskScore,
                topCrimeType = cell.topCrimeType,
            )
        }
        // Same cell — do nothing (debounce)
    }

    /**
     * Resets internal state. Call when the driver starts a new session or
     * when the cell grid is refreshed with a completely new bbox.
     */
    fun reset() {
        lastAlertedCellId    = null
        isInsideHighRiskCell = false
        _alert.value         = null
    }

    // ── Pure helpers ────────────────────────────────────────────────────────

    /**
     * Snaps ([lat], [lng]) to the nearest 0.5 km grid cell and returns the
     * matching [CrimeGridCell], or null if no cell exists at that position.
     */
    fun findCellAt(lat: Double, lng: Double, cells: List<CrimeGridCell>): CrimeGridCell? {
        val snappedLat = snapToGrid(lat)
        val snappedLng = snapToGrid(lng)
        return cells.firstOrNull { cell ->
            snapToGrid(cell.lat) == snappedLat && snapToGrid(cell.lng) == snappedLng
        }
    }

    /**
     * Snaps a coordinate to the nearest grid cell centre.
     * e.g. 37.7749 → 37.775 (GRID_RESOLUTION = 0.005)
     */
    fun snapToGrid(coord: Double): Double =
        kotlin.math.round(coord / GRID_RESOLUTION) * GRID_RESOLUTION
}

package app.haulio.shared.fuel.models

import kotlinx.datetime.LocalDate

/**
 * Regional or crowd-reported fuel price for a single week.
 *
 * @property regularUsd   Price per gallon for regular (87 octane) gasoline in USD.
 * @property midGradeUsd  Price per gallon for mid-grade (89 octane), if available.
 * @property premiumUsd   Price per gallon for premium (91+ octane), if available.
 * @property dieselUsd    Price per gallon for diesel, if available.
 * @property weekOf       ISO week start date (Monday) this price is associated with.
 * @property source       Data source label, e.g. "EIA", "crowd", "osm".
 */
data class FuelPrice(
    val regularUsd: Double,
    val midGradeUsd: Double?,
    val premiumUsd: Double?,
    val dieselUsd: Double?,
    val weekOf: LocalDate,
    val source: String,
)

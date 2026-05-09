package app.haulio.shared.address.models

/**
 * Structured representation of a parsed US address.
 *
 * Fields are nullable when the parser cannot confidently extract a value.
 *
 * @property houseNumber Street number (e.g. "123").
 * @property street Street name, fully expanded (e.g. "North Main Street").
 * @property unit Unit/apartment designator (e.g. "Apartment 4B").
 * @property city City name.
 * @property state Two-letter state abbreviation.
 * @property zip5 Five-digit ZIP code.
 * @property zip4 Four-digit ZIP+4 extension.
 */
data class ParsedAddress(
    val houseNumber: String? = null,
    val street: String? = null,
    val unit: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip5: String? = null,
    val zip4: String? = null,
) {
    /**
     * Returns the full ZIP code with +4 if available (e.g. "50010-4516").
     */
    fun fullZip(): String? = when {
        zip5 != null && zip4 != null -> "$zip5-$zip4"
        zip5 != null -> zip5
        else -> null
    }

    /**
     * Returns a single-line formatted address string.
     */
    fun formatted(): String = buildString {
        houseNumber?.let { append("$it ") }
        street?.let { append("$it") }
        unit?.let { append(", $it") }
        city?.let { append(", $it") }
        state?.let { append(", $it") }
        fullZip()?.let { append(" $it") }
    }.trim()
}

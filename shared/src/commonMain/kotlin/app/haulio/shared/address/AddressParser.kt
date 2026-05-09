package app.haulio.shared.address

import app.haulio.shared.address.models.ParsedAddress
import app.haulio.shared.address.parsing.AbbreviationExpander
import app.haulio.shared.address.parsing.AddressNormalizer
import app.haulio.shared.address.parsing.ZipLookupTable

/**
 * Main address parser orchestrator for the Smart Address Auto-Correction system.
 *
 * Parses raw US address text into a structured [ParsedAddress] by:
 * 1. Normalizing input (trim, collapse whitespace, fix typos)
 * 2. Expanding abbreviations (St→Street, N→North, Apt→Apartment, etc.)
 * 3. Extracting ZIP+4 if present
 * 4. Looking up city/state from ZIP via bundled lookup table
 * 5. Parsing street number, street name, and unit information
 *
 * Usage:
 * ```kotlin
 * val parser = AddressParser()
 * val result = parser.parse("123 N Main St Apt 4B, Ames IA 50010-4516")
 * ```
 */
class AddressParser {

    // Regex patterns for address component extraction
    private val houseNumberRegex = Regex("""^(\d+[A-Za-z]?)\s+""")
    private val unitRegex = Regex(
        """(?:Apartment|Suite|Floor|Room|Building|Department|Unit)\s+(\S+)""",
        RegexOption.IGNORE_CASE
    )
    private val stateRegex = Regex(
        """\b([A-Z]{2})\b"""
    )
    private val zipInTextRegex = Regex("""(\d{5})[-\s]?(\d{4})?""")

    /**
     * Parses a raw address string into a structured [ParsedAddress].
     *
     * @param rawInput The user-entered address text (e.g. "123 N Main St Apt 4B, Ames IA 50010-4516").
     * @return [ParsedAddress] with all extractable fields populated.
     */
    fun parse(rawInput: String): ParsedAddress {
        if (rawInput.isBlank()) {
            return ParsedAddress()
        }

        // Step 1-2: Normalize and expand abbreviations
        val normResult = AddressNormalizer.normalize(rawInput)
        val text = normResult.normalizedText
        val zip5 = normResult.zip5
        val zip4 = normResult.zip4

        // Step 3: Extract house number
        val houseNumber = houseNumberRegex.find(text)?.groupValues?.get(1)

        // Step 4: Extract unit
        val unitMatch = unitRegex.find(text)
        val unit = if (unitMatch != null) {
            unitMatch.value
        } else {
            null
        }

        // Step 5: Look up city/state from ZIP
        var city: String? = null
        var state: String? = null
        if (zip5 != null) {
            val zipEntry = ZipLookupTable.lookup(zip5)
            if (zipEntry != null) {
                city = zipEntry.city
                state = zipEntry.state
            }
        }

        // Step 6: Try to extract state from text if not found via ZIP
        if (state == null) {
            val stateMatch = stateRegex.find(text)
            if (stateMatch != null) {
                val candidate = stateMatch.groupValues[1]
                if (isUsState(candidate)) {
                    state = candidate
                }
            }
        }

        // Step 7: Extract street name (between house number and first comma/unit/zip)
        val street = extractStreetName(text, houseNumber, unit)

        // Step 8: Extract city from text if not found via ZIP
        if (city == null) {
            city = extractCity(text, street, state, zip5)
        }

        return ParsedAddress(
            houseNumber = houseNumber,
            street = street,
            unit = unit,
            city = city,
            state = state,
            zip5 = zip5,
            zip4 = zip4,
        )
    }

    /**
     * Extracts the street name from the normalized address text.
     * The street is typically between the house number and the first comma, unit, or ZIP.
     */
    private fun extractStreetName(text: String, houseNumber: String?, unit: String?): String? {
        var working = text

        // Remove house number from the beginning
        if (houseNumber != null) {
            working = working.replaceFirst(Regex("""^\d+[A-Za-z]?\s+"""), "")
        }

        // Remove everything after the first comma (city/state/zip usually)
        val commaIdx = working.indexOf(',')
        if (commaIdx > 0) {
            working = working.substring(0, commaIdx)
        }

        // Remove unit designator from street portion
        if (unit != null) {
            working = working.replace(unit, "").trim()
        }

        // Remove ZIP from street portion
        working = zipInTextRegex.replace(working, "").trim()

        return working.ifBlank { null }
    }

    /**
     * Attempts to extract the city name from the address text.
     * Looks for text between the last comma and the state abbreviation.
     */
    private fun extractCity(text: String, street: String?, state: String?, zip5: String?): String? {
        val parts = text.split(",").map { it.trim() }
        if (parts.size < 2) return null

        // City is typically in the second segment (after street)
        val citySegment = parts.getOrNull(1) ?: return null

        // Remove state and ZIP from the segment
        var cityText = citySegment
        if (state != null) {
            cityText = cityText.replace(Regex("""\b$state\b"""), "").trim()
        }
        if (zip5 != null) {
            cityText = zipInTextRegex.replace(cityText, "").trim()
        }

        return cityText.ifBlank { null }
    }

    /**
     * Validates if a two-letter code is a US state abbreviation.
     */
    private fun isUsState(code: String): Boolean {
        return code.uppercase() in US_STATE_CODES
    }

    companion object {
        private val US_STATE_CODES = setOf(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC", "PR", "VI", "GU", "AS", "MP",
        )
    }
}

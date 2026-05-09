package app.haulio.shared.address.parsing

/**
 * Normalizes raw address input for consistent parsing and geocoding.
 *
 * Normalization steps:
 * 1. Trim leading/trailing whitespace
 * 2. Collapse multiple spaces into single spaces
 * 3. Handle common typos (double commas, missing spaces after commas)
 * 4. Expand abbreviations via [AbbreviationExpander]
 * 5. Extract ZIP+4 if present
 */
object AddressNormalizer {

    private val zipRegex = Regex("""(\d{5})[-\s]?(\d{4})?""")
    private val multiSpaceRegex = Regex("""\s{2,}""")
    private val commaNoSpaceRegex = Regex(""",(\S)""")
    private val doubleCommaRegex = Regex(""",\s*,""")

    /**
     * Result of normalization containing the cleaned text and extracted ZIP components.
     *
     * @property normalizedText The fully cleaned and expanded address text.
     * @property zip5 Extracted 5-digit ZIP code, if found.
     * @property zip4 Extracted 4-digit ZIP extension, if found.
     */
    data class NormalizationResult(
        val normalizedText: String,
        val zip5: String? = null,
        val zip4: String? = null,
    )

    /**
     * Normalizes the given raw address input.
     *
     * @param raw The raw user-entered address text.
     * @return [NormalizationResult] with cleaned text and extracted ZIP components.
     */
    fun normalize(raw: String): NormalizationResult {
        var text = raw.trim()

        // Fix common punctuation issues
        text = doubleCommaRegex.replace(text, ",")
        text = commaNoSpaceRegex.replace(text) { ", ${it.groupValues[1]}" }

        // Collapse multiple whitespace
        text = multiSpaceRegex.replace(text, " ")

        // Extract ZIP before expansion (to avoid expanding "St" in state abbreviations contextually)
        var zip5: String? = null
        var zip4: String? = null
        val zipMatch = zipRegex.find(text)
        if (zipMatch != null) {
            zip5 = zipMatch.groupValues[1]
            val zip4Value = zipMatch.groupValues[2]
            zip4 = zip4Value.ifEmpty { null }
        }

        // Expand abbreviations
        text = AbbreviationExpander.expand(text)

        return NormalizationResult(
            normalizedText = text,
            zip5 = zip5,
            zip4 = zip4,
        )
    }

    /**
     * Produces a cache-friendly key from an address string.
     * Lowercased, trimmed, whitespace-collapsed, punctuation stripped.
     *
     * @param address Address text to generate a key for.
     * @return Normalized cache key string.
     */
    fun toCacheKey(address: String): String {
        return address
            .lowercase()
            .replace(Regex("""[,.\-#]"""), " ")
            .replace(multiSpaceRegex, " ")
            .trim()
    }
}

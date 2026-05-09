package app.haulio.shared.address.parsing

/**
 * Expands common US address abbreviations to their full form.
 *
 * Handles directionals (N→North), street types (St→Street),
 * and unit types (Apt→Apartment). Matching is case-insensitive
 * and operates on word boundaries.
 */
object AbbreviationExpander {

    private val directionals = mapOf(
        "n" to "North",
        "s" to "South",
        "e" to "East",
        "w" to "West",
        "ne" to "Northeast",
        "nw" to "Northwest",
        "se" to "Southeast",
        "sw" to "Southwest",
    )

    private val streetTypes = mapOf(
        "st" to "Street",
        "ave" to "Avenue",
        "blvd" to "Boulevard",
        "dr" to "Drive",
        "ln" to "Lane",
        "rd" to "Road",
        "ct" to "Court",
        "pl" to "Place",
        "cir" to "Circle",
        "pkwy" to "Parkway",
        "hwy" to "Highway",
        "fwy" to "Freeway",
        "way" to "Way",
        "ter" to "Terrace",
        "trl" to "Trail",
    )

    private val unitTypes = mapOf(
        "apt" to "Apartment",
        "ste" to "Suite",
        "fl" to "Floor",
        "rm" to "Room",
        "bldg" to "Building",
        "dept" to "Department",
    )

    /**
     * All known abbreviation mappings combined.
     * Keys are lowercase, values are the expanded form.
     */
    val allMappings: Map<String, String> = directionals + streetTypes + unitTypes

    /**
     * Expands abbreviations in the given [input] text.
     *
     * Each word token is checked against the known mappings.
     * The `#` symbol is expanded to "Unit" when followed by an alphanumeric value.
     *
     * @param input Raw address text to expand.
     * @return Text with all recognized abbreviations expanded.
     */
    fun expand(input: String): String {
        // Handle # → Unit expansion first
        val hashExpanded = input.replace(Regex("""#\s*(\w+)""")) { match ->
            "Unit ${match.groupValues[1]}"
        }

        val words = hashExpanded.split(Regex("\\s+"))
        return words.joinToString(" ") { word ->
            expandWord(word)
        }
    }

    /**
     * Expands a single word if it matches a known abbreviation.
     * Preserves trailing punctuation (commas, periods).
     */
    private fun expandWord(word: String): String {
        // Separate trailing punctuation
        val punctuationMatch = Regex("""^(.*?)([,.]*)$""").find(word)
        val core = punctuationMatch?.groupValues?.get(1) ?: word
        val trailing = punctuationMatch?.groupValues?.get(2).orEmpty()

        val lookup = core.lowercase()
        val expanded = allMappings[lookup]
        return if (expanded != null) {
            "$expanded$trailing"
        } else {
            word
        }
    }

    /**
     * Checks if a word is a known directional abbreviation.
     */
    fun isDirectional(word: String): Boolean =
        directionals.containsKey(word.lowercase().trimEnd(',', '.'))

    /**
     * Checks if a word is a known street type abbreviation.
     */
    fun isStreetType(word: String): Boolean =
        streetTypes.containsKey(word.lowercase().trimEnd(',', '.'))

    /**
     * Checks if a word is a known unit type abbreviation.
     */
    fun isUnitType(word: String): Boolean =
        unitTypes.containsKey(word.lowercase().trimEnd(',', '.'))
}

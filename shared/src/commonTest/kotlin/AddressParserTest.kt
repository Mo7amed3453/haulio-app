package app.haulio.shared.address

import app.haulio.shared.address.parsing.ZipLookupTable
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AddressParserTest {

    private lateinit var parser: AddressParser

    @BeforeTest
    fun setup() {
        parser = AddressParser()
        // Load a minimal ZIP table for testing
        ZipLookupTable.clear()
        ZipLookupTable.loadFromJson(
            """
            {
                "50010": {"city": "Ames", "state": "IA"},
                "10001": {"city": "New York", "state": "NY"},
                "90001": {"city": "Los Angeles", "state": "CA"},
                "60601": {"city": "Chicago", "state": "IL"},
                "94102": {"city": "San Francisco", "state": "CA"}
            }
            """.trimIndent()
        )
    }

    @Test
    fun parseFullAddressWithZipPlus4() {
        val result = parser.parse("123 N Main St Apt 4B, Ames IA 50010-4516")

        assertEquals("123", result.houseNumber)
        assertNotNull(result.street)
        assert(result.street!!.contains("Main"))
        assertNotNull(result.unit)
        assert(result.unit!!.contains("4B"))
        assertEquals("Ames", result.city)
        assertEquals("IA", result.state)
        assertEquals("50010", result.zip5)
        assertEquals("4516", result.zip4)
    }

    @Test
    fun parseAddressWithZip5Only() {
        val result = parser.parse("456 Oak Ave, New York NY 10001")

        assertEquals("456", result.houseNumber)
        assertNotNull(result.street)
        assertEquals("New York", result.city)
        assertEquals("NY", result.state)
        assertEquals("10001", result.zip5)
        assertNull(result.zip4)
    }

    @Test
    fun parseAddressExpandsAbbreviations() {
        val result = parser.parse("789 W Elm Blvd, Los Angeles CA 90001")

        assertEquals("789", result.houseNumber)
        assertNotNull(result.street)
        // Abbreviations should be expanded
        assert(result.street!!.contains("West") || result.street!!.contains("Boulevard"))
        assertEquals("Los Angeles", result.city)
        assertEquals("CA", result.state)
        assertEquals("90001", result.zip5)
    }

    @Test
    fun parseBlankInputReturnsEmpty() {
        val result = parser.parse("")
        assertNull(result.houseNumber)
        assertNull(result.street)
        assertNull(result.city)
        assertNull(result.state)
        assertNull(result.zip5)
    }

    @Test
    fun parseWhitespaceOnlyReturnsEmpty() {
        val result = parser.parse("   ")
        assertNull(result.houseNumber)
    }

    @Test
    fun parseAddressWithHashUnit() {
        val result = parser.parse("100 Broadway #5, New York NY 10001")

        assertEquals("100", result.houseNumber)
        assertNotNull(result.unit)
        assert(result.unit!!.contains("5"))
        assertEquals("10001", result.zip5)
    }

    @Test
    fun parseAddressWithSuiteAbbreviation() {
        val result = parser.parse("200 Market St Ste 300, San Francisco CA 94102")

        assertEquals("200", result.houseNumber)
        assertNotNull(result.unit)
        assert(result.unit!!.contains("300"))
        assertEquals("94102", result.zip5)
    }

    @Test
    fun parsedAddressFormattedOutput() {
        val result = parser.parse("123 N Main St, Ames IA 50010-4516")
        val formatted = result.formatted()

        assert(formatted.isNotBlank())
        assert(formatted.contains("123"))
        assert(formatted.contains("Ames"))
    }

    @Test
    fun parsedAddressFullZipWithPlus4() {
        val result = parser.parse("123 Main St, Ames IA 50010-4516")
        assertEquals("50010-4516", result.fullZip())
    }

    @Test
    fun parsedAddressFullZipWithout4() {
        val result = parser.parse("123 Main St, Ames IA 50010")
        assertEquals("50010", result.fullZip())
    }

    @Test
    fun zipLookupPopulatesCityState() {
        val result = parser.parse("1 Main St 60601")
        assertEquals("Chicago", result.city)
        assertEquals("IL", result.state)
    }
}

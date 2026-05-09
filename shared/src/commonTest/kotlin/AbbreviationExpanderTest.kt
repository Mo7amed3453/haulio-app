package app.haulio.shared.address

import app.haulio.shared.address.parsing.AbbreviationExpander
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbbreviationExpanderTest {

    @Test
    fun expandDirectionals() {
        assertEquals("North Main Street", AbbreviationExpander.expand("N Main St"))
        assertEquals("South Broadway", AbbreviationExpander.expand("S Broadway"))
        assertEquals("East 42nd Street", AbbreviationExpander.expand("E 42nd St"))
        assertEquals("West Avenue", AbbreviationExpander.expand("W Ave"))
    }

    @Test
    fun expandCompoundDirectionals() {
        assertEquals("Northeast Park Drive", AbbreviationExpander.expand("NE Park Dr"))
        assertEquals("Northwest Highway", AbbreviationExpander.expand("NW Hwy"))
        assertEquals("Southeast Boulevard", AbbreviationExpander.expand("SE Blvd"))
        assertEquals("Southwest Trail", AbbreviationExpander.expand("SW Trl"))
    }

    @Test
    fun expandStreetTypes() {
        assertEquals("Oak Street", AbbreviationExpander.expand("Oak St"))
        assertEquals("Elm Avenue", AbbreviationExpander.expand("Elm Ave"))
        assertEquals("Pine Boulevard", AbbreviationExpander.expand("Pine Blvd"))
        assertEquals("Cedar Drive", AbbreviationExpander.expand("Cedar Dr"))
        assertEquals("Maple Lane", AbbreviationExpander.expand("Maple Ln"))
        assertEquals("Birch Road", AbbreviationExpander.expand("Birch Rd"))
        assertEquals("Walnut Court", AbbreviationExpander.expand("Walnut Ct"))
        assertEquals("Cherry Place", AbbreviationExpander.expand("Cherry Pl"))
        assertEquals("River Circle", AbbreviationExpander.expand("River Cir"))
        assertEquals("Lake Parkway", AbbreviationExpander.expand("Lake Pkwy"))
        assertEquals("County Highway", AbbreviationExpander.expand("County Hwy"))
        assertEquals("Interstate Freeway", AbbreviationExpander.expand("Interstate Fwy"))
        assertEquals("Park Way", AbbreviationExpander.expand("Park Way"))
        assertEquals("Hill Terrace", AbbreviationExpander.expand("Hill Ter"))
        assertEquals("Mountain Trail", AbbreviationExpander.expand("Mountain Trl"))
    }

    @Test
    fun expandUnitTypes() {
        assertEquals("Apartment 4B", AbbreviationExpander.expand("Apt 4B"))
        assertEquals("Suite 200", AbbreviationExpander.expand("Ste 200"))
        assertEquals("Floor 3", AbbreviationExpander.expand("Fl 3"))
        assertEquals("Room 101", AbbreviationExpander.expand("Rm 101"))
        assertEquals("Building A", AbbreviationExpander.expand("Bldg A"))
        assertEquals("Department 5", AbbreviationExpander.expand("Dept 5"))
    }

    @Test
    fun expandHashToUnit() {
        assertEquals("Unit 5", AbbreviationExpander.expand("#5"))
        assertEquals("Unit 12A", AbbreviationExpander.expand("# 12A"))
        assertEquals("100 Main Street Unit 3", AbbreviationExpander.expand("100 Main St #3"))
    }

    @Test
    fun preserveUnknownWords() {
        assertEquals("123 Broadway", AbbreviationExpander.expand("123 Broadway"))
        assertEquals("One World Trade Center", AbbreviationExpander.expand("One World Trade Center"))
    }

    @Test
    fun handleTrailingPunctuation() {
        assertEquals("North Main Street,", AbbreviationExpander.expand("N Main St,"))
        assertEquals("Avenue, Suite 100", AbbreviationExpander.expand("Ave, Ste 100"))
    }

    @Test
    fun caseInsensitiveMatching() {
        assertEquals("North Main Street", AbbreviationExpander.expand("n Main st"))
        assertEquals("South Boulevard", AbbreviationExpander.expand("s blvd"))
        assertEquals("Apartment 1", AbbreviationExpander.expand("APT 1"))
    }

    @Test
    fun isDirectionalChecks() {
        assertTrue(AbbreviationExpander.isDirectional("N"))
        assertTrue(AbbreviationExpander.isDirectional("s"))
        assertTrue(AbbreviationExpander.isDirectional("NE"))
        assertFalse(AbbreviationExpander.isDirectional("Main"))
        assertFalse(AbbreviationExpander.isDirectional("St"))
    }

    @Test
    fun isStreetTypeChecks() {
        assertTrue(AbbreviationExpander.isStreetType("St"))
        assertTrue(AbbreviationExpander.isStreetType("ave"))
        assertTrue(AbbreviationExpander.isStreetType("BLVD"))
        assertFalse(AbbreviationExpander.isStreetType("N"))
        assertFalse(AbbreviationExpander.isStreetType("Apt"))
    }

    @Test
    fun isUnitTypeChecks() {
        assertTrue(AbbreviationExpander.isUnitType("Apt"))
        assertTrue(AbbreviationExpander.isUnitType("ste"))
        assertTrue(AbbreviationExpander.isUnitType("FL"))
        assertFalse(AbbreviationExpander.isUnitType("St"))
        assertFalse(AbbreviationExpander.isUnitType("N"))
    }

    @Test
    fun fullAddressExpansion() {
        val input = "123 N Main St Apt 4B"
        val expected = "123 North Main Street Apartment 4B"
        assertEquals(expected, AbbreviationExpander.expand(input))
    }

    @Test
    fun multipleAbbreviationsInOneAddress() {
        val input = "456 SW Oak Blvd Ste 300"
        val expected = "456 Southwest Oak Boulevard Suite 300"
        assertEquals(expected, AbbreviationExpander.expand(input))
    }
}

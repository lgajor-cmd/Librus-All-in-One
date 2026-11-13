package pl.domreksio.librusallinone

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureDateParserTest {
    @Test
    fun parsesIsoDateOnly() {
        assertEquals(LocalDate.of(2026, 11, 13), FeatureDateParser.parse("2026-11-13"))
    }

    @Test
    fun parsesIsoTimestampWithZuluAndNoFractionalSeconds() {
        assertEquals(
            LocalDate.of(2026, 11, 13),
            FeatureDateParser.parse("2026-11-13T08:30:00Z")
        )
    }

    @Test
    fun parsesIsoTimestampWithOffsetAndFractionalSeconds() {
        assertEquals(
            LocalDate.of(2026, 11, 13),
            FeatureDateParser.parse("2026-11-13T23:45:12.123+01:00")
        )
    }

    @Test
    fun parsesLegacyPolishDateWithOptionalTime() {
        assertEquals(
            LocalDate.of(2026, 11, 13),
            FeatureDateParser.parse("13.11.2026 08:30")
        )
        assertEquals(
            LocalDate.of(2026, 11, 13),
            FeatureDateParser.parse("13-11-2026")
        )
    }

    @Test
    fun rejectsImpossibleDatesAndMalformedSuffixes() {
        assertNull(FeatureDateParser.parse("2026-02-30"))
        assertNull(FeatureDateParser.parse("13.11.2026 29:75"))
        assertNull(FeatureDateParser.parse("2026-11-13garbage"))
    }

    @Test
    fun acceptsLeapDayOnlyInLeapYears() {
        assertEquals(
            LocalDate.of(2024, 2, 29),
            FeatureDateParser.parse("2024-02-29")
        )
        assertNull(FeatureDateParser.parse("2025-02-29"))
    }
}

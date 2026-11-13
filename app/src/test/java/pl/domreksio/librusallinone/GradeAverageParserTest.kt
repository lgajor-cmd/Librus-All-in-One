package pl.domreksio.librusallinone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeAverageParserTest {
    @Test
    fun parsesWholeAndDecimalGrades() {
        assertEquals(5.0, GradeAverageParser.parse("5")!!, 0.001)
        assertEquals(4.5, GradeAverageParser.parse("4.5")!!, 0.001)
        assertEquals(4.5, GradeAverageParser.parse("4,5")!!, 0.001)
    }

    @Test
    fun parsesPlusAndMinusUsingHalfPointConvention() {
        assertEquals(4.5, GradeAverageParser.parse("4+")!!, 0.001)
        assertEquals(3.5, GradeAverageParser.parse("4-")!!, 0.001)
    }

    @Test
    fun rejectsValuesOutsideTheGradeScaleAndNonNumericMarks() {
        assertNull(GradeAverageParser.parse("1-"))
        assertNull(GradeAverageParser.parse("6+"))
        assertNull(GradeAverageParser.parse("np"))
        assertNull(GradeAverageParser.parse(""))
    }

    @Test
    fun formatsWholeAndHalfValuesForPolishDisplay() {
        assertEquals("5", GradeAverageParser.format(5.0))
        assertEquals("4,5", GradeAverageParser.format(4.5))
    }
}

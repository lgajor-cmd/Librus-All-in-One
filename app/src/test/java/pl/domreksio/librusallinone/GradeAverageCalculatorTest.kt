package pl.domreksio.librusallinone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeAverageCalculatorTest {
    @Test
    fun calculatesWholeAndDecimalGradesSeparatedBySemicolonOrWhitespace() {
        val result = GradeAverageCalculator.calculate("5; 4,5 3")

        assertEquals(listOf(5.0, 4.5, 3.0), result?.grades)
        assertEquals(4.166666, result!!.average, 0.00001)
    }

    @Test
    fun usesTheSamePlusMinusConventionAsGradeParser() {
        val result = GradeAverageCalculator.calculate("4+; 4-; 5")

        assertEquals(listOf(4.5, 3.5, 5.0), result?.grades)
        assertEquals(4.333333, result!!.average, 0.00001)
    }

    @Test
    fun rejectsTheWholeInputWhenAnyTokenIsInvalid() {
        assertNull(GradeAverageCalculator.calculate("5; nieobecność; 4"))
        assertNull(GradeAverageCalculator.calculate("5; 6+"))
        assertNull(GradeAverageCalculator.calculate("5; 0"))
        assertNull(GradeAverageCalculator.calculate("5; 6.1"))
    }

    @Test
    fun rejectsEmptyInputAndAcceptsBoundaryGrades() {
        assertNull(GradeAverageCalculator.calculate(""))
        assertNull(GradeAverageCalculator.calculate(" ;   "))
        assertEquals(listOf(1.0, 6.0), GradeAverageCalculator.calculate("1; 6")?.grades)
    }

    @Test
    fun doesNotSilentlyIgnoreInvalidTokensBetweenValidGrades() {
        assertNull(GradeAverageCalculator.calculate("5,,;4"))
    }
}

package pl.domreksio.librusallinone

/**
 * Validates a complete list of marks before calculating an unweighted average.
 * A single invalid token rejects the whole input instead of silently omitting it.
 */
internal data class GradeAverageCalculation(val grades: List<Double>) {
    val average: Double
        get() = grades.average()
}

internal object GradeAverageCalculator {
    fun calculate(raw: String): GradeAverageCalculation? {
        val tokens = raw.trim().split(Regex("[;\\s]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val grades = tokens.map { token ->
            GradeAverageParser.parse(token) ?: return null
        }
        return GradeAverageCalculation(grades)
    }
}

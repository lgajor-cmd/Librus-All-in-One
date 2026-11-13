package pl.domreksio.librusallinone

/**
 * Converts numeric Librus marks into unweighted average values.
 * Plus/minus marks use the common half-point convention; schools may
 * configure their official averages differently.
 */
internal object GradeAverageParser {
    fun parse(raw: String): Double? {
        val value = raw.trim().replace(',', '.')
        if (value.isBlank()) return null

        val marked = Regex("^([1-6])([+-])?$").matchEntire(value)
        if (marked != null) {
            val base = marked.groupValues[1].toDouble()
            val modifier = when (marked.groupValues[2]) {
                "+" -> 0.5
                "-" -> -0.5
                else -> 0.0
            }
            return (base + modifier).takeIf { it in 1.0..6.0 }
        }

        val decimal = value.toDoubleOrNull() ?: return null
        return decimal.takeIf { it in 1.0..6.0 }
    }

    fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(java.util.Locale("pl", "PL"), "%.1f", value)
}

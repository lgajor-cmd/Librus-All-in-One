package pl.domreksio.librusallinone

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * Strictly parses the calendar date from Librus values.
 * Supports ISO dates/timestamps and legacy Polish date strings.
 */
internal object FeatureDateParser {
    private val legacyDot = DateTimeFormatter.ofPattern("dd.MM.uuuu")
        .withResolverStyle(ResolverStyle.STRICT)
    private val legacyDash = DateTimeFormatter.ofPattern("dd-MM-uuuu")
        .withResolverStyle(ResolverStyle.STRICT)
    private val legacyPattern =
        Regex("^(\\d{2}[.-]\\d{2}[.-]\\d{4})(?:\\s+(\\d{2}:\\d{2}(?::\\d{2})?))?$")

    fun parse(raw: String): LocalDate? {
        val value = raw.trim()
        if (value.isBlank()) return null

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            // Continue with ISO timestamps and legacy formats.
        }

        try {
            return LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(value))
        } catch (_: DateTimeParseException) {
            // Continue with legacy formats.
        }

        val match = legacyPattern.matchEntire(value) ?: return null
        val dateText = match.groupValues[1]
        val timeText = match.groupValues.getOrNull(2).orEmpty()
        return try {
            if (timeText.isNotBlank()) LocalTime.parse(timeText, DateTimeFormatter.ISO_LOCAL_TIME)
            if (dateText.contains('.')) LocalDate.parse(dateText, legacyDot)
            else LocalDate.parse(dateText, legacyDash)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

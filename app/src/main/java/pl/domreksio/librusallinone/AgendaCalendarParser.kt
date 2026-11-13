package pl.domreksio.librusallinone

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal data class SchoolAgendaEvent(
    val sourceKey: String,
    val item: JSONObject,
    val date: String
)

/**
 * Normalizes Librus agenda payloads for the monthly calendar.
 *
 * Librus integrations have returned both "agenda" and "events" over time,
 * sometimes as arrays and sometimes wrapped in an object containing items.
 */
internal object AgendaCalendarParser {
    private val dateFields = arrayOf(
        "date", "event_date", "start_date", "date_from",
        "due_date", "deadline", "start"
    )
    private val titleFields = arrayOf("title", "topic", "name", "subject")
    private val detailFields = arrayOf("description", "content", "details")

    fun parse(data: JSONObject): List<SchoolAgendaEvent> {
        val parsed = listOf("agenda", "events").flatMap { sourceKey ->
            val items = readItems(data.opt(sourceKey))
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val date = normalizeDate(firstNonBlank(item, dateFields))
                    ?: return@mapNotNull null
                SchoolAgendaEvent(sourceKey, item, date)
            }
        }

        val seen = linkedSetOf<String>()
        return parsed.filter { event ->
            val id = firstNonBlank(event.item, arrayOf("id", "event_id", "agenda_id"))
            val identity = if (id.isNotBlank()) {
                "id:$id"
            } else {
                listOf(
                    event.date,
                    firstNonBlank(event.item, titleFields),
                    firstNonBlank(event.item, detailFields)
                ).joinToString("|")
            }
            seen.add(identity)
        }
    }

    private fun readItems(raw: Any?): JSONArray = when (raw) {
        is JSONArray -> raw
        is JSONObject -> raw.optJSONArray("items")
            ?: raw.optJSONArray("entries")
            ?: JSONArray().apply { put(raw) }
        else -> JSONArray()
    }

    private fun firstNonBlank(item: JSONObject, fields: Array<String>): String {
        for (field in fields) {
            val value = item.opt(field)
            if (value == null || value == JSONObject.NULL) continue
            val text = value.toString().trim()
            if (text.isNotBlank() && text != "null") return text
        }
        return ""
    }

    private fun normalizeDate(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null

        // ISO timestamps and date-only values share the same calendar date.
        val isoPrefix = Regex("^(\\d{4}-\\d{2}-\\d{2})").find(value)?.groupValues?.get(1)
        if (isoPrefix != null) {
            return try {
                LocalDate.parse(isoPrefix, DateTimeFormatter.ISO_LOCAL_DATE).toString()
            } catch (_: DateTimeParseException) {
                null
            }
        }

        val formats = listOf(
            DateTimeFormatter.ofPattern("dd.MM.uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu")
        )
        for (format in formats) {
            try {
                return LocalDate.parse(value, format).toString()
            } catch (_: DateTimeParseException) {
                // Try the next explicitly supported Librus date format.
            }
        }
        return null
    }
}

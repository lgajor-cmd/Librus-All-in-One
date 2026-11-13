package pl.domreksio.librusallinone

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

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
                // IDs may be scoped to a feed; include date and title to avoid
                // merging unrelated records that happen to reuse the same ID.
                listOf("id:$id", event.date, firstNonBlank(event.item, titleFields))
                    .joinToString("|")
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

        val legacyDate = Regex("^(\\d{2}\\.\\d{2}\\.\\d{4})").find(value)
            ?.groupValues?.get(1)
            ?: Regex("^(\\d{2}-\\d{2}-\\d{4})").find(value)?.groupValues?.get(1)
            ?: return null
        val format = if (legacyDate.contains(".")) {
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
        } else {
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)
        }
        return try {
            LocalDate.parse(legacyDate, format).toString()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

package pl.domreksio.librusallinone

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaCalendarParserTest {
    @Test
    fun mergesAgendaAndEventsArrays() {
        val data = JSONObject()
            .put("agenda", JSONArray().put(
                JSONObject().put("id", "a1").put("date", "2026-11-13").put("title", "Wycieczka")
            ))
            .put("events", JSONObject().put("items", JSONArray().put(
                JSONObject().put("id", "e1").put("date", "14.11.2026").put("title", "Apel")
            )))

        val result = AgendaCalendarParser.parse(data)

        assertEquals(2, result.size)
        assertEquals(listOf("2026-11-13", "2026-11-14"), result.map { it.date })
        assertEquals(listOf("agenda", "events"), result.map { it.sourceKey })
    }

    @Test
    fun deduplicatesSameSchoolEventAcrossSourcesById() {
        val event = JSONObject().put("id", "same-id")
            .put("date", "2026-11-13").put("title", "Wywiadówka")
        val data = JSONObject()
            .put("agenda", JSONArray().put(event))
            .put("events", JSONArray().put(JSONObject(event.toString())))

        val result = AgendaCalendarParser.parse(data)

        assertEquals(1, result.size)
    }

    @Test
    fun ignoresEventsWithoutValidDates() {
        val data = JSONObject().put("agenda", JSONArray()
            .put(JSONObject().put("title", "Bez daty"))
            .put(JSONObject().put("date", "2026-99-99").put("title", "Błędna data")))

        val result = AgendaCalendarParser.parse(data)

        assertTrue(result.isEmpty())
    }

    @Test
    fun readsSingleEventObjectAndIsoTimestamp() {
        val data = JSONObject().put(
            "events",
            JSONObject().put("date", "2026-11-13T08:30:00Z").put("title", "Konkurs")
        )

        val result = AgendaCalendarParser.parse(data)

        assertEquals(1, result.size)
        assertEquals("2026-11-13", result.single().date)
    }
}

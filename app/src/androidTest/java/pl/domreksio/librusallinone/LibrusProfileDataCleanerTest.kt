package pl.domreksio.librusallinone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrusProfileDataCleanerTest {
    private lateinit var context: Context
    private val removedId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val retainedId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(
            "librus_notification_state",
            "librus_notification_diagnostics_$removedId",
            "librus_notification_diagnostics_$retainedId",
            "librus_notification_settings_$removedId",
            "librus_notification_settings_$retainedId",
            "librus_school_profile_$removedId",
            "librus_school_profile_$retainedId"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun removingProfileClearsOnlyItsLocalState() {
        context.getSharedPreferences("librus_notification_state", Context.MODE_PRIVATE)
            .edit()
            .putString("${removedId}_known_grades", "old-grade")
            .putString("${retainedId}_known_grades", "keep-grade")
            .commit()
        context.getSharedPreferences("librus_notification_diagnostics_$removedId", Context.MODE_PRIVATE)
            .edit().putString("last_status", "error").commit()
        context.getSharedPreferences("librus_notification_diagnostics_$retainedId", Context.MODE_PRIVATE)
            .edit().putString("last_status", "success").commit()
        context.getSharedPreferences("librus_notification_settings_$removedId", Context.MODE_PRIVATE)
            .edit().putBoolean("grades", false).commit()
        context.getSharedPreferences("librus_school_profile_$removedId", Context.MODE_PRIVATE)
            .edit().putString("school", "Usuwana szkoła").commit()

        LibrusProfileDataCleaner.clear(context, removedId)

        val state = context.getSharedPreferences("librus_notification_state", Context.MODE_PRIVATE)
        assertNull(state.getString("${removedId}_known_grades", null))
        assertEquals("keep-grade", state.getString("${retainedId}_known_grades", null))
        assertTrueEmpty("librus_notification_diagnostics_$removedId")
        assertEquals(
            "success",
            context.getSharedPreferences("librus_notification_diagnostics_$retainedId", Context.MODE_PRIVATE)
                .getString("last_status", null)
        )
        assertTrueEmpty("librus_notification_settings_$removedId")
        assertTrueEmpty("librus_school_profile_$removedId")
    }

    private fun assertTrueEmpty(name: String) {
        assertEquals(
            emptyMap<String, Any>(),
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all
        )
    }
}

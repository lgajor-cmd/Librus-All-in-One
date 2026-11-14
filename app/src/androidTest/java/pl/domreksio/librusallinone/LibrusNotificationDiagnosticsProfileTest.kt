package pl.domreksio.librusallinone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrusNotificationDiagnosticsProfileTest {
    private lateinit var context: Context

    private val studentProfileId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearDiagnostics()
    }

    @After
    fun tearDown() {
        clearDiagnostics()
    }

    @Test
    fun diagnosticsForOneProfileDoNotOverwriteAnotherProfile() {
        LibrusNotificationDiagnostics.record(
            context,
            "success",
            "diagnostyka-rodzica",
            LibrusProfileStore.LEGACY_PROFILE_ID
        )
        LibrusNotificationDiagnostics.record(
            context,
            "error",
            "diagnostyka-ucznia",
            studentProfileId
        )

        val parentSummary = LibrusNotificationDiagnostics.summary(
            context,
            LibrusProfileStore.LEGACY_PROFILE_ID
        )
        val studentSummary = LibrusNotificationDiagnostics.summary(context, studentProfileId)

        assertTrue(parentSummary.contains("diagnostyka-rodzica"))
        assertFalse(parentSummary.contains("diagnostyka-ucznia"))
        assertTrue(studentSummary.contains("diagnostyka-ucznia"))
        assertFalse(studentSummary.contains("diagnostyka-rodzica"))
    }

    @Test
    fun blockedNotificationStatusIsIsolatedBetweenProfiles() {
        LibrusNotificationDiagnostics.record(
            context,
            "blocked",
            "blokada ucznia",
            studentProfileId
        )

        assertTrue(
            LibrusNotificationDiagnostics.lastStatus(context, studentProfileId) == "blocked"
        )
        assertFalse(
            LibrusNotificationDiagnostics.lastStatus(
                context,
                LibrusProfileStore.LEGACY_PROFILE_ID
            ) == "blocked"
        )
    }

    @Test
    fun notificationTogglesAreIndependentBetweenProfiles() {
        LibrusNotificationSettings.preferences(context, LibrusProfileStore.LEGACY_PROFILE_ID)
            .edit().putBoolean("grades", false).commit()

        val parentEnabled = LibrusNotificationSettings.preferences(
            context,
            LibrusProfileStore.LEGACY_PROFILE_ID
        ).getBoolean("grades", true)
        val studentEnabled = LibrusNotificationSettings.preferences(
            context,
            studentProfileId
        ).getBoolean("grades", true)

        assertFalse(parentEnabled)
        assertTrue(studentEnabled)
    }

    @Test
    fun partialSyncIsCountedAsWarningNotAsHardError() {
        LibrusNotificationDiagnostics.record(
            context,
            "partial_success",
            "Jedna sekcja Librusa była chwilowo niedostępna.",
            LibrusProfileStore.LEGACY_PROFILE_ID
        )

        val prefs = context.getSharedPreferences(
            "librus_notification_diagnostics",
            Context.MODE_PRIVATE
        )
        assertTrue(prefs.getLong("warning_count", 0L) == 1L)
        assertTrue(prefs.getLong("error_count", 0L) == 0L)
        assertTrue(prefs.getLong("success_count", 0L) == 0L)
    }

    @Test
    fun localNotificationTestDoesNotOverwriteSyncStatusOrCounters() {
        LibrusNotificationDiagnostics.record(
            context,
            "success",
            "Synchronizacja zakończona poprawnie.",
            LibrusProfileStore.LEGACY_PROFILE_ID
        )
        LibrusNotificationDiagnostics.recordLocalTest(
            context,
            "blocked",
            "Testowe powiadomienie zablokowane przez Androida.",
            LibrusProfileStore.LEGACY_PROFILE_ID
        )

        val prefs = context.getSharedPreferences(
            "librus_notification_diagnostics",
            Context.MODE_PRIVATE
        )
        assertTrue(LibrusNotificationDiagnostics.lastStatus(context) == "success")
        assertTrue(prefs.getLong("success_count", 0L) == 1L)
        assertTrue(prefs.getLong("error_count", 0L) == 0L)
        assertTrue(prefs.getString("last_test_status", "") == "blocked")
        assertTrue(
            LibrusNotificationDiagnostics.summary(context).contains("Test lokalny: blocked")
        )
    }

    private fun clearDiagnostics() {
        context.getSharedPreferences("librus_notification_diagnostics", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(
            "librus_notification_diagnostics_$studentProfileId",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        LibrusNotificationSettings.preferences(context, LibrusProfileStore.LEGACY_PROFILE_ID)
            .edit().clear().commit()
        LibrusNotificationSettings.preferences(context, studentProfileId)
            .edit().clear().commit()
    }
}

package pl.domreksio.librusallinone

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationStateTrackerTest {
    @Test
    fun firstPartialSyncRecordsFeedsThatNeedBaselineSeeding() {
        val result = NotificationStateTracker.calculate(
            sourceKeys = listOf("grades", "descriptive_grades"),
            availableKeys = setOf("grades"),
            currentIds = setOf("grades:1"),
            previousIds = null,
            pendingSeedKeys = emptySet(),
            notificationKey = "grades",
            legacyPrefix = "grades",
            legacyState = false
        )

        assertEquals(setOf("grades:1"), result.currentIds)
        assertEquals(emptySet<String>(), result.newIds)
        assertEquals(setOf("descriptive_grades"), result.pendingSeedKeys)
    }

    @Test
    fun recoveredFeedIsSeededWithoutFalseHistoricalNotifications() {
        val initial = NotificationStateTracker.calculate(
            sourceKeys = listOf("grades", "descriptive_grades"),
            availableKeys = setOf("grades"),
            currentIds = setOf("grades:1"),
            previousIds = null,
            pendingSeedKeys = emptySet(),
            notificationKey = "grades",
            legacyPrefix = "grades",
            legacyState = false
        )
        val recovered = NotificationStateTracker.calculate(
            sourceKeys = listOf("grades", "descriptive_grades"),
            availableKeys = setOf("grades", "descriptive_grades"),
            currentIds = setOf("grades:1", "descriptive_grades:old"),
            previousIds = initial.currentIds,
            pendingSeedKeys = initial.pendingSeedKeys,
            notificationKey = "grades",
            legacyPrefix = "grades",
            legacyState = false
        )

        assertEquals(emptySet<String>(), recovered.newIds)
        assertEquals(emptySet<String>(), recovered.pendingSeedKeys)
        assertEquals(setOf("grades:1", "descriptive_grades:old"), recovered.currentIds)
    }

    @Test
    fun laterNewItemFromRecoveredFeedCreatesNotificationCandidate() {
        val previous = setOf("grades:1", "descriptive_grades:old")
        val result = NotificationStateTracker.calculate(
            sourceKeys = listOf("grades", "descriptive_grades"),
            availableKeys = setOf("grades", "descriptive_grades"),
            currentIds = previous + "descriptive_grades:new",
            previousIds = previous,
            pendingSeedKeys = emptySet(),
            notificationKey = "grades",
            legacyPrefix = "grades",
            legacyState = false
        )

        assertEquals(setOf("descriptive_grades:new"), result.newIds)
    }

    @Test
    fun stillUnavailableFeedKeepsItsPreviousIdsAndPendingSeedMarker() {
        val result = NotificationStateTracker.calculate(
            sourceKeys = listOf("grades", "descriptive_grades"),
            availableKeys = setOf("grades"),
            currentIds = setOf("grades:1"),
            previousIds = setOf("grades:1", "descriptive_grades:old"),
            pendingSeedKeys = setOf("descriptive_grades"),
            notificationKey = "grades",
            legacyPrefix = "grades",
            legacyState = false
        )

        assertEquals(setOf("grades:1", "descriptive_grades:old"), result.currentIds)
        assertEquals(setOf("descriptive_grades"), result.pendingSeedKeys)
        assertEquals(emptySet<String>(), result.newIds)
    }

    @Test
    fun legacyMigrationStillOnlyReportsNewOrdinaryGrades() {
        val result = NotificationStateTracker.calculate(
            sourceKeys = listOf("grades", "partial_grades"),
            availableKeys = setOf("grades", "partial_grades"),
            currentIds = setOf("grades:1", "grades:2", "partial_grades:old"),
            previousIds = setOf("grades:1"),
            pendingSeedKeys = emptySet(),
            notificationKey = "grades",
            legacyPrefix = "grades",
            legacyState = true
        )

        assertEquals(setOf("grades:2"), result.newIds)
    }
}

package pl.domreksio.librusallinone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStateTrackerTest {
    private val sources = setOf("grades", "descriptive_grades", "partial_grades")

    @Test
    fun partialFirstBaselineDefersUnavailableFeeds() {
        val update = NotificationStateTracker.update(
            previousKnownIds = null,
            currentIdsBySource = mapOf("grades" to setOf("grades:g1")),
            allSourceKeys = sources,
            pendingBaselineKeys = emptySet()
        )

        assertEquals(setOf("grades:g1"), update.knownIds)
        assertTrue(update.newIds.isEmpty())
        assertEquals(setOf("descriptive_grades", "partial_grades"), update.pendingBaselineKeys)
    }

    @Test
    fun firstRecoveryOfPreviouslyUnavailableFeedIsSeededWithoutAlert() {
        val first = NotificationStateTracker.update(
            previousKnownIds = null,
            currentIdsBySource = mapOf("grades" to setOf("grades:g1")),
            allSourceKeys = sources,
            pendingBaselineKeys = emptySet()
        )

        val recovered = NotificationStateTracker.update(
            previousKnownIds = first.knownIds,
            currentIdsBySource = mapOf(
                "grades" to setOf("grades:g1"),
                "descriptive_grades" to setOf("descriptive_grades:d-old")
            ),
            allSourceKeys = sources,
            pendingBaselineKeys = first.pendingBaselineKeys
        )

        assertTrue(recovered.newIds.isEmpty())
        assertEquals(
            setOf("grades:g1", "descriptive_grades:d-old"),
            recovered.knownIds
        )
        assertEquals(setOf("partial_grades"), recovered.pendingBaselineKeys)
    }

    @Test
    fun legacyMigrationCanDeferNewFeedsUntilTheirFirstSuccessfulRead() {
        val migrated = NotificationStateTracker.update(
            previousKnownIds = setOf("grades:g1"),
            currentIdsBySource = mapOf("grades" to setOf("grades:g1")),
            allSourceKeys = sources,
            pendingBaselineKeys = setOf("descriptive_grades", "partial_grades")
        )

        assertTrue(migrated.newIds.isEmpty())
        assertEquals(setOf("descriptive_grades", "partial_grades"), migrated.pendingBaselineKeys)

        val recovered = NotificationStateTracker.update(
            previousKnownIds = migrated.knownIds,
            currentIdsBySource = mapOf(
                "grades" to setOf("grades:g1"),
                "descriptive_grades" to setOf("descriptive_grades:d-old"),
                "partial_grades" to emptySet()
            ),
            allSourceKeys = sources,
            pendingBaselineKeys = migrated.pendingBaselineKeys
        )

        assertTrue(recovered.newIds.isEmpty())
        assertTrue(recovered.pendingBaselineKeys.isEmpty())
    }

    @Test
    fun newGradesInAvailableFeedAreStillDetectedDuringPartialBaseline() {
        val first = NotificationStateTracker.update(
            previousKnownIds = null,
            currentIdsBySource = mapOf("grades" to setOf("grades:g1")),
            allSourceKeys = sources,
            pendingBaselineKeys = emptySet()
        )

        val next = NotificationStateTracker.update(
            previousKnownIds = first.knownIds,
            currentIdsBySource = mapOf("grades" to setOf("grades:g1", "grades:g2")),
            allSourceKeys = sources,
            pendingBaselineKeys = first.pendingBaselineKeys
        )

        assertEquals(setOf("grades:g2"), next.newIds)
        assertEquals(setOf("descriptive_grades", "partial_grades"), next.pendingBaselineKeys)
    }

    @Test
    fun temporaryFailureAfterBaselinePreservesOldIdsAndDetectsNewOnRecovery() {
        val baseline = setOf("grades:g1", "descriptive_grades:d1")

        val failed = NotificationStateTracker.update(
            previousKnownIds = baseline,
            currentIdsBySource = mapOf("grades" to setOf("grades:g1")),
            allSourceKeys = sources,
            pendingBaselineKeys = emptySet()
        )

        assertEquals(baseline, failed.knownIds)
        assertTrue(failed.newIds.isEmpty())

        val recovered = NotificationStateTracker.update(
            previousKnownIds = failed.knownIds,
            currentIdsBySource = mapOf(
                "grades" to setOf("grades:g1"),
                "descriptive_grades" to setOf("descriptive_grades:d1", "descriptive_grades:d2"),
                "partial_grades" to emptySet()
            ),
            allSourceKeys = sources,
            pendingBaselineKeys = failed.pendingBaselineKeys
        )

        assertEquals(setOf("descriptive_grades:d2"), recovered.newIds)
        assertTrue(recovered.pendingBaselineKeys.isEmpty())
    }

    @Test
    fun emptyButAvailableFeedIsNotMarkedAsMissing() {
        val update = NotificationStateTracker.update(
            previousKnownIds = null,
            currentIdsBySource = mapOf(
                "grades" to emptySet(),
                "descriptive_grades" to emptySet(),
                "partial_grades" to emptySet()
            ),
            allSourceKeys = sources,
            pendingBaselineKeys = emptySet()
        )

        assertTrue(update.knownIds.isEmpty())
        assertTrue(update.newIds.isEmpty())
        assertTrue(update.pendingBaselineKeys.isEmpty())
    }
}

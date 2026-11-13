package pl.domreksio.librusallinone

internal data class NotificationStateUpdate(
    val knownIds: Set<String>,
    val newIds: Set<String>,
    val pendingBaselineKeys: Set<String>
)

/**
 * Keeps the first successful observation of temporarily unavailable feeds
 * from being mistaken for newly added records when those feeds recover.
 */
internal object NotificationStateTracker {
    fun update(
        previousKnownIds: Set<String>?,
        currentIdsBySource: Map<String, Set<String>>,
        allSourceKeys: Set<String>,
        pendingBaselineKeys: Set<String>
    ): NotificationStateUpdate {
        val availableKeys = currentIdsBySource.keys
        val unavailableKeys = allSourceKeys - availableKeys

        if (previousKnownIds == null) {
            return NotificationStateUpdate(
                knownIds = currentIdsBySource.values.flatten().toSet(),
                newIds = emptySet(),
                pendingBaselineKeys = unavailableKeys
            )
        }

        val recoveredBaselineKeys = pendingBaselineKeys.intersect(availableKeys)
        val knownBeforeSync = previousKnownIds.toMutableSet()

        // These feeds were unavailable during the very first baseline.
        // Their first recovered response is a baseline, not a notification.
        recoveredBaselineKeys.forEach { sourceKey ->
            knownBeforeSync.addAll(currentIdsBySource[sourceKey].orEmpty())
        }

        val current = currentIdsBySource.values.flatten().toMutableSet()

        // A later temporary failure must not erase previously known IDs.
        unavailableKeys.forEach { sourceKey ->
            knownBeforeSync.filterTo(current) { it.startsWith("$sourceKey:") }
        }

        return NotificationStateUpdate(
            knownIds = current,
            newIds = current - knownBeforeSync,
            pendingBaselineKeys = pendingBaselineKeys.intersect(allSourceKeys) - recoveredBaselineKeys
        )
    }
}

package pl.domreksio.librusallinone

internal data class NotificationStateDiff(
    val currentIds: Set<String>,
    val newIds: Set<String>,
    val pendingSeedKeys: Set<String>
)

/**
 * Keeps notification baselines safe when one Librus feed fails during the
 * first background sync and only becomes available on a later poll.
 */
internal object NotificationStateTracker {
    fun calculate(
        sourceKeys: List<String>,
        availableKeys: Set<String>,
        currentIds: Set<String>,
        previousIds: Set<String>?,
        pendingSeedKeys: Set<String>,
        notificationKey: String,
        legacyPrefix: String,
        legacyState: Boolean
    ): NotificationStateDiff {
        val unavailableKeys = sourceKeys.filter { it !in availableKeys }.toSet()

        if (previousIds == null) {
            return NotificationStateDiff(
                currentIds = currentIds.toSet(),
                newIds = emptySet(),
                pendingSeedKeys = unavailableKeys
            )
        }

        val recoveredKeys = pendingSeedKeys.intersect(availableKeys)
        val comparisonPrevious = previousIds.toMutableSet()

        // A recovered feed has no trustworthy previous snapshot. Seed its
        // current IDs once instead of announcing its entire historical content.
        currentIds.filterTo(comparisonPrevious) { identity ->
            recoveredKeys.any { identity.startsWith("$it:") }
        }

        val updatedCurrent = currentIds.toMutableSet()
        unavailableKeys.forEach { unavailableKey ->
            comparisonPrevious.filterTo(updatedCurrent) {
                it.startsWith("$unavailableKey:")
            }
        }

        val newIds = if (legacyState && notificationKey == "grades") {
            updatedCurrent.filterTo(linkedSetOf()) {
                it.startsWith("$legacyPrefix:")
            } - comparisonPrevious
        } else {
            updatedCurrent - comparisonPrevious
        }

        return NotificationStateDiff(
            currentIds = updatedCurrent,
            newIds = newIds,
            pendingSeedKeys = pendingSeedKeys.intersect(sourceKeys.toSet()) - recoveredKeys
        )
    }
}

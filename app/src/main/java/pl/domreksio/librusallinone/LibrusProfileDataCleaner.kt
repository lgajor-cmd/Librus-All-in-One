package pl.domreksio.librusallinone

import android.content.Context

/**
 * Removes non-secret local state owned by a profile after its credentials have been cleared.
 * The shared notification-state file is filtered by profile-specific key prefixes.
 */
internal object LibrusProfileDataCleaner {
    fun clear(context: Context, profileId: String) {
        require(
            profileId == LibrusProfileStore.LEGACY_PROFILE_ID ||
                profileId.matches(Regex("[a-f0-9]{32}"))
        ) { "Nieprawidłowy identyfikator profilu." }

        val appContext = context.applicationContext
        clearPreferences(appContext, "librus_notification_diagnostics", profileId)
        clearPreferences(appContext, "librus_notification_settings", profileId)
        clearPreferences(appContext, "librus_school_profile", profileId)

        val state = appContext.getSharedPreferences(
            "librus_notification_state",
            Context.MODE_PRIVATE
        )
        val prefix = if (profileId == LibrusProfileStore.LEGACY_PROFILE_ID) "" else "${profileId}_"
        val keysToRemove = state.all.keys.filter { key ->
            key.startsWith("${prefix}known_") || key.startsWith("${prefix}pending_seed_")
        }
        if (keysToRemove.isNotEmpty()) {
            val editor = state.edit()
            keysToRemove.forEach { key -> editor.remove(key) }
            check(editor.commit()) { "Nie udało się usunąć historii powiadomień profilu." }
        }
    }

    private fun clearPreferences(context: Context, baseName: String, profileId: String) {
        val name = when (baseName) {
            "librus_notification_diagnostics" ->
                if (profileId == LibrusProfileStore.LEGACY_PROFILE_ID) baseName else "${baseName}_${profileId}"
            "librus_notification_settings" ->
                if (profileId == LibrusProfileStore.LEGACY_PROFILE_ID) baseName else "${baseName}_${profileId}"
            "librus_school_profile" ->
                "librus_school_profile_${profileId}"
            else -> error("Nieobsługiwany magazyn profilu.")
        }
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        check(preferences.edit().clear().commit()) {
            "Nie udało się wyczyścić lokalnych danych profilu."
        }
        if (baseName == "librus_school_profile" &&
            profileId == LibrusProfileStore.LEGACY_PROFILE_ID
        ) {
            check(context.getSharedPreferences(baseName, Context.MODE_PRIVATE)
                .edit().clear().commit()) {
                "Nie udało się usunąć przeniesionych danych szkoły profilu."
            }
        }
    }
}

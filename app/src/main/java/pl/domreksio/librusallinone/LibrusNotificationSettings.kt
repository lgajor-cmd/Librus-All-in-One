package pl.domreksio.librusallinone

import android.content.Context
import android.content.SharedPreferences

/**
 * Notification preferences are isolated per Librus profile.
 * The legacy parent profile keeps the old preference file during migration.
 */
internal object LibrusNotificationSettings {
    private const val PREFS = "librus_notification_settings"

    fun preferences(context: Context, profileId: String): SharedPreferences =
        context.getSharedPreferences(
            if (profileId == LibrusProfileStore.LEGACY_PROFILE_ID) PREFS else "${PREFS}_${profileId}",
            Context.MODE_PRIVATE
        )
}

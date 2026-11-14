package pl.domreksio.librusallinone

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient

/**
 * Single application-wide Supabase client.
 *
 * Uses the same Android preferences file and URL-derived keys as supabase-kt's
 * default SettingsSessionManager/SettingsCodeVerifierCache, while supplying
 * Context explicitly so startup and WorkManager do not depend on no-arg
 * AndroidX Startup initialization.
 */
internal object SupabaseClientProvider {
    private const val SUPABASE_URL =
        "https://alegkhpqxdzuqjuuoawp.supabase.co"

    private const val SUPABASE_PUBLISHABLE_KEY =
        "sb_publishable_gchAnvMxlFgvzdenD0QqWg_YoOtsTPe"

    @Volatile
    private var instance: SupabaseClient? = null

    fun get(context: Context): SupabaseClient {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    private fun create(context: Context): SupabaseClient {
        val settings = SharedPreferencesSettings(
            context.getSharedPreferences(
                "${context.packageName}_preferences",
                Context.MODE_PRIVATE
            )
        )
        val settingsKeyPrefix =
            "sb-${SUPABASE_URL.removeSuffix("/").replace('/', '-').replace('.', '-')}"

        return createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Auth) {
                sessionManager = SettingsSessionManager(
                    settings = settings,
                    key = "$settingsKeyPrefix-${SettingsSessionManager.SETTINGS_KEY}"
                )
                codeVerifierCache = SettingsCodeVerifierCache(
                    settings = settings,
                    key = "$settingsKeyPrefix-${SettingsCodeVerifierCache.SETTINGS_KEY}"
                )
            }
        }
    }
}

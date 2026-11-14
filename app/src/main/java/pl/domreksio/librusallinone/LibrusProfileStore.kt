package pl.domreksio.librusallinone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Non-secret metadata for locally stored Librus accounts.
 * Passwords and Librus sessions remain in a separate Android Keystore-backed vault per profile.
 */
internal class LibrusProfileStore(context: Context) {
    data class Profile(
        val id: String,
        val displayName: String,
        val accountType: String
    ) {
        val isParent: Boolean get() = accountType == TYPE_PARENT
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun ensureDefaultProfile(): Profile {
        val existing = getProfiles()
        if (existing.isNotEmpty()) {
            val active = getActiveProfileId()?.let { id -> existing.firstOrNull { it.id == id } }
            val selected = active ?: existing.first()
            if (active == null) setActiveProfileId(selected.id)
            return selected
        }

        // "parent" is the legacy vault key used by releases before multi-profile support.
        // Keeping it unchanged preserves existing encrypted credentials and Keystore keys.
        val legacy = Profile(LEGACY_PROFILE_ID, "Konto rodzica", TYPE_PARENT)
        saveProfiles(listOf(legacy))
        setActiveProfileId(legacy.id)
        return legacy
    }

    @Synchronized
    fun getProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("displayName").trim()
                    val type = item.optString("accountType")
                    if (isValidProfileId(id) && name.isNotBlank() &&
                        (type == TYPE_PARENT || type == TYPE_STUDENT) &&
                        none { it.id == id }
                    ) {
                        add(Profile(id, name.take(MAX_NAME_LENGTH), type))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun getProfile(id: String): Profile? = getProfiles().firstOrNull { it.id == id }

    @Synchronized
    fun getActiveProfileId(): String? = prefs.getString(KEY_ACTIVE_PROFILE, null)
        ?.takeIf(::isValidProfileId)

    @Synchronized
    fun setActiveProfileId(id: String) {
        require(getProfile(id) != null) { "Nie znaleziono wybranego profilu." }
        check(prefs.edit().putString(KEY_ACTIVE_PROFILE, id).commit()) {
            "Nie udało się zapisać aktywnego profilu."
        }
    }

    @Synchronized
    fun addProfile(displayName: String, accountType: String): Profile {
        val normalizedName = displayName.trim().take(MAX_NAME_LENGTH)
        require(normalizedName.isNotBlank()) { "Podaj nazwę profilu." }
        require(accountType == TYPE_PARENT || accountType == TYPE_STUDENT) {
            "Nieobsługiwany typ konta."
        }
        val profile = Profile(UUID.randomUUID().toString().replace("-", ""), normalizedName, accountType)
        saveProfiles(getProfiles() + profile)
        setActiveProfileId(profile.id)
        return profile
    }

    @Synchronized
    fun removeProfile(id: String): Boolean {
        val profiles = getProfiles()
        if (profiles.size <= 1 || profiles.none { it.id == id }) return false
        val remaining = profiles.filterNot { it.id == id }
        saveProfiles(remaining)
        if (getActiveProfileId() == id) setActiveProfileId(remaining.first().id)
        return true
    }

    private fun saveProfiles(profiles: List<Profile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().apply {
                put("id", profile.id)
                put("displayName", profile.displayName)
                put("accountType", profile.accountType)
            })
        }
        check(prefs.edit().putString(KEY_PROFILES, array.toString()).commit()) {
            "Nie udało się zapisać listy profili."
        }
    }

    private fun isValidProfileId(id: String): Boolean =
        id == LEGACY_PROFILE_ID || id.matches(Regex("[a-f0-9]{32}"))

    companion object {
        const val TYPE_PARENT = "parent"
        const val TYPE_STUDENT = "student"
        const val LEGACY_PROFILE_ID = "parent"
        private const val PREFS_NAME = "librus_profiles"
        private const val KEY_PROFILES = "profiles_v1"
        private const val KEY_ACTIVE_PROFILE = "active_profile_v1"
        private const val MAX_NAME_LENGTH = 40
    }
}

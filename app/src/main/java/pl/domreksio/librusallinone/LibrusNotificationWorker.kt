package pl.domreksio.librusallinone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal object LibrusNotificationDiagnostics {
    private const val PREFS = "librus_notification_diagnostics"

    private fun preferences(context: Context, profileId: String) =
        context.getSharedPreferences(
            if (profileId == LibrusProfileStore.LEGACY_PROFILE_ID) PREFS else "${PREFS}_${profileId}",
            Context.MODE_PRIVATE
        )

    fun record(
        context: Context,
        status: String,
        detail: String = "",
        profileId: String = LibrusProfileStore.LEGACY_PROFILE_ID
    ) {
        val prefs = preferences(context, profileId)
        val edit = prefs.edit()
            .putLong("last_attempt", System.currentTimeMillis())
            .putString("last_status", status)
            .putString("last_detail", detail.take(400))
        if (status == "success" || status == "partial_success") {
            edit.putLong("last_success", System.currentTimeMillis())
                .putLong("success_count", prefs.getLong("success_count", 0L) + 1L)
        }
        if (status == "error" || status == "auth_required" || status == "blocked" || status == "partial_success" || status == "vault_error") {
            edit.putLong("error_count", prefs.getLong("error_count", 0L) + 1L)
        }
        edit.commit()
    }

    fun lastStatus(
        context: Context,
        profileId: String = LibrusProfileStore.LEGACY_PROFILE_ID
    ): String = preferences(context, profileId).getString("last_status", "").orEmpty()

    fun summary(
        context: Context,
        profileId: String = LibrusProfileStore.LEGACY_PROFILE_ID
    ): String {
        val prefs = preferences(context, profileId)
        fun time(key: String): String {
            val value = prefs.getLong(key, 0L)
            return if (value == 0L) "brak" else
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(value))
        }
        val status = prefs.getString("last_status", "jeszcze_nie_sprawdzano")
        val detail = prefs.getString("last_detail", "").orEmpty()
        return "Ostatnia próba: ${time("last_attempt")}\n" +
            "Ostatnie udane sprawdzenie: ${time("last_success")}\n" +
            "Status: $status\n" +
            "Udane sprawdzenia: ${prefs.getLong("success_count", 0L)}\n" +
            "Błędy: ${prefs.getLong("error_count", 0L)}" +
            if (detail.isBlank()) "" else "\nSzczegóły: $detail"
    }

    fun sendTest(
        context: Context,
        profileId: String = LibrusProfileStore.LEGACY_PROFILE_ID
    ): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            record(context, "blocked", "Brak uprawnienia Android POST_NOTIFICATIONS.", profileId)
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("librus_updates", "Aktualizacje EduFlow", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 26 &&
                manager.getNotificationChannel("librus_updates")?.importance == NotificationManager.IMPORTANCE_NONE)
        ) {
            record(context, "blocked", "Powiadomienia aplikacji lub kanał Aktualizacje EduFlow są wyłączone w Androidzie.", profileId)
            return false
        }
        val notification = NotificationCompat.Builder(context, "librus_updates")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("EduFlow – test powiadomień")
            .setContentText("Powiadomienia na tym urządzeniu działają poprawnie.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(41001, notification)
        record(context, "test_sent", "Wysłano test lokalny; nie sprawdza on jeszcze synchronizacji Librusa.", profileId)
        return true
    }
}

object LibrusNotificationScheduler {
    private const val UNIQUE_WORK_PREFIX = "librus-background-notifications-"

    fun schedule(context: Context, profileId: String = LibrusProfileStore.LEGACY_PROFILE_ID) {
        // Remove the single-profile worker used by older app versions to avoid duplicate syncs.
        WorkManager.getInstance(context).cancelUniqueWork("librus-background-notifications")
        val request = PeriodicWorkRequestBuilder<LibrusNotificationWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).setInputData(workDataOf("profile_id" to profileId))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_PREFIX + profileId,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context, profileId: String = LibrusProfileStore.LEGACY_PROFILE_ID) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_PREFIX + profileId)
    }
}

class LibrusNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val profileId = inputData.getString("profile_id")
            ?.takeIf { it == LibrusProfileStore.LEGACY_PROFILE_ID || it.matches(Regex("[a-f0-9]{32}")) }
            ?: LibrusProfileStore.LEGACY_PROFILE_ID
        val profile = LibrusProfileStore(applicationContext).getProfile(profileId)
        val profileName = profile?.displayName ?: "Konto Librus"
        val profileType = profile?.accountType ?: LibrusProfileStore.TYPE_PARENT
        recordDiagnostic(profileId, "running")
        val vault = LibrusCredentialVault(applicationContext, profileId)
        val vaultResult = vault.loadDetailed()
        val credentials = vaultResult.credentials ?: run {
            when (vaultResult.status) {
                LibrusCredentialVault.LoadStatus.NOT_CONFIGURED ->
                    recordDiagnostic(profileId,
                        "not_configured",
                        "Brak zapisanych danych logowania Librusa."
                    )
                LibrusCredentialVault.LoadStatus.KEY_MISSING ->
                    recordDiagnostic(profileId,
                        "vault_error",
                        "Brakuje klucza Android Keystore dla zapisanych danych. Nie usunięto zaszyfrowanych danych."
                    )
                LibrusCredentialVault.LoadStatus.DECRYPTION_FAILED ->
                    recordDiagnostic(profileId,
                        "vault_error",
                        "Android Keystore nie może odszyfrować zapisanych danych Librusa. Dane pozostawiono bez zmian."
                    )
                LibrusCredentialVault.LoadStatus.CORRUPTED ->
                    recordDiagnostic(profileId,
                        "vault_error",
                        "Zapisany magazyn danych logowania jest uszkodzony lub ma nieprawidłowy format. Dane pozostawiono bez zmian."
                    )
                LibrusCredentialVault.LoadStatus.OK -> Unit
            }
            return Result.success()
        }

        var connection: HttpURLConnection? = null
        return try {
            // Supabase may still be restoring the device session after process start.
            // Bound the wait so a background worker cannot remain suspended indefinitely.
            val sessionState = withTimeoutOrNull(20_000L) {
                supabase.auth.sessionStatus.first {
                    it !is SessionStatus.Initializing
                }
            } ?: run {
                recordDiagnostic(profileId, "error", "Przekroczono czas oczekiwania na sesję aplikacji.")
                return Result.retry()
            }
            if (sessionState !is SessionStatus.Authenticated) {
                recordDiagnostic(profileId, "auth_required", "Brak aktywnej sesji Supabase. Otwórz aplikację i ponów sprawdzanie.")
                return Result.retry()
            }

            val accessToken = supabase.auth.currentAccessTokenOrNull()
                ?: run {
                    recordDiagnostic(profileId, "auth_required", "Sesja Supabase nie udostępniła tokenu dostępu.")
                    return Result.retry()
                }

            val activeConnection = (URL("$BACKEND_URL/v1/sync").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 25_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            connection = activeConnection

            val payload = JSONObject().apply {
                put("profile", profileType)
                put("notifications_only", true)
                put("login", credentials.login)
                put("password", credentials.password)
                credentials.sessionJson?.let { put("session", JSONObject(it)) }
            }
            activeConnection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = activeConnection.responseCode
            val body = (if (code in 200..299) activeConnection.inputStream
                else activeConnection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code == 401 || code == 428 || code == 409) {
                val errorCode = activeConnection.getHeaderField("X-Librus-Error-Code").orEmpty()
                val detail = body.take(300).ifBlank { "HTTP $code" }
                when {
                    errorCode.startsWith("SUPABASE_") || errorCode.startsWith("AUTH_TOKEN_") -> {
                        recordDiagnostic(profileId,
                            "auth_required",
                            "Wymagana sesja aplikacji: $detail"
                        )
                        postNotification(
                            "Wymagana sesja aplikacji",
                            "Otwórz EduFlow, aby odnowić sesję aplikacji. Dane Librusa nie zostały zmienione.",
                            "START",
                            profileId
                        )
                    }
                    errorCode.startsWith("LIBRUS_") || code == 428 || code == 409 -> {
                        recordDiagnostic(profileId, "auth_required", detail)
                        postNotification(
                            "Wymagane działanie w Librusie",
                            "Otwórz EduFlow i sprawdź połączenie z Librusem. $detail",
                            "START",
                            profileId
                        )
                    }
                    else -> {
                        // An unclassified 401 must not be reported as a Librus
                        // password problem. Retry and leave a diagnostic trail.
                        recordDiagnostic(profileId,
                            "error",
                            "Nierozpoznany błąd autoryzacji HTTP $code: $detail"
                        )
                        return Result.retry()
                    }
                }
                return Result.success()
            }
            if (code !in 200..299) {
                recordDiagnostic(profileId, "error", "Backend zwrócił HTTP $code: ${body.take(250)}")
                return if (code >= 500 || code == 429) Result.retry() else Result.success()
            }

            val response = runCatching { JSONObject(body) }.getOrElse {
                recordDiagnostic(profileId, "error", "Backend zwrócił nieprawidłowy JSON.")
                return Result.retry()
            }
            val data = response.optJSONObject("data") ?: run {
                recordDiagnostic(profileId, "error", "Odpowiedź backendu nie zawiera sekcji data.")
                return Result.retry()
            }
            response.optJSONObject("session")?.let {
                vault.updateSession(it.toString())
            }

            detectNewItems(
                data,
                listOf("grades", "descriptive_grades", "partial_grades", "point_grades", "text_grades", "behaviour_grades"),
                "grades",
                "OCENY",
                "Nowa ocena",
                profileId,
                profileName
            )
            detectNewItems(data, listOf("messages"), "messages", "WIADOMOŚCI", "Nowa wiadomość", profileId, profileName)
            detectNewItems(data, listOf("notes"), "notes", "UWAGI", "Nowa uwaga", profileId, profileName)
            detectNewItems(data, listOf("behaviour_notes"), "behaviour_notes", "UWAGI", "Nowa uwaga", profileId, profileName)
            val notificationStatus = LibrusNotificationDiagnostics.lastStatus(
                applicationContext,
                profileId
            )
            val failedSections = data.optJSONArray("failed_sections")
            if (notificationStatus == "blocked") {
                // Preserve the actionable Android permission/channel problem;
                // do not overwrite it with a generic partial-sync status.
            } else if (failedSections != null && failedSections.length() > 0) {
                val names = (0 until failedSections.length())
                    .map { failedSections.optString(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                recordDiagnostic(profileId,
                    "partial_success",
                    "Synchronizacja częściowa. Niedostępne kategorie: ${names.joinToString(", ")}"
                )
            } else {
                recordDiagnostic(profileId, "success")
            }
            Result.success()
        } catch (error: Exception) {
            recordDiagnostic(profileId,
                "error",
                "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            )
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    private fun recordDiagnostic(profileId: String, status: String, detail: String = "") {
        LibrusNotificationDiagnostics.record(applicationContext, status, detail, profileId)
    }

    private fun detectNewItems(
        data: JSONObject,
        sourceKeys: List<String>,
        notificationKey: String,
        section: String,
        title: String,
        profileId: String,
        profileName: String
    ) {
        val failedSections = data.optJSONArray("failed_sections")
        val failed = mutableSetOf<String>()
        if (failedSections != null) {
            for (i in 0 until failedSections.length()) {
                failed.add(failedSections.optString(i))
            }
        }

        val availableKeys = sourceKeys.filter { key ->
            data.optJSONArray(key) != null && key !in failed
        }
        // A missing/failed Librus section is not an empty section. Keep its
        // previous IDs so a temporary API failure cannot trigger old alerts later.
        if (availableKeys.isEmpty()) return

        val prefs = applicationContext.getSharedPreferences(
            "librus_notification_state",
            Context.MODE_PRIVATE
        )
        val current = linkedSetOf<String>()
        val items = mutableListOf<Pair<String, JSONObject>>()

        for (sourceKey in availableKeys) {
            val array = data.optJSONArray(sourceKey) ?: continue
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.opt("id")?.toString()?.takeIf {
                    it.isNotBlank() && it != "null"
                } ?: MessageDigest.getInstance("SHA-256")
                    .digest(item.toString().toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte -> "%02x".format(byte) }
                val identity = "$sourceKey:$id"
                current.add(identity)
                items.add(identity to item)
            }
        }

        val profilePrefix = if (profileId == LibrusProfileStore.LEGACY_PROFILE_ID) "" else "${profileId}_"
        val prefKey = "${profilePrefix}known_$notificationKey"
        val pendingSeedPrefKey = "${profilePrefix}pending_seed_$notificationKey"
        val legacyPrefix = sourceKeys.first()
        val previousRaw = prefs.getString(prefKey, null)
        val previousValues = previousRaw?.split("|")?.filter { it.isNotBlank() }
        val legacyState = previousValues != null && previousValues.none { it.contains(":") }
        val previous = previousValues
            ?.map { if (it.contains(":")) it else "$legacyPrefix:$it" }
            ?.toSet()
        val pendingSeedKeys = prefs.getString(pendingSeedPrefKey, null)
            ?.split("|")?.filter { it.isNotBlank() }?.toSet().orEmpty()

        val stateDiff = NotificationStateTracker.calculate(
            sourceKeys = sourceKeys,
            availableKeys = availableKeys.toSet(),
            currentIds = current,
            previousIds = previous,
            pendingSeedKeys = pendingSeedKeys,
            notificationKey = notificationKey,
            legacyPrefix = legacyPrefix,
            legacyState = legacyState
        )

        if (previous == null) {
            prefs.edit()
                .putString(prefKey, stateDiff.currentIds.joinToString("|"))
                .putString(pendingSeedPrefKey, stateDiff.pendingSeedKeys.joinToString("|"))
                .commit()
            return
        }

        val newIds = stateDiff.newIds
        if (newIds.isNotEmpty() && notificationsEnabled(notificationKey, profileId)) {
            val newest = items.firstOrNull { it.first in newIds }?.second
            val detail = newest?.let {
                listOf("title", "subject", "topic", "value", "text", "skill")
                    .firstNotNullOfOrNull { field ->
                        it.optString(field).takeIf(String::isNotBlank)
                    }
            }.orEmpty()
            val posted = postNotification(
                "$title • $profileName",
                if (detail.isBlank()) "Profil: $profileName. Otwórz EduFlow, aby zobaczyć szczegóły." else "$profileName: $detail",
                section,
                profileId
            )
            if (!posted) {
                recordDiagnostic(profileId,
                    "blocked",
                    "Wykryto nowe dane, ale Android blokuje wyświetlanie powiadomień."
                )
                return
            }
        }
        prefs.edit()
            .putString(prefKey, stateDiff.currentIds.joinToString("|"))
            .putString(pendingSeedPrefKey, stateDiff.pendingSeedKeys.joinToString("|"))
            .commit()
    }

    private fun notificationsEnabled(key: String, profileId: String): Boolean =
        LibrusNotificationSettings.preferences(applicationContext, profileId).getBoolean(key, true)

    private fun postNotification(title: String, text: String, section: String, profileId: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("librus_updates", "Aktualizacje EduFlow", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 26 &&
                manager.getNotificationChannel("librus_updates")?.importance == NotificationManager.IMPORTANCE_NONE)
        ) return false

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_librus_section", section)
            putExtra("profile_id", profileId)
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 31 * profileId.hashCode() + section.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, "librus_updates")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(
            (profileId + section + title + System.currentTimeMillis()).hashCode(), notification
        )
        return true
    }
}

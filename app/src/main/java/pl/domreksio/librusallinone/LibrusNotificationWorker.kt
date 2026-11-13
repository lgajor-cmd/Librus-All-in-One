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

    fun record(context: Context, status: String, detail: String = "") {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    fun summary(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    fun sendTest(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            record(context, "blocked", "Brak uprawnienia Android POST_NOTIFICATIONS.")
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("librus_updates", "Aktualizacje Librus", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 26 &&
                manager.getNotificationChannel("librus_updates")?.importance == NotificationManager.IMPORTANCE_NONE)
        ) {
            record(context, "blocked", "Powiadomienia aplikacji lub kanał Aktualizacje Librus są wyłączone w Androidzie.")
            return false
        }
        val notification = NotificationCompat.Builder(context, "librus_updates")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Librus – test powiadomień")
            .setContentText("Powiadomienia na tym urządzeniu działają poprawnie.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(41001, notification)
        record(context, "test_sent", "Wysłano test lokalny; nie sprawdza on jeszcze synchronizacji Librusa.")
        return true
    }
}

object LibrusNotificationScheduler {
    private const val UNIQUE_WORK = "librus-background-notifications"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<LibrusNotificationWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}

class LibrusNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        LibrusNotificationDiagnostics.record(applicationContext, "running")
        val vaultResult = LibrusCredentialVault(applicationContext).loadDetailed()
        val credentials = vaultResult.credentials ?: run {
            when (vaultResult.status) {
                LibrusCredentialVault.LoadStatus.NOT_CONFIGURED ->
                    LibrusNotificationDiagnostics.record(
                        applicationContext,
                        "not_configured",
                        "Brak zapisanych danych logowania Librusa."
                    )
                LibrusCredentialVault.LoadStatus.KEY_MISSING ->
                    LibrusNotificationDiagnostics.record(
                        applicationContext,
                        "vault_error",
                        "Brakuje klucza Android Keystore dla zapisanych danych. Nie usunięto zaszyfrowanych danych."
                    )
                LibrusCredentialVault.LoadStatus.DECRYPTION_FAILED ->
                    LibrusNotificationDiagnostics.record(
                        applicationContext,
                        "vault_error",
                        "Android Keystore nie może odszyfrować zapisanych danych Librusa. Dane pozostawiono bez zmian."
                    )
                LibrusCredentialVault.LoadStatus.CORRUPTED ->
                    LibrusNotificationDiagnostics.record(
                        applicationContext,
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
                LibrusNotificationDiagnostics.record(applicationContext, "error", "Przekroczono czas oczekiwania na sesję aplikacji.")
                return Result.retry()
            }
            if (sessionState !is SessionStatus.Authenticated) {
                LibrusNotificationDiagnostics.record(applicationContext, "auth_required", "Brak aktywnej sesji Supabase. Otwórz aplikację i ponów sprawdzanie.")
                return Result.retry()
            }

            val accessToken = supabase.auth.currentAccessTokenOrNull()
                ?: run {
                    LibrusNotificationDiagnostics.record(applicationContext, "auth_required", "Sesja Supabase nie udostępniła tokenu dostępu.")
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
                put("profile", "parent")
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
                        LibrusNotificationDiagnostics.record(
                            applicationContext,
                            "auth_required",
                            "Wymagana sesja aplikacji: $detail"
                        )
                        postNotification(
                            "Wymagana sesja aplikacji",
                            "Otwórz Librus All-in-One, aby odnowić sesję aplikacji. Dane Librusa nie zostały zmienione.",
                            "START"
                        )
                    }
                    errorCode.startsWith("LIBRUS_") || code == 428 || code == 409 -> {
                        LibrusNotificationDiagnostics.record(applicationContext, "auth_required", detail)
                        postNotification(
                            "Wymagane działanie w Librusie",
                            "Otwórz aplikację i sprawdź połączenie z Librusem. $detail",
                            "START"
                        )
                    }
                    else -> {
                        // An unclassified 401 must not be reported as a Librus
                        // password problem. Retry and leave a diagnostic trail.
                        LibrusNotificationDiagnostics.record(
                            applicationContext,
                            "error",
                            "Nierozpoznany błąd autoryzacji HTTP $code: $detail"
                        )
                        return Result.retry()
                    }
                }
                return Result.success()
            }
            if (code !in 200..299) {
                LibrusNotificationDiagnostics.record(applicationContext, "error", "Backend zwrócił HTTP $code: ${body.take(250)}")
                return if (code >= 500 || code == 429) Result.retry() else Result.success()
            }

            val response = runCatching { JSONObject(body) }.getOrElse {
                LibrusNotificationDiagnostics.record(applicationContext, "error", "Backend zwrócił nieprawidłowy JSON.")
                return Result.retry()
            }
            val data = response.optJSONObject("data") ?: run {
                LibrusNotificationDiagnostics.record(applicationContext, "error", "Odpowiedź backendu nie zawiera sekcji data.")
                return Result.retry()
            }
            response.optJSONObject("session")?.let {
                LibrusCredentialVault(applicationContext).updateSession(it.toString())
            }

            detectNewItems(
                data,
                listOf("grades", "descriptive_grades", "partial_grades", "point_grades", "text_grades", "behaviour_grades"),
                "grades",
                "OCENY",
                "Nowa ocena"
            )
            detectNewItems(data, listOf("messages"), "messages", "WIADOMOŚCI", "Nowa wiadomość")
            detectNewItems(data, listOf("notes"), "notes", "UWAGI", "Nowa uwaga")
            detectNewItems(data, listOf("behaviour_notes"), "behaviour_notes", "UWAGI", "Nowa uwaga")
            val notificationStatus = applicationContext.getSharedPreferences(
                "librus_notification_diagnostics",
                Context.MODE_PRIVATE
            ).getString("last_status", "")
            val failedSections = data.optJSONArray("failed_sections")
            if (notificationStatus == "blocked") {
                // Preserve the actionable Android permission/channel problem;
                // do not overwrite it with a generic partial-sync status.
            } else if (failedSections != null && failedSections.length() > 0) {
                val names = (0 until failedSections.length())
                    .map { failedSections.optString(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                LibrusNotificationDiagnostics.record(
                    applicationContext,
                    "partial_success",
                    "Synchronizacja częściowa. Niedostępne kategorie: ${names.joinToString(", ")}"
                )
            } else {
                LibrusNotificationDiagnostics.record(applicationContext, "success")
            }
            Result.success()
        } catch (error: Exception) {
            LibrusNotificationDiagnostics.record(
                applicationContext,
                "error",
                "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            )
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    private fun detectNewItems(
        data: JSONObject,
        sourceKeys: List<String>,
        notificationKey: String,
        section: String,
        title: String
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
        val items = mutableListOf<Pair<String, JSONObject>>()
        val currentIdsBySource = linkedMapOf<String, MutableSet<String>>()

        for (sourceKey in availableKeys) {
            val array = data.optJSONArray(sourceKey) ?: continue
            val sourceIds = currentIdsBySource.getOrPut(sourceKey) { linkedSetOf() }
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.opt("id")?.toString()?.takeIf {
                    it.isNotBlank() && it != "null"
                } ?: MessageDigest.getInstance("SHA-256")
                    .digest(item.toString().toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte -> "%02x".format(byte) }
                val identity = "$sourceKey:$id"
                sourceIds.add(identity)
                items.add(identity to item)
            }
        }

        val prefKey = "known_$notificationKey"
        val pendingBaselinePrefKey = "pending_baseline_$notificationKey"
        val legacyPrefix = sourceKeys.first()
        val previousRaw = prefs.getString(prefKey, null)
        val previousValues = previousRaw?.split("|")?.filter { it.isNotBlank() }
        val legacyState = previousValues != null && previousValues.none { it.contains(":") }
        val previous = previousValues
            ?.map { if (it.contains(":")) it else "$legacyPrefix:$it" }
            ?.toSet()
        val storedPendingBaseline = prefs.getString(pendingBaselinePrefKey, null)
            ?.split("|")?.filter { it.isNotBlank() }?.toSet().orEmpty()
        // Older installs already have a baseline for ordinary grades, but
        // the descriptive/partial/point/text/behaviour feeds are new. Seed
        // those feeds on migration, and also seed ordinary grades if that
        // feed itself is unavailable during the migration sync.
        val migrationPendingBaseline = if (legacyState && notificationKey == "grades") {
            sourceKeys.filter { it != "grades" }.toSet() +
                if ("grades" !in availableKeys) setOf("grades") else emptySet()
        } else {
            emptySet()
        }
        val pendingBaseline = storedPendingBaseline + migrationPendingBaseline

        val stateUpdate = NotificationStateTracker.update(
            previousKnownIds = previous,
            currentIdsBySource = currentIdsBySource.mapValues { it.value.toSet() },
            allSourceKeys = sourceKeys.toSet(),
            pendingBaselineKeys = pendingBaseline
        )

        // Before this update only ordinary "grades" were monitored. During
        // migration, seed newly supported grade types without announcing
        // every historical descriptive/partial/point grade as if it were new.
        val newIds = if (legacyState && notificationKey == "grades") {
            stateUpdate.newIds.filterTo(linkedSetOf()) { it.startsWith("grades:") }
        } else {
            stateUpdate.newIds
        }
        if (newIds.isNotEmpty() && notificationsEnabled(notificationKey)) {
            val newest = items.firstOrNull { it.first in newIds }?.second
            val detail = newest?.let {
                listOf("title", "subject", "topic", "value", "text", "skill")
                    .firstNotNullOfOrNull { field ->
                        it.optString(field).takeIf(String::isNotBlank)
                    }
            }.orEmpty()
            val posted = postNotification(
                title,
                if (detail.isBlank()) "Otwórz aplikację, aby zobaczyć szczegóły." else detail,
                section
            )
            if (!posted) {
                LibrusNotificationDiagnostics.record(
                    applicationContext,
                    "blocked",
                    "Wykryto nowe dane, ale Android blokuje wyświetlanie powiadomień."
                )
                return
            }
        }
        prefs.edit()
            .putString(prefKey, stateUpdate.knownIds.joinToString("|"))
            .putString(pendingBaselinePrefKey, stateUpdate.pendingBaselineKeys.joinToString("|"))
            .commit()
    }

    private fun notificationsEnabled(key: String): Boolean {
        val prefs = applicationContext.getSharedPreferences("librus_notification_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean(key, true)
    }

    private fun postNotification(title: String, text: String, section: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("librus_updates", "Aktualizacje Librus", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= 26 &&
                manager.getNotificationChannel("librus_updates")?.importance == NotificationManager.IMPORTANCE_NONE)
        ) return false

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_librus_section", section)
        }
        val pending = PendingIntent.getActivity(
            applicationContext, section.hashCode(), intent,
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
            (section + title + System.currentTimeMillis()).hashCode(), notification
        )
        return true
    }
}

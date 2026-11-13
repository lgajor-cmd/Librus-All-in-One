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
import java.util.concurrent.TimeUnit

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
        val credentials = LibrusCredentialVault(applicationContext).load()
            ?: return Result.success()

        var connection: HttpURLConnection? = null
        return try {
            // Supabase may still be restoring the device session after process start.
            // Bound the wait so a background worker cannot remain suspended indefinitely.
            val sessionState = withTimeoutOrNull(20_000L) {
                supabase.auth.sessionStatus.first {
                    it !is SessionStatus.Initializing
                }
            } ?: return Result.retry()
            if (sessionState !is SessionStatus.Authenticated) return Result.success()

            val accessToken = supabase.auth.currentAccessTokenOrNull()
                ?: return Result.retry()

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

            if (code == 401 || code == 428 || code == 409) return Result.success()
            if (code !in 200..299) return if (code >= 500 || code == 429) Result.retry() else Result.success()

            val response = JSONObject(body)
            val data = response.optJSONObject("data") ?: return Result.retry()
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
            Result.success()
        } catch (_: Exception) {
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
        val current = linkedSetOf<String>()
        val items = mutableListOf<Pair<String, JSONObject>>()

        for (sourceKey in availableKeys) {
            val array = data.optJSONArray(sourceKey) ?: continue
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.opt("id")?.toString()?.takeIf {
                    it.isNotBlank() && it != "null"
                } ?: item.toString().hashCode().toString()
                val identity = "$sourceKey:$id"
                current.add(identity)
                items.add(identity to item)
            }
        }

        val prefKey = "known_$notificationKey"
        val legacyPrefix = sourceKeys.first()
        val previousRaw = prefs.getString(prefKey, null)
        val previousValues = previousRaw?.split("|")?.filter { it.isNotBlank() }
        val legacyState = previousValues != null && previousValues.none { it.contains(":") }
        val previous = previousValues
            ?.map { if (it.contains(":")) it else "$legacyPrefix:$it" }
            ?.toSet()

        if (previous == null) {
            prefs.edit().putString(prefKey, current.joinToString("|")).apply()
            return
        }

        val unavailableKeys = sourceKeys - availableKeys.toSet()
        unavailableKeys.forEach { unavailableKey ->
            previous.filterTo(current) { it.startsWith("$unavailableKey:") }
        }

        // Before this update only ordinary "grades" were monitored. During
        // migration, seed newly supported grade types without announcing
        // every historical descriptive/partial/point grade as if it were new.
        val newIds = if (legacyState && notificationKey == "grades") {
            current.filterTo(linkedSetOf()) { it.startsWith("grades:") } - previous
        } else {
            current - previous
        }
        if (newIds.isNotEmpty() && notificationsEnabled(notificationKey)) {
            val newest = items.firstOrNull { it.first in newIds }?.second
            val detail = newest?.let {
                listOf("title", "subject", "topic", "value", "text", "skill")
                    .firstNotNullOfOrNull { field ->
                        it.optString(field).takeIf(String::isNotBlank)
                    }
            }.orEmpty()
            postNotification(
                title,
                if (detail.isBlank()) "Otwórz aplikację, aby zobaczyć szczegóły." else detail,
                section
            )
        }
        prefs.edit().putString(prefKey, current.joinToString("|")).apply()
    }

    private fun notificationsEnabled(key: String): Boolean {
        val prefs = applicationContext.getSharedPreferences("librus_notification_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean(key, true)
    }

    private fun postNotification(title: String, text: String, section: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("librus_updates", "Aktualizacje Librus", NotificationManager.IMPORTANCE_DEFAULT)
        )

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
    }
}

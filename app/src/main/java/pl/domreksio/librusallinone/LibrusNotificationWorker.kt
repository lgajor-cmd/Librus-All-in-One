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
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
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

        return try {
            val sessionState = supabase.auth.sessionStatus.first {
                it !is SessionStatus.Initializing
            }
            if (sessionState !is SessionStatus.Authenticated) return Result.success()

            val accessToken = supabase.auth.currentAccessTokenOrNull()
                ?: return Result.retry()

            val connection = (URL("$BACKEND_URL/v1/sync").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 25_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("profile", "parent")
                put("login", credentials.login)
                put("password", credentials.password)
                credentials.sessionJson?.let { put("session", JSONObject(it)) }
            }
            connection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream
                else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (code == 401 || code == 428 || code == 409) return Result.success()
            if (code !in 200..299) return if (code >= 500 || code == 429) Result.retry() else Result.success()

            val response = JSONObject(body)
            val data = response.optJSONObject("data") ?: return Result.retry()
            response.optJSONObject("session")?.let {
                LibrusCredentialVault(applicationContext).updateSession(it.toString())
            }

            detectNewItems(data, "grades", "OCENY", "Nowa ocena")
            detectNewItems(data, "messages", "WIADOMOŚCI", "Nowa wiadomość")
            detectNewItems(data, "notes", "UWAGI", "Nowa uwaga")
            detectNewItems(data, "behaviour_notes", "UWAGI", "Nowa uwaga")
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun detectNewItems(data: JSONObject, key: String, section: String, title: String) {
        val array = data.optJSONArray(key) ?: return
        val prefs = applicationContext.getSharedPreferences("librus_notification_state", Context.MODE_PRIVATE)
        val current = linkedSetOf<String>()
        val items = mutableListOf<JSONObject>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                ?: item.toString().hashCode().toString()
            current.add(id)
            items.add(item)
        }

        val prefKey = "known_$key"
        val previousRaw = prefs.getString(prefKey, null)
        val previous = previousRaw?.split("|")?.filter { it.isNotBlank() }?.toSet()
        if (previous == null) {
            prefs.edit().putString(prefKey, current.joinToString("|")).apply()
            return
        }

        val newIds = current - previous
        if (newIds.isNotEmpty() && notificationsEnabled(key)) {
            val newest = items.firstOrNull {
                val id = it.opt("id")?.toString()?.takeIf { value -> value.isNotBlank() && value != "null" }
                    ?: it.toString().hashCode().toString()
                id in newIds
            }
            val detail = newest?.let {
                listOf("title", "subject", "topic", "value", "text")
                    .firstNotNullOfOrNull { field -> it.optString(field).takeIf(String::isNotBlank) }
            }.orEmpty()
            postNotification(title, if (detail.isBlank()) "Otwórz aplikację, aby zobaczyć szczegóły." else detail, section)
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
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel("librus_updates", "Aktualizacje Librus", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_librus_section", section)
        }
        val pending = PendingIntent.getActivity(
            applicationContext, section.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, "librus_updates")
            .setSmallIcon(applicationApplicationIcon())
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

    private fun applicationApplicationIcon(): Int = R.drawable.ic_launcher
}

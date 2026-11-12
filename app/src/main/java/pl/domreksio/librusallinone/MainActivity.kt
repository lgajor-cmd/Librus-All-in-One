package pl.domreksio.librusallinone

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private const val SUPABASE_URL =
    "https://alegkhpqxdzuqjuuoawp.supabase.co"

private const val SUPABASE_PUBLISHABLE_KEY =
    "sb_publishable_gchAnvMxlFgvzdenD0QqWg_YoOtsTPe"

private const val AUTH_REDIRECT =
    "pl.domreksio.librusallinone://login-callback"

private const val BACKEND_URL =
    "https://librus-all-in-one-api.onrender.com"

private val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_PUBLISHABLE_KEY
) {
    install(Auth) {
        host = "login-callback"
        scheme = "pl.domreksio.librusallinone"
        flowType = FlowType.PKCE
    }
}

class MainActivity : Activity() {

    private val activityScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var backendStatusText: TextView

    private lateinit var librusLoginInput: EditText
    private lateinit var librusPasswordInput: EditText
    private lateinit var librusStatusText: TextView
    private lateinit var librusConnectButton: Button

    private var librusLoginMemory: String? = null
    private var librusPasswordMemory: String? = null
    private var librusSessionMemory: JSONObject? = null
    private var librusDataMemory: JSONObject? = null
    private var syncInProgress = false

    private val white = Color.rgb(242, 245, 250)
    private val muted = Color.rgb(165, 178, 198)
    private val panel = Color.rgb(25, 34, 51)
    private val bgColor = Color.rgb(13, 18, 30)
    private val successColor = Color.rgb(100, 210, 140)
    private val errorColor = Color.rgb(255, 125, 125)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showLogin("Sprawdzam sesję konta…")

        activityScope.launch {
            supabase.auth.sessionStatus.collect { state ->
                when (state) {
                    is SessionStatus.Authenticated -> {
                        showHome(state.session.user?.email.orEmpty())
                    }

                    SessionStatus.Initializing -> {
                        showLogin("Sprawdzam zapisane konto…")
                    }

                    is SessionStatus.NotAuthenticated -> {
                        showLogin("Zaloguj się, aby otworzyć aplikację.")
                    }

                    is SessionStatus.RefreshFailure -> {
                        showLogin("Sesja wygasła. Zaloguj się ponownie.")
                    }
                }
            }
        }

        try {
            supabase.handleDeeplinks(intent)
        } catch (e: Exception) {
            showLogin("Błąd linku: ${e.message ?: "nieznany błąd"}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        try {
            supabase.handleDeeplinks(intent)
        } catch (e: Exception) {
            showLogin("Błąd linku logowania: ${e.message}")
        }
    }

    private fun showLogin(message: String) {
        if (isFinishing) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 36, 24, 28)
            setBackgroundColor(bgColor)
        }

        content.addView(label("LIBRUS / ALL-IN-ONE", 13f, muted, true))
        content.addView(label("Twoje konto\nw jednym miejscu", 30f, white, true))

        content.addView(
            label(
                "Zaloguj się do konta aplikacji. To konto jest oddzielne od danych Librusa.",
                15f, muted
            )
        )

        emailInput = EditText(this).apply {
            hint = "Adres e-mail"
            setSingleLine(true)
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(16, 12, 16, 12)
            setBackgroundColor(panel)
        }

        content.addView(emailInput, params(0, 18, 0, 8))

        passwordInput = EditText(this).apply {
            hint = "Hasło do konta aplikacji"
            setSingleLine(true)
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(16, 12, 16, 12)
            setBackgroundColor(panel)
        }

        content.addView(passwordInput, params(0, 0, 0, 12))

        content.addView(Button(this).apply {
            text = "ZAREJESTRUJ KONTO"
            setOnClickListener { authenticate(true) }
        }, params(0, 4, 0, 6))

        content.addView(Button(this).apply {
            text = "ZALOGUJ SIĘ"
            setOnClickListener { authenticate(false) }
        }, params(0, 4, 0, 6))

        statusText = label(message, 14f, muted)
        content.addView(statusText, params(0, 14, 0, 4))

        content.addView(
            label(
                "Po rejestracji potwierdź adres e-mail. Po zalogowaniu otworzy się ekran główny.",
                13f, muted
            )
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(content)
        })
    }

    private fun showHome(email: String) {
        if (isFinishing) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 36, 24, 28)
            setBackgroundColor(bgColor)
        }

        content.addView(label("LIBRUS / ALL-IN-ONE", 13f, muted, true))
        content.addView(label("Witaj!", 32f, white, true))
        content.addView(label("Zalogowano jako: $email", 14f, muted))
        content.addView(label("Konto aplikacji działa.", 15f, white))

        content.addView(
            label("POŁĄCZENIE Z BACKENDEM", 14f, white, true),
            params(0, 18, 0, 4)
        )

        backendStatusText = label(
            "Nie sprawdzono jeszcze połączenia.",
            14f, muted
        )

        content.addView(backendStatusText)

        content.addView(Button(this).apply {
            text = "SPRAWDŹ POŁĄCZENIE Z SERWEREM"
            setOnClickListener { checkBackendConnection() }
        }, params(0, 8, 0, 12))

        content.addView(
            label("POŁĄCZ KONTO LIBRUS", 18f, white, true),
            params(0, 20, 0, 4)
        )

        content.addView(
            label(
                "Wybierz konto rodzica i podaj dane logowania do Librus Synergia.",
                14f, muted
            )
        )

        librusLoginInput = EditText(this).apply {
            hint = "Login do Librusa"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(16, 12, 16, 12)
            setBackgroundColor(panel)
        }

        content.addView(librusLoginInput, params(0, 12, 0, 8))

        librusPasswordInput = EditText(this).apply {
            hint = "Hasło do Librusa"
            setSingleLine(true)
            inputType =
                InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(16, 12, 16, 12)
            setBackgroundColor(panel)
        }

        content.addView(librusPasswordInput, params(0, 0, 0, 8))

        librusConnectButton = Button(this).apply {
            text = "POŁĄCZ Z LIBRUSEM"
            setOnClickListener { connectLibrus() }
        }

        content.addView(librusConnectButton, params(0, 4, 0, 8))

        librusStatusText = label(
            "Konto Librus nie zostało jeszcze podłączone.",
            14f, muted
        )

        content.addView(librusStatusText)

        content.addView(
            label(
                "Dane logowania są przesyłane do Twojego backendu przez HTTPS. Hasło nie jest zapisywane na serwerze.",
                12f, muted
            ),
            params(0, 4, 0, 12)
        )

        addHomeSection(content, "PROFILE", "Profile rodzica i ucznia")
        addHomeSection(content, "OCENY", "Oceny zwykłe, opisowe, punktowe i tekstowe")
        addHomeSection(content, "FREKWENCJA", "Obecności i nieobecności")
        addHomeSection(content, "PLAN LEKCJI", "Plan lekcji i zajęcia")
        addHomeSection(content, "WIADOMOŚCI", "Wiadomości i ogłoszenia")

        content.addView(Button(this).apply {
            text = "WYLOGUJ"
            setOnClickListener { signOut() }
        }, params(0, 24, 0, 8))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(content)
        })
    }

    private fun connectLibrus() {
        val login = librusLoginInput.text.toString().trim()
        val password = librusPasswordInput.text.toString()

        if (login.isBlank()) {
            librusStatusText.text = "Podaj login do Librusa."
            librusStatusText.setTextColor(errorColor)
            return
        }

        if (password.isBlank()) {
            librusStatusText.text = "Podaj hasło do Librusa."
            librusStatusText.setTextColor(errorColor)
            return
        }

        val accessToken = supabase.auth.currentAccessTokenOrNull()

        if (accessToken.isNullOrBlank()) {
            librusStatusText.text =
                "Brak aktywnej sesji aplikacji. Zaloguj się ponownie."
            librusStatusText.setTextColor(errorColor)
            return
        }

        librusConnectButton.isEnabled = false
        librusStatusText.text =
            "Łączę się z Librusem. To może chwilę potrwać…"
        librusStatusText.setTextColor(muted)

        thread {
            var connection: HttpURLConnection? = null

            val result = try {
                connection =
                    URL("$BACKEND_URL/v1/connect")
                        .openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.doOutput = true

                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")

                val payload = JSONObject().apply {
                    put("profile", "parent")
                    put("login", login)
                    put("password", password)
                }

                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode

                val responseBody =
                    if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)

                    if (json.has("student") && json.has("session")) {
                        librusLoginMemory = login
                        librusPasswordMemory = password
                        librusSessionMemory = json.optJSONObject("session")
                        librusDataMemory = null

                        "SUCCESS|Połączono z Librusem. Wybierz sekcję, aby pobrać dane."
                    } else {
                        "ERROR|Serwer nie zwrócił profilu lub sesji Librusa."
                    }
                } else {
                    val detail = try {
                        JSONObject(responseBody).optString("detail", "Kod HTTP $responseCode")
                    } catch (_: Exception) {
                        "Kod HTTP $responseCode"
                    }

                    "ERROR|Błąd połączenia ($responseCode): $detail"
                }
            } catch (e: Exception) {
                "ERROR|Nie udało się połączyć: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                if (!isFinishing && ::librusStatusText.isInitialized) {
                    val successful = result.startsWith("SUCCESS|")
                    librusStatusText.text = result.substringAfter("|")
                    librusStatusText.setTextColor(
                        if (successful) successColor else errorColor
                    )
                    librusConnectButton.isEnabled = true
                    librusPasswordInput.text.clear()
                }
            }
        }
    }

    private fun loadLibrusSection(section: String) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()

        if (login.isNullOrBlank() || password.isNullOrBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Brak połączenia z Librusem")
                .setMessage(
                    "Najpierw połącz konto Librus. Dane logowania są przechowywane tylko w pamięci aplikacji."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (syncInProgress) return

        val cached = librusDataMemory
        if (cached != null) {
            showLibrusSection(section, cached)
            return
        }

        if (token.isNullOrBlank()) {
            librusStatusText.text =
                "Sesja aplikacji wygasła. Zaloguj się ponownie."
            librusStatusText.setTextColor(errorColor)
            return
        }

        syncInProgress = true
        librusStatusText.text = "Pobieram dane z Librusa…"
        librusStatusText.setTextColor(muted)

        thread {
            var connection: HttpURLConnection? = null

            val result = try {
                connection =
                    URL("$BACKEND_URL/v1/sync")
                        .openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 120000
                connection.doOutput = true

                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")

                val payload = JSONObject().apply {
                    put("profile", "parent")
                    put("login", login)
                    put("password", password)
                    librusSessionMemory?.let { put("session", it) }
                }

                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode

                val responseBody =
                    if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val data = json.optJSONObject("data")

                    if (data != null) {
                        librusDataMemory = data
                        librusSessionMemory =
                            json.optJSONObject("session") ?: librusSessionMemory
                        "SUCCESS"
                    } else {
                        "ERROR|Serwer nie zwrócił pola data."
                    }
                } else {
                    val detail = try {
                        JSONObject(responseBody).optString("detail", "HTTP $responseCode")
                    } catch (_: Exception) {
                        "HTTP $responseCode"
                    }

                    "ERROR|Synchronizacja nie powiodła się ($responseCode): $detail"
                }
            } catch (e: Exception) {
                "ERROR|Błąd pobierania: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread

                if (result == "SUCCESS") {
                    if (::librusStatusText.isInitialized) {
                        librusStatusText.text = "Dane Librusa pobrane."
                        librusStatusText.setTextColor(successColor)
                    }

                    librusDataMemory?.let { showLibrusSection(section, it) }
                } else if (::librusStatusText.isInitialized) {
                    librusStatusText.text = result.substringAfter("|")
                    librusStatusText.setTextColor(errorColor)
                }
            }
        }
    }

    // NOWY WYGLĄD WSZYSTKICH PIĘCIU SEKCJI

    private fun showLibrusSection(section: String, data: JSONObject) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 20)
            setBackgroundColor(bgColor)
        }

        val subtitle = when (section) {
            "OCENY" -> "Wszystkie oceny i informacje o postępach ucznia"
            "FREKWENCJA" -> "Obecności, nieobecności i spóźnienia"
            "PLAN LEKCJI" -> "Plan zajęć"
            "WIADOMOŚCI" -> "Wiadomości oraz ogłoszenia"
            "PROFILE" -> "Dane ucznia, przedmioty, nauczyciele i klasy"
            else -> "Dane z Librusa"
        }

        root.addView(label(subtitle, 14f, muted))

        val keys = when (section) {
            "OCENY" -> listOf(
                "grades", "descriptive_grades", "partial_grades",
                "point_grades", "text_grades", "behaviour_grades",
                "grading_system"
            )

            "FREKWENCJA" -> listOf("attendances", "attendance_types")
            "PLAN LEKCJI" -> listOf("timetable")
            "WIADOMOŚCI" -> listOf("messages", "announcements")
            "PROFILE" -> listOf("me", "student", "students", "classes", "subjects", "teachers")
            else -> data.keys().asSequence().toList()
        }

        val subjects = data.optJSONObject("subjects")
        val teachers = data.optJSONObject("teachers")
        var count = 0

        for (key in keys) {
            if (!data.has(key) || data.isNull(key)) continue
            val value = data.opt(key) ?: continue
            if (value.toString() == "[]" || value.toString() == "{}") continue

            val heading = when (key) {
                "grades" -> "Oceny"
                "descriptive_grades" -> "Oceny opisowe"
                "partial_grades" -> "Oceny cząstkowe i umiejętności"
                "point_grades" -> "Oceny punktowe"
                "text_grades" -> "Oceny tekstowe"
                "behaviour_grades" -> "Oceny zachowania"
                "grading_system" -> "Skala ocen"
                "attendances" -> "Frekwencja"
                "attendance_types" -> "Rodzaje obecności"
                "timetable" -> "Plan zajęć"
                "messages" -> "Wiadomości"
                "announcements" -> "Ogłoszenia"
                "me" -> "Dane konta"
                "student" -> "Uczeń"
                "students" -> "Uczniowie"
                "classes" -> "Klasy"
                "subjects" -> "Przedmioty"
                "teachers" -> "Nauczyciele"
                else -> key.replace("_", " ").replaceFirstChar { it.uppercase() }
            }

            root.addView(sectionHeader(heading), params(0, 18, 0, 8))

            when (value) {
                is org.json.JSONArray -> {
                    if (section == "OCENY" && key in listOf(
                            "grades", "descriptive_grades", "partial_grades",
                            "point_grades", "text_grades", "behaviour_grades"
                        )
                    ) {
                        renderGradeItems(root, value, subjects, teachers)
                    } else {
                        renderGenericArray(root, value, subjects, teachers)
                    }
                }

                is JSONObject -> renderObjectCard(root, value, subjects, teachers)
                else -> root.addView(infoCard(value.toString()))
            }

            count++
        }

        if (count == 0) {
            root.addView(infoCard("Brak danych w tej sekcji. Użyj ODŚWIEŻ, aby pobrać je ponownie."))
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(root)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(section)
            .setView(scroll)
            .setPositiveButton("ZAMKNIJ", null)
            .setNeutralButton("ODŚWIEŻ", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                librusDataMemory = null
                dialog.dismiss()
                loadLibrusSection(section)
            }
        }

        dialog.show()
    }

    private fun renderGradeItems(
        parent: LinearLayout,
        items: org.json.JSONArray,
        subjects: JSONObject?,
        teachers: JSONObject?
    ) {
        if (items.length() == 0) {
            parent.addView(infoCard("Brak wpisów."))
            return
        }

        val sorted = (0 until items.length())
            .mapNotNull { items.optJSONObject(it) }
            .sortedByDescending { it.optString("date", it.optString("add_date")) }

        for (item in sorted) {
            val sid = item.opt("subject_id")?.toString()
            val subject = sid?.let { subjects?.optString(it) }
                ?.takeIf { !it.isNullOrBlank() && it != "null" }
                ?: "Przedmiot"

            val skill = item.optString("skill")
                .takeIf { it.isNotBlank() && it != "null" }

            val value = item.optString("value").ifBlank { "—" }

            val date = item.optString("date")
                .takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("add_date").take(10)

            val comments = item.optJSONArray("comments")
            val requirements = item.optJSONArray("requirements")

            val tid = item.optString("teacher_id")
                .takeIf { it.isNotBlank() && it != "null" }

            val teacher = tid?.let { teachers?.optString(it) }
                ?.takeIf { !it.isNullOrBlank() && it != "null" }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundColor(panel)
            }

            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val grade = TextView(this).apply {
                text = value
                textSize = 25f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(
                    if (value.startsWith("6") || value.startsWith("5") || value.startsWith("+5"))
                        successColor else white
                )
            }

            top.addView(grade, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(label(date, 12f, muted))
            card.addView(top)
            card.addView(label(subject, 15f, white, true))

            if (skill != null) card.addView(label(skill, 14f, muted))
            if (teacher != null) card.addView(label("Nauczyciel: $teacher", 12f, muted))

            if (comments != null) {
                for (i in 0 until comments.length()) {
                    val c = comments.optString(i)
                    if (c.isNotBlank() && c != "null") {
                        card.addView(label(c, 13f, white))
                    }
                }
            }

            if (requirements != null) {
                for (i in 0 until requirements.length()) {
                    val q = requirements.optString(i)
                    if (q.isNotBlank() && q != "null") {
                        card.addView(label("Wymaganie: $q", 12f, muted))
                    }
                }
            }

            parent.addView(card, params(0, 0, 0, 9))
        }
    }

    private fun renderGenericArray(
        parent: LinearLayout,
        items: org.json.JSONArray,
        subjects: JSONObject?,
        teachers: JSONObject?
    ) {
        if (items.length() == 0) {
            parent.addView(infoCard("Brak wpisów."))
            return
        }

        for (i in 0 until items.length()) {
            val item = items.opt(i)
            when (item) {
                is JSONObject -> renderObjectCard(parent, item, subjects, teachers)
                null -> Unit
                else -> parent.addView(infoCard(readableValue(item)))
            }
        }
    }

    private fun renderObjectCard(
        parent: LinearLayout,
        obj: JSONObject,
        subjects: JSONObject?,
        teachers: JSONObject?
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 11, 15, 11)
            setBackgroundColor(panel)
        }

        val preferred = listOf(
            "subject_id", "name", "title", "subject", "date",
            "start", "start_time", "time", "value", "type",
            "status", "description", "content", "message",
            "teacher_id", "class_id", "room", "lesson"
        )

        val keys = preferred.filter { obj.has(it) } +
            obj.keys().asSequence().filter { it !in preferred }.toList()

        for (key in keys) {
            val raw = obj.opt(key) ?: continue
            if (raw == JSONObject.NULL) continue

            val value = when (key) {
                "subject_id" -> subjects?.optString(raw.toString()) ?: raw.toString()
                "teacher_id" -> teachers?.optString(raw.toString()) ?: raw.toString()
                else -> readableValue(raw)
            }

            if (value.isBlank() || value == "null" || value == "[]") continue

            val name = key.replace("_", " ").replaceFirstChar { it.uppercase() }

            card.addView(
                label(
                    "$name: $value",
                    13f,
                    white,
                    key in listOf("name", "title", "value", "subject_id")
                )
            )
        }

        parent.addView(card, params(0, 0, 0, 8))
    }

    private fun readableValue(value: Any): String = when (value) {
        is JSONObject -> value.keys().asSequence().joinToString(" • ") {
            "$it: ${readableValue(value.opt(it) ?: "")}"
        }

        is org.json.JSONArray -> (0 until value.length()).joinToString(", ") {
            readableValue(value.opt(it) ?: "")
        }

        else -> value.toString()
    }

    private fun sectionHeader(title: String): TextView =
        TextView(this).apply {
            text = title.uppercase()
            textSize = 13f
            setTextColor(successColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(2, 8, 2, 5)
        }

    private fun infoCard(message: String): TextView =
        TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(white)
            setPadding(16, 14, 16, 14)
            setBackgroundColor(panel)
        }

    private fun checkBackendConnection() {
        if (!::backendStatusText.isInitialized) return

        backendStatusText.text =
            "Sprawdzam sesję Supabase i połączenie z serwerem…"
        backendStatusText.setTextColor(muted)

        val accessToken = supabase.auth.currentAccessTokenOrNull()

        if (accessToken.isNullOrBlank()) {
            backendStatusText.text =
                "Brak aktywnego tokenu. Zaloguj się ponownie."
            backendStatusText.setTextColor(errorColor)
            return
        }

        thread {
            var connection: HttpURLConnection? = null

            val result = try {
                connection =
                    URL("$BACKEND_URL/v1/session-check")
                        .openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 20000
                connection.readTimeout = 20000

                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode

                val responseBody =
                    if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val authenticated = json.optBoolean("authenticated", false)
                    val userId = json.optString("user_id", "")

                    if (json.optBoolean("ok", false) &&
                        authenticated &&
                        userId.isNotBlank()
                    ) {
                        "SUCCESS|Serwer potwierdził Twoje konto Supabase.\nID użytkownika: $userId"
                    } else {
                        "ERROR|Serwer odpowiedział, ale nie potwierdził użytkownika."
                    }
                } else {
                    val message = try {
                        JSONObject(responseBody).optString("detail", "Kod HTTP $responseCode")
                    } catch (_: Exception) {
                        "Kod HTTP $responseCode"
                    }

                    "ERROR|Błąd serwera ($responseCode): $message"
                }
            } catch (e: Exception) {
                "ERROR|Nie udało się połączyć: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                if (!isFinishing && ::backendStatusText.isInitialized) {
                    val successful = result.startsWith("SUCCESS|")
                    backendStatusText.text = result.substringAfter("|")
                    backendStatusText.setTextColor(
                        if (successful) successColor else errorColor
                    )
                }
            }
        }
    }

    private fun addHomeSection(
        parent: LinearLayout,
        heading: String,
        description: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            setBackgroundColor(panel)
            isClickable = true
            isFocusable = true
        }

        card.addView(label(heading, 14f, white, true))
        card.addView(label(description, 13f, muted))

        card.setOnClickListener {
            loadLibrusSection(heading)
        }

        parent.addView(card, params(0, 10, 0, 0))
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)

        if (bold) {
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        setPadding(0, 8, 0, 8)
    }

    private fun params(l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(
            -1,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(l, t, r, b)
        }

    private fun authenticate(register: Boolean) {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            statusText.text = "Podaj prawidłowy adres e-mail."
            return
        }

        if (password.length < 10) {
            statusText.text = "Hasło musi mieć co najmniej 10 znaków."
            return
        }

        statusText.text = if (register) "Tworzę konto…" else "Loguję…"

        thread {
            val result = try {
                runBlocking {
                    if (register) {
                        supabase.auth.signUpWith(
                            Email,
                            redirectUrl = AUTH_REDIRECT
                        ) {
                            this.email = email
                            this.password = password
                        }

                        "Konto utworzone. Sprawdź pocztę i potwierdź adres e-mail."
                    } else {
                        supabase.auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }

                        "Logowanie przyjęte. Otwieram ekran główny…"
                    }
                }
            } catch (e: Exception) {
                "Błąd: ${e.message ?: e.javaClass.simpleName}"
            }

            runOnUiThread {
                if (!isFinishing) {
                    statusText.text = result

                    if (!register) {
                        val session = supabase.auth.currentSessionOrNull()

                        if (session != null) {
                            showHome(session.user?.email.orEmpty())
                        }
                    }
                }
            }
        }
    }

    private fun signOut() {
        thread {
            val result = try {
                runBlocking {
                    supabase.auth.signOut()
                }
                null
            } catch (e: Exception) {
                e.message ?: "Nie udało się wylogować."
            }

            runOnUiThread {
                if (!isFinishing && result != null) {
                    showHome(
                        supabase.auth.currentSessionOrNull()
                            ?.user?.email.orEmpty()
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}

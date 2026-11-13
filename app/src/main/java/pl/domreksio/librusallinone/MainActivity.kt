package pl.domreksio.librusallinone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.util.Base64
import android.webkit.MimeTypeMap
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

private const val SUPABASE_URL =
    "https://alegkhpqxdzuqjuuoawp.supabase.co"

private const val SUPABASE_PUBLISHABLE_KEY =
    "sb_publishable_gchAnvMxlFgvzdenD0QqWg_YoOtsTPe"

private const val AUTH_REDIRECT =
    "pl.domreksio.librusallinone://login-callback"

internal const val BACKEND_URL =
    "https://librus-all-in-one-api.onrender.com"

internal val supabase = createSupabaseClient(
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

    private lateinit var librusVault: LibrusCredentialVault

    private var librusLoginMemory: String? = null
    private var librusPasswordMemory: String? = null
    private var librusSessionMemory: JSONObject? = null
    private var librusDataMemory: JSONObject? = null

    private var syncInProgress = false
    private var timetableTicker: Runnable? = null
    private var autoRestoreStarted = false
    private var pendingNotificationSection: String? = null

    private val white = Color.rgb(242, 245, 250)
    private val muted = Color.rgb(155, 169, 190)
    private val panel = Color.rgb(25, 34, 51)
    private val panelLight = Color.rgb(34, 45, 65)
    private val bgColor = Color.rgb(13, 18, 30)
    private val successColor = Color.rgb(100, 210, 140)
    private val errorColor = Color.rgb(255, 125, 125)
    private val uiPrefs by lazy { getSharedPreferences("librus_ui_settings", MODE_PRIVATE) }
    private val accentColor: Int
        get() = when (uiPrefs.getString("accent", "blue")) {
            "teal" -> Color.rgb(32, 174, 160)
            "violet" -> Color.rgb(145, 112, 220)
            "amber" -> Color.rgb(224, 156, 58)
            else -> Color.rgb(105, 165, 255)
        }
    private val warningColor = Color.rgb(255, 190, 95)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        librusVault = LibrusCredentialVault(applicationContext)
        pendingNotificationSection = intent?.getStringExtra("open_librus_section")
        requestNotificationPermissionIfNeeded()
        showLogin("Sprawdzam sesję konta…")

        activityScope.launch {
            supabase.auth.sessionStatus.collect { state ->
                when (state) {
                    is SessionStatus.Authenticated ->
                        showHome(state.session.user?.email.orEmpty())

                    SessionStatus.Initializing ->
                        showLogin("Sprawdzam zapisane konto…")

                    is SessionStatus.NotAuthenticated ->
                        showLogin("Zaloguj się, aby otworzyć aplikację.")

                    is SessionStatus.RefreshFailure ->
                        showLogin("Sesja wygasła. Zaloguj się ponownie.")
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

    // ---------------------------------------------------------
    // LOGOWANIE DO APLIKACJI
    // ---------------------------------------------------------

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
                15f,
                muted
            )
        )

        emailInput = inputField(
            "Adres e-mail",
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        content.addView(emailInput, params(0, 18, 0, 8))

        passwordInput = inputField(
            "Hasło do konta aplikacji",
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        content.addView(passwordInput, params(0, 0, 0, 12))

        content.addView(actionButton("ZAREJESTRUJ KONTO") {
            authenticate(true)
        }, params(0, 4, 0, 6))

        content.addView(actionButton("ZALOGUJ SIĘ") {
            authenticate(false)
        }, params(0, 4, 0, 6))

        statusText = label(message, 14f, muted)
        content.addView(statusText, params(0, 14, 0, 4))

        content.addView(
            label(
                "Po rejestracji potwierdź adres e-mail. Po zalogowaniu otworzy się ekran główny.",
                13f,
                muted
            )
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(content)
        })
    }

    // ---------------------------------------------------------
    // EKRAN GŁÓWNY
    // ---------------------------------------------------------

    private fun showHome(email: String) {
        timetableTicker?.let { android.os.Handler(mainLooper).removeCallbacks(it) }
        timetableTicker = null
        if (isFinishing) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 30)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(15, 24, 42), bgColor, Color.rgb(10, 15, 26)))
        }

        val welcomeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(28, 57, 104), Color.rgb(25, 34, 58), Color.rgb(22, 31, 48))
            ).apply { cornerRadius = 24f * resources.displayMetrics.density }
            elevation = 5f * resources.displayMetrics.density
            alpha = 0f
            translationY = 18f * resources.displayMetrics.density
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandMark = TextView(this).apply {
            text = "14"
            textSize = 23f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(7, 27, 73))
            background = roundedBackground(Color.WHITE, 13)
            setPadding(10, 5, 10, 5)
        }
        brandRow.addView(brandMark, LinearLayout.LayoutParams(48, 48))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 0, 0, 0)
        }
        brandText.addView(label("SZKOŁA PODSTAWOWA NR 14", 11f, Color.rgb(190, 211, 245), true))
        brandText.addView(label("im. Polskich Olimpijczyków • Krosno", 11f, muted))
        brandRow.addView(brandText)
        welcomeCard.addView(brandRow)
        welcomeCard.addView(label("Witaj!", 32f, white, true), params(0, 14, 0, 0))
        welcomeCard.addView(label("Zalogowano jako: $email", 14f, Color.rgb(210, 220, 237)))
        welcomeCard.addView(label("Twoje szkolne sprawy w jednym miejscu.", 14f, Color.rgb(130, 220, 180)))
        content.addView(welcomeCard, params(0, 0, 0, 16))
        welcomeCard.animate().alpha(1f).translationY(0f).setDuration(520).start()

        val savedLibrus = librusVault.load()

        if (savedLibrus != null) {
            librusLoginMemory = savedLibrus.login
            librusPasswordMemory = savedLibrus.password

            librusSessionMemory = savedLibrus.sessionJson?.let {
                try {
                    JSONObject(it)
                } catch (_: Exception) {
                    null
                }
            }
        }

        val connectionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(29, 48, 73), Color.rgb(22, 33, 51))
            ).apply {
                cornerRadius = 20f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(58, 83, 119))
            }
        }
        val connectionTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        connectionTop.addView(label("STATUS POŁĄCZENIA", 11f, Color.rgb(180, 205, 239), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val statusPill = TextView(this).apply {
            text = if (savedLibrus != null) "● ZAPISANO" else "○ NIEPOŁĄCZONO"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (savedLibrus != null) successColor else warningColor)
            setPadding(10, 6, 10, 6)
            background = roundedBackground(Color.rgb(39, 55, 73), 20)
        }
        connectionTop.addView(statusPill)
        connectionCard.addView(connectionTop)
        backendStatusText = label(
            if (savedLibrus != null) "Dane logowania Librus są zapisane na tym urządzeniu." else "Połącz konto Librus, aby pobierać dane szkolne.",
            13f, Color.rgb(205, 217, 235)
        )
        connectionCard.addView(backendStatusText, params(0, 8, 0, 0))
        connectionCard.addView(actionButton("SPRAWDŹ SERWER") {
            checkBackendConnection()
        }, params(0, 12, 0, 0))
        content.addView(connectionCard, params(0, 2, 0, 14))

        content.addView(sectionHeader("KONTO LIBRUS"), params(0, 12, 0, 5))
        content.addView(
            label(
                "Wybierz konto rodzica i podaj dane logowania do Librus Synergia.",
                14f,
                muted
            )
        )

        librusLoginInput = inputField("Login do Librusa", InputType.TYPE_CLASS_TEXT)
        content.addView(librusLoginInput, params(0, 12, 0, 8))
        savedLibrus?.let { librusLoginInput.setText(it.login) }

        librusPasswordInput = inputField(
            "Hasło do Librusa",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        content.addView(librusPasswordInput, params(0, 0, 0, 8))

        librusConnectButton = actionButton("POŁĄCZ Z LIBRUSEM") {
            connectLibrus()
        }
        content.addView(librusConnectButton, params(0, 4, 0, 8))

        librusStatusText = label(
            if (savedLibrus != null)
                "Znaleziono zapisane połączenie z Librusem."
            else
                "Konto Librus nie zostało jeszcze podłączone.",
            14f,
            muted
        )
        content.addView(librusStatusText)

        content.addView(
            label(
                "Dane logowania są przesyłane do Twojego backendu przez HTTPS. Hasło jest przechowywane lokalnie w postaci zaszyfrowanej.",
                12f,
                muted
            ),
            params(0, 4, 0, 12)
        )

        content.addView(sectionHeader("CENTRUM SZKOLNE"), params(0, 18, 0, 4))
        content.addView(label("Wybierz moduł, aby przejść do danych z dziennika.", 13f, muted), params(0, 0, 0, 10))

        val quickAccess = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun quickTile(title: String, caption: String, emoji: String, tint: Int, section: String) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(13, 14, 12, 14)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(tint, Color.rgb(25, 36, 55))
                ).apply {
                    cornerRadius = 18f * resources.displayMetrics.density
                    setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(59, 78, 106))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { loadLibrusSection(section) }
            }
            tile.addView(label(emoji, 22f, white, true))
            tile.addView(label(title, 13f, white, true), params(0, 8, 0, 1))
            tile.addView(label(caption, 10f, Color.rgb(187, 201, 222)))
            quickAccess.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (quickAccess.childCount == 0) marginEnd = (6 * resources.displayMetrics.density).toInt()
                else marginStart = (6 * resources.displayMetrics.density).toInt()
            })
        }
        quickTile("Oceny", "Postępy ucznia", "✦", Color.rgb(40, 78, 126), "OCENY")
        quickTile("Plan", "Lekcje i godziny", "▦", Color.rgb(47, 78, 100), "PLAN LEKCJI")
        quickTile("Frekwencja", "Obecności", "◷", Color.rgb(67, 66, 112), "FREKWENCJA")
        content.addView(quickAccess, params(0, 0, 0, 12))

        addHomeSection(content, "PROFILE", "Profile rodzica i ucznia", "👤")
        addHomeSection(content, "OCENY", "Oceny i postępy ucznia", "📊")
        addHomeSection(content, "FREKWENCJA", "Obecności, nieobecności i spóźnienia", "📅")
        addHomeSection(content, "PLAN LEKCJI", "Tygodniowy harmonogram zajęć", "🗓")
        addHomeSection(content, "WIADOMOŚCI", "Wiadomości i ogłoszenia", "✉")
        addHomeSection(content, "ZADANIA DOMOWE", "Zadania, terminy i załączniki", "📚")
        addHomeSection(content, "TERMINARZ", "Sprawdziany, wydarzenia i wycieczki", "🗓")
        addHomeSection(content, "UWAGI", "Uwagi pozytywne, neutralne i negatywne", "📝")
        addHomeSection(content, "SZCZĘŚLIWY NUMEREK", "Numer obowiązujący danego dnia", "🍀")

        content.addView(actionButton("USTAWIENIA POWIADOMIEŃ") {
            showNotificationSettings()
        }, params(0, 12, 0, 8))

        content.addView(actionButton("WYLOGUJ") {
            signOut()
        }, params(0, 24, 0, 8))

        val homeScroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(content)
        }
        val homePage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        homePage.addView(homeScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        homePage.addView(buildBottomNavigation("START"))
        setContentView(homePage)

        if (savedLibrus != null) LibrusNotificationScheduler.schedule(applicationContext)

        if (savedLibrus != null && !autoRestoreStarted) {
            autoRestoreStarted = true
            librusStatusText.text = "Przywrócono zapisane połączenie. Synchronizuję dane…"
            val requestedSection = pendingNotificationSection ?: "PROFILE"
            pendingNotificationSection = null
            loadLibrusSection(requestedSection)
        }
    }

    // ---------------------------------------------------------
    // POŁĄCZENIE Z LIBRUSEM
    // ---------------------------------------------------------

    private fun connectLibrus() {
        val login = librusLoginInput.text.toString().trim()
        val password = librusPasswordInput.text.toString()

        if (login.isBlank()) {
            setLibrusStatus("Podaj login do Librusa.", false)
            return
        }

        if (password.isBlank()) {
            setLibrusStatus("Podaj hasło do Librusa.", false)
            return
        }

        val accessToken = supabase.auth.currentAccessTokenOrNull()

        if (accessToken.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji aplikacji. Zaloguj się ponownie.", false)
            return
        }

        librusConnectButton.isEnabled = false
        setLibrusStatus("Łączę się z Librusem. To może chwilę potrwać…", null)

        thread {
            var connection: HttpURLConnection? = null

            val result = try {
                connection = URL("$BACKEND_URL/v1/connect")
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

                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val body = readResponse(connection, code)

                if (code in 200..299) {
                    val json = JSONObject(body)

                    if (json.has("student") && json.has("session")) {
                        librusLoginMemory = login
                        librusPasswordMemory = password
                        librusSessionMemory = json.optJSONObject("session")
                        librusDataMemory = null

                        librusVault.save(login, password, librusSessionMemory?.toString())
                        LibrusNotificationScheduler.schedule(applicationContext)

                        "SUCCESS|Połączono z Librusem. Dane można teraz pobrać."
                    } else {
                        "ERROR|Serwer nie zwrócił profilu lub sesji Librusa."
                    }
                } else {
                    val detail = errorDetail(body, "Kod HTTP $code")
                    "ERROR|Błąd połączenia ($code): $detail"
                }
            } catch (e: Exception) {
                "ERROR|Nie udało się połączyć: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread

                val ok = result.startsWith("SUCCESS|")
                setLibrusStatus(result.substringAfter("|"), ok)
                librusConnectButton.isEnabled = true
                librusPasswordInput.text.clear()
            }
        }
    }

    // ---------------------------------------------------------
    // SYNCHRONIZACJA
    // ---------------------------------------------------------

    private fun loadLibrusSection(section: String) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()

        if (login.isNullOrBlank() || password.isNullOrBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Brak połączenia z Librusem")
                .setMessage("Najpierw połącz konto Librus.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (syncInProgress) return

        librusDataMemory?.let {
            showLibrusSection(section, it)
            return
        }

        if (token.isNullOrBlank()) {
            setLibrusStatus("Sesja aplikacji wygasła. Zaloguj się ponownie.", false)
            return
        }

        syncInProgress = true
        setLibrusStatus("Pobieram dane z Librusa…", null)

        thread {
            var connection: HttpURLConnection? = null

            val result = try {
                connection = URL("$BACKEND_URL/v1/sync")
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

                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val body = readResponse(connection, code)

                if (code in 200..299) {
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data")

                    if (data != null) {
                        librusDataMemory = data
                        librusSessionMemory =
                            json.optJSONObject("session") ?: librusSessionMemory
                        librusVault.updateSession(librusSessionMemory?.toString())
                        "SUCCESS"
                    } else {
                        "ERROR|Serwer nie zwrócił pola data."
                    }
                } else {
                    "ERROR|Synchronizacja nie powiodła się ($code): ${errorDetail(body, "HTTP $code")}"
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
                    setLibrusStatus("Dane Librusa pobrane.", true)
                    librusDataMemory?.let { showLibrusSection(section, it) }
                } else {
                    setLibrusStatus(result.substringAfter("|"), false)
                }
            }
        }
    }

    // ---------------------------------------------------------
    // WYŚWIETLANIE SEKCJI
    // ---------------------------------------------------------

    private fun showLibrusSection(section: String, data: JSONObject) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 22)
            setBackgroundColor(bgColor)
        }

        val subtitle = when (section) {
            "OCENY" -> "Oceny i postępy ucznia"
            "FREKWENCJA" -> "Obecności, nieobecności i spóźnienia"
            "PLAN LEKCJI" -> "Harmonogram zajęć"
            "WIADOMOŚCI" -> "Wiadomości oraz ogłoszenia"
            "ZADANIA DOMOWE" -> "Zadania domowe i materiały"
            "TERMINARZ" -> "Sprawdziany, wydarzenia i wycieczki"
            "UWAGI" -> "Uwagi dotyczące zachowania"
            "SZCZĘŚLIWY NUMEREK" -> "Szczęśliwy numerek"
            "PROFILE" -> "Dane ucznia, przedmioty i nauczyciele"
            else -> "Dane z Librusa"
        }

        val density = resources.displayMetrics.density
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 18)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(35, 78, 139), Color.rgb(27, 43, 70), Color.rgb(24, 34, 53))
            ).apply {
                cornerRadius = 24f * density
                setStroke((1 * density).toInt(), Color.rgb(73, 105, 151))
            }
            elevation = 4f * density
        }
        hero.addView(label("LIBRUS  /  CENTRUM UCZNIA", 10f, Color.rgb(181, 211, 255), true))
        hero.addView(label(section, 25f, white, true), params(0, 5, 0, 0))
        hero.addView(label(subtitle, 13f, Color.rgb(202, 215, 235)))
        root.addView(hero, params(0, 8, 0, 16))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = actionButton("‹  PULPIT") {
            showHome(supabase.auth.currentSessionOrNull()?.user?.email.orEmpty())
        }
        val refresh = actionButton("ODŚWIEŻ  ↻") {
            librusDataMemory = null
            loadLibrusSection(section)
        }
        actions.addView(back, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (6 * density).toInt() })
        actions.addView(refresh, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (6 * density).toInt() })
        root.addView(actions, params(0, 0, 0, 14))

        val subjects = data.optJSONObject("subjects")
        val teachers = data.optJSONObject("teachers")
        val classrooms = data.optJSONObject("classrooms")

        when (section) {
            "PLAN LEKCJI" -> renderTimetable(root, data, subjects, teachers, classrooms)
            "OCENY" -> renderGradesSection(root, data, subjects, teachers)
            "FREKWENCJA" -> renderAttendanceSection(root, data)
            "WIADOMOŚCI" -> renderMessagesSection(root, data)
            "ZADANIA DOMOWE" -> {
                val assignments = data.optJSONArray("homework_assignments")
                if (assignments != null && assignments.length() > 0) {
                    renderFeatureArray(root, data, listOf("homework_assignments"), "Zadania domowe")
                } else {
                    renderFeatureArray(root, data, listOf("homework", "homeworks"), "Zadania domowe")
                }
            }
            "TERMINARZ" -> renderFeatureArray(root, data, listOf("agenda", "events"), "Wydarzenia i terminy")
            "UWAGI" -> renderFeatureArray(root, data, listOf("notes", "behaviour_notes"), "Uwagi")
            "SZCZĘŚLIWY NUMEREK" -> renderLuckyNumber(root, data)
            "PROFILE" -> renderProfileSection(root, data)
            else -> root.addView(infoCard("Nieznana sekcja."))
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            clipToPadding = false
            setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
            addView(root)
        }
        val sectionPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        sectionPage.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        sectionPage.addView(buildBottomNavigation(section))
        setContentView(sectionPage)
        if (uiPrefs.getBoolean("motion", true)) {
            root.alpha = 0f
            root.translationY = 12f * resources.displayMetrics.density
            root.animate().alpha(1f).translationY(0f).setDuration(280).start()
        }
    }

    // ---------------------------------------------------------
    // PLAN LEKCJI
    // ---------------------------------------------------------

    private data class TimetableLesson(
        val date: String,
        val number: Int,
        val from: String,
        val to: String,
        val subject: String,
        val teacher: String,
        val classroom: String,
        val canceled: Boolean,
        val substitution: Boolean,
        val note: String
    )

    private fun renderTimetable(
        parent: LinearLayout,
        data: JSONObject,
        subjects: JSONObject?,
        teachers: JSONObject?,
        classrooms: JSONObject?
    ) {
        val raw = data.opt("timetable")

        if (raw == null || raw == JSONObject.NULL) {
            parent.addView(infoCard("Brak danych planu lekcji."))
            return
        }

        val lessons = mutableListOf<TimetableLesson>()

        fun addLesson(date: String, obj: JSONObject) {
            val number = obj.optInt("lesson_no", 0)
            val from = firstValue(obj, "hour_from", "start", "start_time")
            val to = firstValue(obj, "hour_to", "end", "end_time")

            val subjectId = firstValue(obj, "subject_id", "subject")
            val teacherId = firstValue(obj, "teacher_id", "teacher")
            val roomId = firstValue(obj, "classroom_id", "classroom", "room")

            val subject = lookupName(subjects, subjectId)
                .ifBlank { obj.optString("subject_name").ifBlank { subjectId } }

            val teacher = lookupName(teachers, teacherId)
                .ifBlank { obj.optString("teacher_name").ifBlank { teacherId } }

            val room = lookupName(classrooms, roomId)
                .ifBlank { obj.optString("classroom_name").ifBlank { roomId } }

            val canceled = obj.optBoolean("is_canceled", false)
            val substitution = obj.optBoolean("is_substitution", false)
            val note = firstValue(obj, "substitution_note", "note", "description")

            lessons.add(
                TimetableLesson(
                    date = date,
                    number = number,
                    from = from,
                    to = to,
                    subject = subject.ifBlank { "Przedmiot" },
                    teacher = teacher,
                    classroom = room,
                    canceled = canceled,
                    substitution = substitution,
                    note = note
                )
            )
        }

        fun parseDay(date: String, value: Any?) {
            when (value) {
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        value.optJSONObject(i)?.let { addLesson(date, it) }
                    }
                }

                is JSONObject -> {
                    if (value.has("lesson_no") || value.has("hour_from")) {
                        addLesson(date, value)
                    } else {
                        val nested = value.optJSONArray("lessons")
                            ?: value.optJSONArray("items")
                        if (nested != null) {
                            parseDay(date, nested)
                        } else {
                            val iterator = value.keys()
                            while (iterator.hasNext()) {
                                val key = iterator.next()
                                val child = value.opt(key)
                                if (child is JSONObject || child is JSONArray) {
                                    parseDay(date, child)
                                }
                            }
                        }
                    }
                }
            }
        }

        when (raw) {
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val item = raw.optJSONObject(i) ?: continue
                    val date = firstValue(item, "date", "lesson_date", "day")
                    val nested = item.optJSONArray("lessons")
                        ?: item.optJSONArray("items")

                    if (nested != null) {
                        parseDay(date, nested)
                    } else {
                        addLesson(date, item)
                    }
                }
            }

            is JSONObject -> {
                val iterator = raw.keys()
                while (iterator.hasNext()) {
                    val date = iterator.next()
                    parseDay(date, raw.opt(date))
                }
            }

            else -> Unit
        }

        if (lessons.isEmpty()) {
            parent.addView(
                infoCard(
                    "Nie udało się odczytać struktury planu lekcji. Dane zostały pobrane, ale ich format wymaga dopasowania."
                )
            )
            return
        }

        lessons.sortWith(
            compareBy<TimetableLesson> { it.date }
                .thenBy { it.from.ifBlank { "99:99" } }
                .thenBy { it.number }
        )

        val grouped = lessons.groupBy { it.date }.toSortedMap()
        val dates = grouped.keys.toList()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .format(java.util.Date())
        // Wybierz dzisiaj, a gdy nie ma go w planie — najbliższy przyszły dzień.
        // Jeśli cały pobrany plan jest z przeszłości, pokaż ostatni dostępny dzień.
        var selectedDate = dates.firstOrNull { it == today }
            ?: dates.firstOrNull { it > today }
            ?: dates.last()

        parent.addView(
            infoCard("Znaleziono ${lessons.size} lekcji w ${grouped.size} dniach. Wybierz dzień, aby zobaczyć szczegóły planu.")
        )
        parent.addView(sectionHeader("PLAN TYGODNIA"), params(0, 14, 0, 8))

        val daySelector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dayScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(daySelector)
        }
        val dayButtons = linkedMapOf<String, TextView>()

        fun refreshDaySelection() {
            dayButtons.forEach { (date, button) ->
                val selected = date == selectedDate
                button.background = roundedBackground(
                    if (selected) Color.rgb(47, 91, 148) else panelLight,
                    12
                )
                button.setTextColor(if (selected) white else muted)
            }
        }

        val dayContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun renderSelectedDay(date: String) {
            timetableTicker?.let { android.os.Handler(mainLooper).removeCallbacks(it) }
            timetableTicker = null
            dayContent.removeAllViews()
            val dayLessons = grouped[date] ?: return
            val dayCard = card()
            dayCard.addView(label(dateLabel(date), 18f, white, true))
            dayCard.addView(
                label("${dayLessons.size} zaplanowanych lekcji", 12f, muted)
            )

            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
            if (date == todayKey) {
                val livePanel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(),
                        (12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt())
                    background = roundedBackground(Color.rgb(23, 48, 72), 14)
                }
                val liveHeader = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val bellIcon = label("🔔", 21f, warningColor, true)
                liveHeader.addView(bellIcon, LinearLayout.LayoutParams((36 * resources.displayMetrics.density).toInt(), -2))
                val liveStatus = label("Sprawdzam aktualną lekcję…", 14f, white, true)
                liveHeader.addView(liveStatus, LinearLayout.LayoutParams(0, -2, 1f))
                livePanel.addView(liveHeader)
                val liveDetail = label("", 12f, Color.rgb(190, 210, 232))
                livePanel.addView(liveDetail, params(0, 2, 0, 4))
                val liveProgress = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progressTintList = android.content.res.ColorStateList.valueOf(accentColor)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(55, 73, 94))
                }
                livePanel.addView(liveProgress, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (5 * resources.displayMetrics.density).toInt()
                ))
                val bellSync = TextView(this).apply {
                    text = "🔔  SYNCHRONIZUJ Z DZWONKIEM"
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.rgb(170, 207, 255))
                    setPadding(0, (9 * resources.displayMetrics.density).toInt(), 0, 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val choices = mutableListOf<Pair<String, Int>>()
                        dayLessons.filterNot { it.canceled }.forEach { item ->
                            val startMinute = item.from.split(":").takeIf { it.size >= 2 }
                                ?.let { (it[0].toIntOrNull() ?: -1) * 60 + (it[1].toIntOrNull() ?: -1) }
                                ?: -1
                            val endMinute = item.to.split(":").takeIf { it.size >= 2 }
                                ?.let { (it[0].toIntOrNull() ?: -1) * 60 + (it[1].toIntOrNull() ?: -1) }
                                ?: -1
                            if (startMinute in 0..1439) choices.add("Początek: ${item.subject} (${item.from})" to startMinute)
                            if (endMinute in 0..1439) choices.add("Koniec: ${item.subject} (${item.to})" to endMinute)
                        }
                        if (choices.isEmpty()) {
                            Toast.makeText(this@MainActivity, "Brak godzin lekcji do synchronizacji.", Toast.LENGTH_SHORT).show()
                        } else {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Kiedy zadzwonił dzwonek?")
                                .setItems(choices.map { it.first }.toTypedArray()) { _, which ->
                                    val now = java.util.Calendar.getInstance()
                                    val actual = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                                    val offset = choices[which].second - actual
                                    uiPrefs.edit().putInt("bell_offset_minutes", offset).apply()
                                    Toast.makeText(this@MainActivity,
                                        "Zsynchronizowano dzwonek (${if (offset >= 0) "+" else ""}${offset} min).",
                                        Toast.LENGTH_LONG).show()
                                }
                                .setNegativeButton("Anuluj", null)
                                .show()
                        }
                    }
                }
                livePanel.addView(bellSync)
                dayCard.addView(livePanel, params(0, 10, 0, 2))

                var previousLiveState = ""
                val handler = android.os.Handler(mainLooper)
                val ticker = object : Runnable {
                    override fun run() {
                        val now = java.util.Calendar.getInstance()
                        val actualMinute = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                        val correctedMinute = (actualMinute + uiPrefs.getInt("bell_offset_minutes", 0)).let {
                            ((it % 1440) + 1440) % 1440
                        }
                        fun minuteOf(value: String): Int? {
                            val match = Regex("^(\\d{1,2}):(\\d{2})").find(value.trim()) ?: return null
                            val hour = match.groupValues[1].toIntOrNull() ?: return null
                            val minute = match.groupValues[2].toIntOrNull() ?: return null
                            return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
                        }
                        val activeLesson = dayLessons.firstOrNull { item ->
                            !item.canceled && (minuteOf(item.from)?.let { it <= correctedMinute } == true) &&
                                (minuteOf(item.to)?.let { correctedMinute < it } == true)
                        }
                        val nextLesson = dayLessons.filterNot { it.canceled }
                            .filter { (minuteOf(it.from) ?: -1) > correctedMinute }
                            .minByOrNull { minuteOf(it.from) ?: Int.MAX_VALUE }
                        val previousLesson = dayLessons.filterNot { it.canceled }
                            .filter { (minuteOf(it.to) ?: Int.MAX_VALUE) <= correctedMinute }
                            .maxByOrNull { minuteOf(it.to) ?: -1 }

                        val stateKey: String
                        when {
                            activeLesson != null -> {
                                val endMinute = minuteOf(activeLesson.to) ?: correctedMinute
                                val remaining = (endMinute - correctedMinute).coerceAtLeast(0)
                                stateKey = "lesson-${activeLesson.number}"
                                liveStatus.text = "TERAZ: ${activeLesson.subject}"
                                liveDetail.text = "Lekcja ${activeLesson.number} • ${activeLesson.from}–${activeLesson.to} • koniec za ${remaining} min"
                                val startMinute = minuteOf(activeLesson.from) ?: correctedMinute
                                val duration = (endMinute - startMinute).coerceAtLeast(1)
                                liveProgress.progress = (((correctedMinute - startMinute).toFloat() / duration) * 100).toInt().coerceIn(0, 100)
                            }
                            nextLesson != null && previousLesson != null -> {
                                val startMinute = minuteOf(nextLesson.from) ?: correctedMinute
                                val endMinute = minuteOf(previousLesson.to) ?: correctedMinute
                                val remaining = (startMinute - correctedMinute).coerceAtLeast(0)
                                stateKey = "break-${nextLesson.number}"
                                liveStatus.text = "PRZERWA"
                                liveDetail.text = "Do lekcji ${nextLesson.number} (${nextLesson.subject}) zostało ${remaining} min"
                                liveProgress.progress = if (startMinute > endMinute)
                                    (((correctedMinute - endMinute).toFloat() / (startMinute - endMinute)) * 100).toInt().coerceIn(0, 100)
                                else 0
                            }
                            nextLesson != null -> {
                                val startMinute = minuteOf(nextLesson.from) ?: correctedMinute
                                stateKey = "before"
                                liveStatus.text = "PRZED LEKCJAMI"
                                liveDetail.text = "Pierwsza lekcja: ${nextLesson.subject} o ${nextLesson.from} • za ${(startMinute - correctedMinute).coerceAtLeast(0)} min"
                                liveProgress.progress = 0
                            }
                            else -> {
                                stateKey = "after"
                                liveStatus.text = "KONIEC LEKCJI NA DZIŚ"
                                liveDetail.text = "Plan na dziś został zrealizowany."
                                liveProgress.progress = 100
                            }
                        }
                        if (stateKey != previousLiveState && uiPrefs.getBoolean("motion", true)) {
                            bellIcon.animate().cancel()
                            bellIcon.rotation = -10f
                            bellIcon.animate().rotation(10f).setDuration(130)
                                .withEndAction { bellIcon.animate().rotation(0f).setDuration(130).start() }
                                .start()
                        }
                        previousLiveState = stateKey
                        handler.postDelayed(this, 30_000L)
                    }
                }
                timetableTicker = ticker
                ticker.run()
            }

            for (lesson in dayLessons.sortedWith(
                compareBy<TimetableLesson> { it.from.ifBlank { "99:99" } }
                    .thenBy { it.number }
            )) {
                dayCard.addView(renderLessonCard(lesson), params(0, 10, 0, 0))
            }
            dayContent.addView(dayCard, params(0, 8, 0, 4))
        }

        for (date in dates) {
            val weekday = try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
                parsed?.let {
                    SimpleDateFormat("EEE", Locale("pl", "PL"))
                        .format(it)
                        .replaceFirstChar { first -> first.uppercase() }
                } ?: date
            } catch (_: Exception) {
                date
            }
            val button = TextView(this).apply {
                text = "$weekday\n${date.takeLast(5)}"
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setPadding(9, 9, 9, 9)
                isClickable = true
                isFocusable = true
                contentDescription = dateLabel(date)
                setOnClickListener {
                    selectedDate = date
                    refreshDaySelection()
                    renderSelectedDay(date)
                    if (uiPrefs.getBoolean("motion", true)) {
                        dayContent.alpha = 0f
                        dayContent.translationY = 8f * resources.displayMetrics.density
                        dayContent.animate().alpha(1f).translationY(0f).setDuration(190).start()
                        this.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120)
                            .withEndAction { this.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
                            .start()
                    }
                }
            }
            dayButtons[date] = button
            daySelector.addView(
                button,
                LinearLayout.LayoutParams(
                    (72 * resources.displayMetrics.density).toInt(),
                    (58 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (5 * resources.displayMetrics.density).toInt()
                }
            )
        }

        parent.addView(dayScroll, params(0, 0, 0, 4))
        parent.addView(dayContent, params(0, 0, 0, 4))
        refreshDaySelection()
        renderSelectedDay(selectedDate)
        dayScroll.post {
            dayButtons[selectedDate]?.let { selected ->
                val target = selected.left - (dayScroll.width - selected.width) / 2
                dayScroll.smoothScrollTo(target.coerceAtLeast(0), 0)
            }
        }
    }

    private fun renderLessonCard(lesson: TimetableLesson): LinearLayout {
        val canceled = lesson.canceled

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 11, 10, 11)
            background = roundedBackground(
                if (canceled) Color.rgb(65, 39, 48) else panelLight,
                12
            )
            alpha = if (canceled) 0.72f else 1f
        }

        val number = TextView(this).apply {
            text = if (lesson.number > 0) lesson.number.toString() else "•"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
        }
        container.addView(number, LinearLayout.LayoutParams(34, -2))

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        details.addView(
            label(
                lesson.subject,
                15f,
                if (lesson.canceled) errorColor else white,
                true
            )
        )

        when {
            lesson.canceled -> details.addView(statusChip("ODWOŁANA", errorColor), params(0, 3, 0, 2))
            lesson.substitution -> details.addView(statusChip("ZASTĘPSTWO", warningColor), params(0, 3, 0, 2))
        }

        val time = listOf(lesson.from, lesson.to)
            .filter { it.isNotBlank() }
            .joinToString(" – ")

        if (time.isNotBlank()) {
            details.addView(label(time, 13f, successColor, true))
        }

        if (lesson.classroom.isNotBlank()) {
            details.addView(label("Sala: ${lesson.classroom}", 12f, muted))
        }

        if (lesson.teacher.isNotBlank()) {
            details.addView(label("Nauczyciel: ${lesson.teacher}", 12f, muted))
        }

        if (lesson.note.isNotBlank()) {
            details.addView(label(lesson.note, 12f, warningColor))
        }

        container.addView(
            details,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        return container
    }

    // ---------------------------------------------------------
    // OCENY
    // ---------------------------------------------------------

    private fun renderGradesSection(
        parent: LinearLayout,
        data: JSONObject,
        subjects: JSONObject?,
        teachers: JSONObject?
    ) {
        val gradeKeys = listOf(
            "grades",
            "descriptive_grades",
            "partial_grades",
            "point_grades",
            "text_grades",
            "behaviour_grades"
        )

        var total = 0

        for (key in gradeKeys) {
            val array = data.optJSONArray(key) ?: continue
            total += array.length()
        }

        parent.addView(infoCard("Łącznie znaleziono $total wpisów ocen."))

        val headings = mapOf(
            "grades" to "Oceny",
            "descriptive_grades" to "Oceny opisowe",
            "partial_grades" to "Oceny cząstkowe",
            "point_grades" to "Oceny punktowe",
            "text_grades" to "Oceny tekstowe",
            "behaviour_grades" to "Oceny zachowania"
        )

        for (key in gradeKeys) {
            val array = data.optJSONArray(key) ?: continue
            if (array.length() == 0) continue

            parent.addView(sectionHeader(headings[key] ?: key), params(0, 18, 0, 8))
            renderGradeItems(parent, array, subjects, teachers)
        }

        data.optJSONObject("grading_system")?.let {
            parent.addView(sectionHeader("Skala ocen"), params(0, 18, 0, 8))
            renderObjectCard(parent, it, null, null)
        }

        if (total == 0) parent.addView(infoCard("Brak ocen do wyświetlenia."))
    }

    /*
     * Grupowanie ocen według przedmiotów.
     * Własna implementacja widoku inspirowana układem aplikacji Szkolny.eu.
     * Nie zawiera skopiowanego kodu źródłowego Szkolny.eu.
     */
    private fun renderGradeItems(
        parent: LinearLayout,
        items: JSONArray,
        subjects: JSONObject?,
        teachers: JSONObject?
    ) {
        val grouped = linkedMapOf<String, MutableList<JSONObject>>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val sid = firstValue(item, "subject_id", "subject")
            val subject = lookupName(subjects, sid)
                .ifBlank { item.optString("subject_name").ifBlank { "Pozostałe" } }

            grouped.getOrPut(subject) { mutableListOf() }.add(item)
        }

        if (grouped.isEmpty()) {
            parent.addView(infoCard("Brak wpisów do wyświetlenia."))
            return
        }

        for ((subject, grades) in grouped.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
            grades.sortByDescending {
                firstValue(it, "date", "add_date", "created_at")
            }

            val subjectCard = card()
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            header.addView(
                label(subject, 16f, white, true),
                LinearLayout.LayoutParams(0, -2, 1f)
            )
            header.addView(label("${grades.size} wpisów", 12f, successColor))
            subjectCard.addView(header)

            for (grade in grades) {
                // Librus może zwracać oceny liczbowe i opisowe w różnych polach.
                // Rozszerzona lista pozwala wyświetlić także tekstowe wpisy spotykane
                // w edukacji wczesnoszkolnej (klasy 1–3), bez wymuszania symbolu liczbowego.
                // Wartość może być liczbą, tekstem albo obiektem/strukturą
                // zwracaną przez różne warianty API Librusa.
                val value = firstDisplayValue(
                    grade,
                    "value", "grade", "symbol", "text", "result", "assessment",
                    "grade_value", "grade_text", "mark", "grade_description",
                    "skill", "name"
                ).ifBlank { "Ocena opisowa" }
                val date = firstValue(
                    grade, "date", "add_date", "created_at", "modified_at"
                ).take(10)
                val description = firstDisplayValue(
                    grade,
                    "description", "skill_description", "comment",
                    "teacher_comment", "feedback", "note", "details"
                )
                val teacherId = firstValue(grade, "teacher_id")
                val teacher = lookupName(teachers, teacherId)
                    .ifBlank { firstDisplayValue(grade, "teacher_name", "teacher") }

                val numericGrade = value.trim().matches(Regex("^[1-6][+-]?$"))
                val valueColor = gradeColor(value)
                val row = LinearLayout(this).apply {
                    orientation = if (numericGrade) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                    gravity = if (numericGrade) Gravity.CENTER_VERTICAL else Gravity.START
                    setPadding(0, 10, 0, 10)
                }

                if (numericGrade) {
                    val valueBadge = TextView(this).apply {
                        text = value
                        textSize = 22f
                        gravity = Gravity.CENTER
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(valueColor)
                        background = GradientDrawable().apply {
                            setColor(Color.argb(34, Color.red(valueColor), Color.green(valueColor), Color.blue(valueColor)))
                            cornerRadius = 14f * resources.displayMetrics.density
                            setStroke((1 * resources.displayMetrics.density).toInt(),
                                Color.argb(110, Color.red(valueColor), Color.green(valueColor), Color.blue(valueColor)))
                        }
                    }
                    row.addView(valueBadge, LinearLayout.LayoutParams(
                        (48 * resources.displayMetrics.density).toInt(),
                        (48 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginEnd = (12 * resources.displayMetrics.density).toInt()
                    })
                } else {
                    row.addView(statusChip("OCENA OPISOWA", accentColor), params(0, 0, 0, 5))
                    row.addView(label(value, 15f, white, true), params(0, 0, 0, 3))
                }

                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }

                if (date.isNotBlank()) details.addView(label(dateLabel(date), 12f, muted))
                if (description.isNotBlank()) {
                    details.addView(label(description, 13f, white))
                }
                if (teacher.isNotBlank()) {
                    details.addView(label("Nauczyciel: $teacher", 11f, muted))
                }

                if (numericGrade) {
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                } else {
                    row.addView(details, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, -2
                    ))
                }
                subjectCard.addView(row)

                appendJsonDetails(subjectCard, grade, "comments", "Komentarz")
                appendJsonDetails(subjectCard, grade, "requirements", "Wymaganie")
                appendJsonDetails(subjectCard, grade, "criteria", "Kryterium")
                appendJsonDetails(subjectCard, grade, "skills", "Umiejętność")
            }

            parent.addView(subjectCard, params(0, 0, 0, 10))
            if (uiPrefs.getBoolean("motion", true)) {
                subjectCard.alpha = 0f
                subjectCard.translationY = 10f * resources.displayMetrics.density
                subjectCard.animate().alpha(1f).translationY(0f)
                    .setStartDelay((parent.childCount.coerceAtMost(8) * 35L))
                    .setDuration(240).start()
            }
        }
    }

    // ---------------------------------------------------------
    // FREKWENCJA
    // ---------------------------------------------------------

    private fun renderAttendanceSection(parent: LinearLayout, data: JSONObject) {
        val types = data.optJSONObject("attendance_types")
        val array = data.optJSONArray("attendances")
        val entries = (0 until (array?.length() ?: 0))
            .mapNotNull { array?.optJSONObject(it) }
            .mapNotNull { item ->
                val rawDate = firstValue(item, "date", "lesson_date", "created_at")
                val isoDate = normalizeLibrusDate(rawDate)
                if (isoDate.isBlank()) null else item to isoDate
            }
            .sortedByDescending { it.second }

        if (entries.isEmpty()) {
            parent.addView(infoCard("Brak wpisów frekwencji."))
            renderJustifications(parent, data.optJSONArray("justifications"))
            return
        }

        fun typeOf(item: JSONObject): String {
            val id = firstValue(item, "type_id", "attendance_type_id", "type")
            return lookupName(types, id)
                .ifBlank { item.optString("type_name").ifBlank { id } }
                .ifBlank { "Wpis frekwencji" }
        }

        val byDate = entries.groupBy { it.second }.toSortedMap()
        val counts = linkedMapOf<String, Int>()
        val colors = linkedMapOf<String, Int>()
        entries.forEach { (item, _) ->
            val category = attendanceStatusCategory(typeOf(item))
            counts[category] = (counts[category] ?: 0) + 1
            colors[category] = attendanceStatusColor(typeOf(item))
        }

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        counts.forEach { (category, count) ->
            summary.addView(
                statusChip("$category: $count", colors[category] ?: accentColor),
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = (5 * resources.displayMetrics.density).toInt()
                }
            )
        }
        parent.addView(summary, params(0, 0, 0, 12))

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        var selectedDate = if (byDate.containsKey(today)) today else entries.first().second
        val monthCursor = java.util.Calendar.getInstance()
        val initial = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate)
        } catch (_: Exception) { null }
        if (initial != null) monthCursor.time = initial

        val monthTitle = TextView(this).apply {
            textSize = 17f
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val calendarGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt())
            background = roundedBackground(panel, 16)
        }
        val selectedDayTitle = label("", 16f, white, true)
        val dayContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun iso(calendar: java.util.Calendar): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        fun renderSelectedDay() {
            dayContent.removeAllViews()
            val dayEntries = byDate[selectedDate].orEmpty()
            selectedDayTitle.text = dateLabel(selectedDate)
            if (dayEntries.isEmpty()) {
                dayContent.addView(infoCard("Brak wpisów frekwencji w tym dniu."))
                return
            }

            dayEntries.forEach { (item, _) ->
                val type = typeOf(item)
                val tint = attendanceStatusColor(type)
                val subject = firstValue(item, "subject", "subject_name")
                val lesson = firstValue(item, "lesson_no", "lesson_number")
                val description = firstValue(item, "description", "comment", "note")
                val attendanceCard = card()
                val header = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                header.addView(statusChip(type, tint), LinearLayout.LayoutParams(0, -2, 1f))
                if (lesson.isNotBlank()) header.addView(label("Lekcja $lesson", 12f, muted, true))
                attendanceCard.addView(header)
                if (subject.isNotBlank()) attendanceCard.addView(label(subject, 13f, white, true), params(0, 5, 0, 0))
                if (description.isNotBlank()) attendanceCard.addView(label(description, 12f, muted))

                val normalized = type.lowercase(Locale("pl", "PL"))
                val excused = (normalized.contains("uspr") || normalized.contains("uspraw")) &&
                    !normalized.contains("nieuspr")
                val unexcused = attendanceStatusCategory(type) == "Nieobecności" && !excused
                if (unexcused) {
                    val lessonsForDay = dayEntries
                        .filter { (candidate, _) ->
                            val candidateType = typeOf(candidate).lowercase(Locale("pl", "PL"))
                            val candidateExcused = (candidateType.contains("uspr") || candidateType.contains("uspraw")) &&
                                !candidateType.contains("nieuspr")
                            attendanceStatusCategory(typeOf(candidate)) == "Nieobecności" && !candidateExcused
                        }
                        .mapNotNull { (candidate, _) ->
                            firstValue(candidate, "lesson_no", "lesson_number").toIntOrNull()
                        }
                        .filter { it in 1..20 }
                        .distinct()
                        .sorted()
                    val openJustification = {
                        showFormalJustificationComposer(
                            firstValue(item, "date", "lesson_date").ifBlank { selectedDate },
                            subject,
                            lesson,
                            lessonsForDay.ifEmpty { lesson.toIntOrNull()?.let { listOf(it) } ?: emptyList() }
                        )
                    }
                    attendanceCard.isClickable = true
                    attendanceCard.isFocusable = true
                    attendanceCard.setOnClickListener { openJustification() }
                    attendanceCard.addView(
                        actionButton("ZŁÓŻ E-USPRAWIEDLIWIENIE", openJustification),
                        params(0, 8, 0, 0)
                    )
                }
                dayContent.addView(attendanceCard, params(0, 0, 0, 8))
            }
        }

        fun renderCalendar() {
            val monthName = SimpleDateFormat("LLLL yyyy", Locale("pl", "PL"))
                .format(monthCursor.time).replaceFirstChar { it.uppercase() }
            monthTitle.text = monthName
            calendarGrid.removeAllViews()
            val weekdays = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            weekdays.forEach { day ->
                header.addView(TextView(this).apply {
                    text = day
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(muted)
                    setTypeface(null, Typeface.BOLD)
                }, LinearLayout.LayoutParams(0, (28 * resources.displayMetrics.density).toInt(), 1f))
            }
            calendarGrid.addView(header)

            val first = (monthCursor.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 12)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            val offset = (first.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
            val daysInMonth = first.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            var dayNumber = 1
            for (week in 0 until 6) {
                if (dayNumber > daysInMonth) break
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (column in 0 until 7) {
                    val cellIndex = week * 7 + column
                    val number = cellIndex - offset + 1
                    if (number !in 1..daysInMonth) {
                        row.addView(View(this), LinearLayout.LayoutParams(0, (46 * resources.displayMetrics.density).toInt(), 1f))
                        continue
                    }
                    val cellCalendar = (first.clone() as java.util.Calendar).apply {
                        set(java.util.Calendar.DAY_OF_MONTH, number)
                    }
                    val dateKey = iso(cellCalendar)
                    val hasEntries = byDate.containsKey(dateKey)
                    val isSelected = dateKey == selectedDate
                    val isToday = dateKey == today
                    val dayCell = TextView(this).apply {
                        text = if (hasEntries) "$number\n●" else number.toString()
                        textSize = if (hasEntries) 12f else 13f
                        gravity = Gravity.CENTER
                        setTypeface(null, if (isSelected || isToday || hasEntries) Typeface.BOLD else Typeface.NORMAL)
                        setTextColor(when {
                            isSelected -> Color.WHITE
                            hasEntries -> attendanceStatusColor(typeOf(byDate[dateKey]!!.first().first))
                            isToday -> accentColor
                            else -> muted
                        })
                        background = GradientDrawable().apply {
                            setColor(if (isSelected) Color.argb(220, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                                else if (isToday) Color.argb(35, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                                else Color.TRANSPARENT)
                            cornerRadius = 13f * resources.displayMetrics.density
                            if (isToday && !isSelected) setStroke((1.5f * resources.displayMetrics.density).toInt(), accentColor)
                        }
                        isClickable = true
                        isFocusable = true
                        contentDescription = dateLabel(dateKey) + if (hasEntries) ", wpisy frekwencji" else ""
                        setOnClickListener {
                            selectedDate = dateKey
                            renderCalendar()
                            renderSelectedDay()
                            if (uiPrefs.getBoolean("motion", true)) {
                                dayContent.alpha = 0f
                                dayContent.translationY = 8f * resources.displayMetrics.density
                                dayContent.animate().alpha(1f).translationY(0f).setDuration(180).start()
                            }
                        }
                    }
                    row.addView(dayCell, LinearLayout.LayoutParams(0, (46 * resources.displayMetrics.density).toInt(), 1f).apply {
                        val margin = (2 * resources.displayMetrics.density).toInt()
                        setMargins(margin, margin, margin, margin)
                    })
                    dayNumber++
                }
                calendarGrid.addView(row)
            }
        }

        val monthControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun monthButton(symbol: String, delta: Int): TextView = TextView(this).apply {
            text = symbol
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(accentColor)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                monthCursor.add(java.util.Calendar.MONTH, delta)
                val prefix = SimpleDateFormat("yyyy-MM", Locale.US).format(monthCursor.time)
                selectedDate = byDate.keys.firstOrNull { it.startsWith(prefix) }
                    ?: iso((monthCursor.clone() as java.util.Calendar).apply { set(java.util.Calendar.DAY_OF_MONTH, 1) })
                renderCalendar()
                renderSelectedDay()
            }
        }
        monthControls.addView(monthButton("‹", -1), LinearLayout.LayoutParams((44 * resources.displayMetrics.density).toInt(), (42 * resources.displayMetrics.density).toInt()))
        monthControls.addView(monthTitle, LinearLayout.LayoutParams(0, -2, 1f))
        monthControls.addView(monthButton("›", 1), LinearLayout.LayoutParams((44 * resources.displayMetrics.density).toInt(), (42 * resources.displayMetrics.density).toInt()))
        parent.addView(sectionHeader("FREKWENCJA • KALENDARZ"), params(0, 4, 0, 4))
        parent.addView(monthControls)
        parent.addView(calendarGrid, params(0, 4, 0, 12))
        parent.addView(selectedDayTitle, params(0, 4, 0, 4))
        parent.addView(dayContent)
        renderCalendar()
        renderSelectedDay()
        renderJustifications(parent, data.optJSONArray("justifications"))
    }

    private fun normalizeLibrusDate(value: String): String {
        val trimmed = value.trim()
        val parsed = parseFeatureDate(trimmed) ?: return ""
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed)
    }

    private fun showFormalJustificationComposer(
        date: String,
        subject: String,
        lesson: String,
        availableLessons: List<Int>
    ) {
        val isoDate = normalizeLibrusDate(date)
        val lessonNumber = lesson.trim().toIntOrNull()
        val choices = availableLessons.filter { it in 1..20 }.distinct().sorted()
            .ifEmpty { lessonNumber?.takeIf { it in 1..20 }?.let { listOf(it) } ?: emptyList() }
        if (isoDate.isBlank() || choices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Nie można wskazać lekcji")
                .setMessage("Nie udało się jednoznacznie rozpoznać daty lub numeru lekcji. Możesz zamiast tego wysłać prośbę do wychowawcy.")
                .setNegativeButton("ANULUJ", null)
                .setPositiveButton("WIADOMOŚĆ") { _, _ ->
                    requestJustificationToTutor(date, subject, lesson)
                }
                .show()
            return
        }

        val density = resources.displayMetrics.density
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (8 * density).toInt(),
                (18 * density).toInt(), (12 * density).toInt())
        }
        form.addView(label("Data: $date", 13f, warningColor, true))
        if (subject.isNotBlank()) form.addView(label("Przedmiot: $subject", 12f, muted))
        form.addView(label(
            "Zaznacz lekcje z tego dnia, które chcesz usprawiedliwić. Domyślnie zaznaczone są wykryte nieobecności.",
            12f, muted
        ), params(0, 6, 0, 8))

        val lessonChecks = choices.map { number ->
            CheckBox(this).apply {
                text = "Lekcja $number"
                textSize = 14f
                setTextColor(white)
                buttonTintList = android.content.res.ColorStateList.valueOf(accentColor)
                isChecked = true
            }.also { check ->
                form.addView(check, params(0, 0, 0, 2))
            }.let { number to it }
        }

        form.addView(label(
            "Zgłoszenie zostanie wysłane do oficjalnego modułu e-Usprawiedliwienia Librusa.",
            12f, accentColor
        ), params(0, 8, 0, 0))

        val reasonInput = EditText(this).apply {
            hint = "Powód nieobecności"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            setTextColor(white)
            setHintTextColor(muted)
            setPadding((14 * density).toInt(), (12 * density).toInt(),
                (14 * density).toInt(), (12 * density).toInt())
            background = roundedBackground(panel, 10)
        }
        form.addView(reasonInput, params(0, 10, 0, 0))

        val dialog = AlertDialog.Builder(this).setTitle("E-Usprawiedliwienie")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("ANULUJ", null).setPositiveButton("DALEJ", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedLessons = lessonChecks.filter { it.second.isChecked }.map { it.first }
                val reason = reasonInput.text.toString().trim()
                if (selectedLessons.isEmpty()) {
                    Toast.makeText(this, "Zaznacz przynajmniej jedną lekcję.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (reason.isBlank()) {
                    reasonInput.error = "Wpisz powód nieobecności."
                    return@setOnClickListener
                }
                dialog.dismiss()
                val lessonLabel = selectedLessons.joinToString(", ")
                val confirmation = AlertDialog.Builder(this).setTitle("Potwierdź e-Usprawiedliwienie")
                    .setMessage(
                        "Librus otrzyma formalne usprawiedliwienie dla dnia $date, lekcje: $lessonLabel.\n\n" +
                            "Powód: $reason\n\nWychowawca będzie mógł je zaakceptować lub odrzucić."
                    )
                    .setNegativeButton("WRÓĆ", null).setPositiveButton("WYŚLIJ", null).create()
                confirmation.setOnShowListener {
                    confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        confirmation.dismiss()
                        submitFormalJustification(
                            isoDate, selectedLessons, reason, date, subject, lessonLabel
                        )
                    }
                }
                confirmation.show()
            }
        }
        dialog.show()
    }

    private fun submitFormalJustification(
        isoDate: String,
        lessonNumbers: List<Int>,
        reason: String,
        displayDate: String,
        subject: String,
        lesson: String
    ) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()
        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Składam e-Usprawiedliwienie w Librusie…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/justification/submit").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 90000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                val payload = JSONObject().apply {
                    put("profile", "parent")
                    put("login", login)
                    put("password", password)
                    put("date_from", isoDate)
                    put("date_to", isoDate)
                    put("lessons", JSONArray().apply { lessonNumbers.forEach { put(it) } })
                    put("message_from_parent", reason)
                    put("send_notify", true)
                    librusSessionMemory?.let { put("session", it) }
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                "$status|${readResponse(connection, status)}"
            } catch (e: Exception) {
                "ERROR|${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("ERROR|")) {
                    setLibrusStatus("Nieznany wynik wysyłki e-Usprawiedliwienia.", false)
                    AlertDialog.Builder(this).setTitle("Sprawdź status zgłoszenia")
                        .setMessage("Połączenie zostało przerwane. Nie wysyłaj ponownie, dopóki nie sprawdzisz modułu e-Usprawiedliwienia w Librusie.")
                        .setPositiveButton("OK", null).show()
                    return@runOnUiThread
                }

                val status = result.substringBefore("|").toIntOrNull() ?: 0
                val responseBody = result.substringAfter("|")
                if (status in 200..299) {
                    try {
                        val json = JSONObject(responseBody)
                        librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                        librusVault.updateSession(librusSessionMemory?.toString())
                    } catch (_: Exception) { }
                    setLibrusStatus("E-Usprawiedliwienie wysłane.", true)
                    AlertDialog.Builder(this).setTitle("Zgłoszenie wysłane")
                        .setMessage(
                            "Librus przyjął e-Usprawiedliwienie dla $displayDate, lekcje: $lesson. " +
                                "Status początkowy to „Wysłane” — oczekuje na decyzję wychowawcy.\\n\\n" +
                                "Możesz teraz odświeżyć frekwencję, aby pobrać aktualny status zgłoszenia."
                        )
                        .setNegativeButton("PÓŹNIEJ", null)
                        .setPositiveButton("ODŚWIEŻ STATUS") { _, _ ->
                            librusDataMemory = null
                            loadLibrusSection("FREKWENCJA")
                        }
                        .show()
                    return@runOnUiThread
                }

                val detailObject = try { JSONObject(responseBody).opt("detail") } catch (_: Exception) { null }
                val detail = when (detailObject) {
                    is JSONObject -> detailObject.optString("message", detailObject.toString())
                    null -> "HTTP $status"
                    else -> detailObject.toString()
                }
                val moduleUnavailable = status == 403 || status == 404 ||
                    (detailObject is JSONObject && detailObject.has("module_available") &&
                        !detailObject.optBoolean("module_available", true))
                if (moduleUnavailable) {
                    AlertDialog.Builder(this).setTitle("Moduł e-Usprawiedliwienia niedostępny")
                        .setMessage("Ta szkoła lub konto nie udostępnia formalnego modułu e-Usprawiedliwienia. Czy chcesz zamiast tego wysłać zwykłą prośbę do wychowawcy?")
                        .setNegativeButton("ANULUJ", null)
                        .setPositiveButton("WYŚLIJ WIADOMOŚĆ") { _, _ ->
                            requestJustificationToTutor(displayDate, subject, lesson)
                        }
                        .show()
                } else {
                    setLibrusStatus(detail, false)
                    val unknownOutcome = status >= 500
                    AlertDialog.Builder(this)
                        .setTitle(if (unknownOutcome) "Sprawdź status zgłoszenia" else "Nie wysłano e-Usprawiedliwienia")
                        .setMessage(
                            if (unknownOutcome) "$detail\n\nNie ponawiaj wysyłki, dopóki nie sprawdzisz modułu e-Usprawiedliwienia."
                            else detail
                        )
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun renderJustifications(parent: LinearLayout, array: JSONArray?) {
        if (array == null || array.length() == 0) return

        parent.addView(sectionHeader("E-USPRAWIEDLIWIENIA"), params(0, 18, 0, 8))
        val items = (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .sortedByDescending { firstValue(it, "posted", "date_from") }

        for (item in items) {
            val normalized = firstValue(item, "status").lowercase(Locale.ROOT)
            val (statusLabel, tint) = when {
                normalized.startsWith("accept") -> "PRZYJĘTE" to successColor
                normalized.startsWith("reject") || normalized.startsWith("refus") ||
                    normalized.startsWith("deny") || normalized.startsWith("declin") ->
                    "ODRZUCONE" to errorColor
                else -> "OCZEKUJE NA DECYZJĘ" to warningColor
            }

            val dateFrom = firstValue(item, "date_from")
            val dateTo = firstValue(item, "date_to")
            val dateRange = when {
                dateFrom.isNotBlank() && dateTo.isNotBlank() && dateFrom != dateTo ->
                    "$dateFrom – $dateTo"
                dateFrom.isNotBlank() -> dateFrom
                else -> dateTo
            }
            val message = firstValue(item, "message")
            val posted = firstValue(item, "posted")
            val justifiedCount = item.optInt("justified_absences", 0)
            val teachers = item.optJSONArray("teachers")
            val teacherNames = teachers?.let { list ->
                (0 until list.length()).mapNotNull { index ->
                    list.optString(index).takeIf { it.isNotBlank() }
                }.joinToString(", ")
            }.orEmpty()

            val justificationCard = card()
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(statusChip(statusLabel, tint), LinearLayout.LayoutParams(0, -2, 1f))
            if (dateRange.isNotBlank()) header.addView(label(dateRange, 12f, muted, true))
            justificationCard.addView(header)
            if (justifiedCount > 0) {
                justificationCard.addView(label("Usprawiedliwione lekcje: $justifiedCount", 12f, successColor))
            }
            if (message.isNotBlank()) {
                justificationCard.addView(label(message, 13f, white), params(0, 4, 0, 0))
            }
            if (posted.isNotBlank()) justificationCard.addView(label("Wysłano: $posted", 11f, muted))
            if (teacherNames.isNotBlank()) {
                justificationCard.addView(label("Powiadomieni nauczyciele: $teacherNames", 11f, muted))
            }
            if (item.optBoolean("has_attachment")) {
                justificationCard.addView(label("Dodano załącznik", 11f, accentColor))
            }
            parent.addView(justificationCard, params(0, 0, 0, 8))
        }
    }

    private fun requestJustificationToTutor(date: String, subject: String, lesson: String) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()
        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Pobieram wychowawcę z Librusa…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/message/recipients").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 90000
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
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                "$status|${readResponse(connection, status)}"
            } catch (e: Exception) {
                "ERROR|${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("ERROR|")) {
                    setLibrusStatus("Nie udało się pobrać wychowawcy: ${result.substringAfter("|")}", false)
                    return@runOnUiThread
                }
                val status = result.substringBefore("|").toIntOrNull() ?: 0
                val responseBody = result.substringAfter("|")
                if (status !in 200..299) {
                    setLibrusStatus(errorDetail(responseBody, "HTTP $status"), false)
                    return@runOnUiThread
                }
                try {
                    val json = JSONObject(responseBody)
                    librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                    librusVault.updateSession(librusSessionMemory?.toString())
                    val recipients = json.optJSONArray("recipients") ?: JSONArray()
                    var tutor: JSONObject? = null
                    for (index in 0 until recipients.length()) {
                        val candidate = recipients.optJSONObject(index) ?: continue
                        val group = firstValue(candidate, "group").lowercase(Locale.ROOT)
                        val groupName = firstValue(candidate, "group_name", "groupName")
                            .lowercase(Locale("pl", "PL"))
                        if (group == "tutors" || groupName.contains("wychowaw")) {
                            tutor = candidate
                            break
                        }
                    }
                    if (tutor == null) {
                        setLibrusStatus("Librus nie udostępnił wychowawcy jako odbiorcy.", false)
                        AlertDialog.Builder(this).setTitle("Nie znaleziono wychowawcy")
                            .setMessage("Nie mogę bezpiecznie wybrać odbiorcy. Sprawdź listę odbiorców w Librusie.")
                            .setPositiveButton("OK", null).show()
                        return@runOnUiThread
                    }
                    val recipientId = firstValue(tutor, "account_id", "accountId")
                    val recipientName = firstValue(tutor, "name").ifBlank { "Wychowawca" }
                    if (recipientId.isBlank()) {
                        setLibrusStatus("Librus nie zwrócił identyfikatora wychowawcy.", false)
                        return@runOnUiThread
                    }
                    showJustificationComposer(recipientId, recipientName, date, subject, lesson)
                } catch (e: Exception) {
                    setLibrusStatus("Nieprawidłowa odpowiedź Librusa: ${e.message}", false)
                }
            }
        }
    }

    private fun showJustificationComposer(
        recipientId: String, recipientName: String, date: String, subject: String, lesson: String
    ) {
        val density = resources.displayMetrics.density
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (8 * density).toInt(),
                (18 * density).toInt(), (12 * density).toInt())
        }
        form.addView(label("Do: $recipientName", 13f, muted, true))
        form.addView(label("Nieobecność: $date", 13f, warningColor, true))
        if (subject.isNotBlank()) form.addView(label("Przedmiot: $subject", 12f, muted))
        if (lesson.isNotBlank()) form.addView(label("Lekcja: $lesson", 12f, muted))

        val topicInput = inputField("Temat wiadomości",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            setText("Prośba o usprawiedliwienie nieobecności – $date")
        }
        form.addView(topicInput, params(0, 8, 0, 8))
        val absenceDescription = buildString {
            append("Proszę o usprawiedliwienie nieobecności mojego dziecka w dniu $date")
            if (subject.isNotBlank()) append(" na lekcji: $subject")
            if (lesson.isNotBlank()) append(" (lekcja $lesson)")
        }
        val bodyInput = EditText(this).apply {
            hint = "Treść usprawiedliwienia"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            maxLines = 9
            gravity = Gravity.TOP or Gravity.START
            setText("Dzień dobry,\n\n$absenceDescription.\n\nPowód nieobecności: ")
            setTextColor(white)
            setHintTextColor(muted)
            setPadding((14 * density).toInt(), (12 * density).toInt(),
                (14 * density).toInt(), (12 * density).toInt())
            background = roundedBackground(panel, 10)
        }
        form.addView(bodyInput, params(0, 0, 0, 0))
        val dialog = AlertDialog.Builder(this).setTitle("Usprawiedliwienie nieobecności")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("ANULUJ", null).setPositiveButton("DALEJ", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val topic = topicInput.text.toString().trim()
                val body = bodyInput.text.toString().trim()
                if (topic.isBlank() || body.isBlank() || body.endsWith("Powód nieobecności:")) {
                    bodyInput.error = "Uzupełnij powód nieobecności."
                    return@setOnClickListener
                }
                dialog.dismiss()
                val confirmation = AlertDialog.Builder(this).setTitle("Potwierdź wysłanie")
                    .setMessage("Wiadomość zostanie wysłana do: $recipientName\n\nTemat: $topic\n\n$body\n\nWysłanie wiadomości nie oznacza jeszcze usprawiedliwienia nieobecności.")
                    .setNegativeButton("WRÓĆ", null).setPositiveButton("WYŚLIJ", null).create()
                confirmation.setOnShowListener {
                    confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        confirmation.dismiss()
                        sendJustificationMessage(recipientId, recipientName, topic, body)
                    }
                }
                confirmation.show()
            }
        }
        dialog.show()
    }

    private fun sendJustificationMessage(
        recipientId: String, recipientName: String, topic: String, body: String
    ) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()
        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Wysyłam wiadomość do wychowawcy…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/message/send").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 90000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                val payload = JSONObject().apply {
                    put("profile", "parent")
                    put("login", login)
                    put("password", password)
                    put("recipient_ids", JSONArray().put(recipientId))
                    put("topic", topic)
                    put("text", body)
                    librusSessionMemory?.let { put("session", it) }
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                "$status|${readResponse(connection, status)}"
            } catch (e: Exception) {
                "ERROR|${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("ERROR|")) {
                    setLibrusStatus("Nie udało się potwierdzić wysyłki: ${result.substringAfter("|")}", false)
                    AlertDialog.Builder(this).setTitle("Nieznany wynik wysyłki")
                        .setMessage("Nie ponawiaj teraz wysyłania. Sprawdź folder Wysłane w Librusie.")
                        .setPositiveButton("OK", null).show()
                    return@runOnUiThread
                }
                val status = result.substringBefore("|").toIntOrNull() ?: 0
                val responseBody = result.substringAfter("|")
                if (status in 200..299) {
                    try {
                        val json = JSONObject(responseBody)
                        librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                        librusVault.updateSession(librusSessionMemory?.toString())
                    } catch (_: Exception) { }
                    setLibrusStatus("Wiadomość wysłana do $recipientName.", true)
                    AlertDialog.Builder(this).setTitle("Wiadomość wysłana")
                        .setMessage("Librus przyjął wiadomość do $recipientName. Nieobecność pozostaje nieusprawiedliwiona do czasu decyzji szkoły.")
                        .setPositiveButton("OK", null).show()
                } else {
                    val detail = try {
                        val raw = JSONObject(responseBody).opt("detail")
                        if (raw is JSONObject) raw.optString("message", raw.toString())
                        else raw?.toString() ?: "HTTP $status"
                    } catch (_: Exception) { "HTTP $status" }
                    setLibrusStatus(detail, false)
                    AlertDialog.Builder(this)
                        .setTitle(if (status == 502) "Sprawdź folder Wysłane" else "Nie wysłano wiadomości")
                        .setMessage(detail).setPositiveButton("OK", null).show()
                }
            }
        }
    }

    // WIADOMOŚCI I OGŁOSZENIA
    // ---------------------------------------------------------

    private fun renderMessagesSection(parent: LinearLayout, data: JSONObject) {
        parent.addView(
            actionButton("NAPISZ WIADOMOŚĆ") { openNewMessageComposer() },
            params(0, 0, 0, 10)
        )
        renderMessageArray(parent, data.optJSONArray("messages"), "Wiadomości")
        renderMessageArray(parent, data.optJSONArray("announcements"), "Ogłoszenia")
    }

    private fun openNewMessageComposer() {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()
        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Pobieram listę odbiorców…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/message/recipients").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 90000
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
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                "$status|${readResponse(connection, status)}"
            } catch (e: Exception) {
                "ERROR|${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("ERROR|")) {
                    setLibrusStatus("Nie udało się pobrać odbiorców: ${result.substringAfter("|")}", false)
                    return@runOnUiThread
                }
                val status = result.substringBefore("|").toIntOrNull() ?: 0
                val responseBody = result.substringAfter("|")
                if (status !in 200..299) {
                    setLibrusStatus(errorDetail(responseBody, "HTTP $status"), false)
                    return@runOnUiThread
                }
                try {
                    val json = JSONObject(responseBody)
                    librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                    librusVault.updateSession(librusSessionMemory?.toString())
                    val array = json.optJSONArray("recipients") ?: JSONArray()
                    val recipients = (0 until array.length())
                        .mapNotNull { array.optJSONObject(it) }
                        .filter { firstValue(it, "account_id", "accountId").isNotBlank() }
                        .sortedWith(compareBy<JSONObject> {
                            when (firstValue(it, "group").lowercase(Locale.ROOT)) {
                                "tutors" -> 0
                                "teachers" -> 1
                                else -> 2
                            }
                        }.thenBy { firstValue(it, "name") })
                    if (recipients.isEmpty()) {
                        setLibrusStatus("Librus nie zwrócił dostępnych odbiorców.", false)
                        AlertDialog.Builder(this).setTitle("Brak odbiorców")
                            .setMessage("Nie ma odbiorców, do których można teraz wysłać wiadomość.")
                            .setPositiveButton("OK", null).show()
                        return@runOnUiThread
                    }
                    showMessageRecipientPicker(recipients)
                } catch (e: Exception) {
                    setLibrusStatus("Nieprawidłowa lista odbiorców: ${e.message}", false)
                }
            }
        }
    }

    private fun showMessageRecipientPicker(recipients: List<JSONObject>) {
        val labels = recipients.map { recipient ->
            val name = firstValue(recipient, "name").ifBlank { "Odbiorca" }
            val group = firstValue(recipient, "group_name", "groupName")
            val away = if (recipient.has("available") && !recipient.isNull("available") &&
                !recipient.optBoolean("available")) " • nieobecny" else ""
            if (group.isNotBlank()) "$name — $group$away" else "$name$away"
        }.toTypedArray()
        val selected = BooleanArray(recipients.size)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Wybierz odbiorców")
            .setMultiChoiceItems(labels, selected) { dialogInterface, which, checked ->
                if (checked && selected.count { it } >= 10) {
                    (dialogInterface as? AlertDialog)?.listView?.setItemChecked(which, false)
                    Toast.makeText(this, "Możesz wybrać maksymalnie 10 odbiorców.", Toast.LENGTH_SHORT).show()
                } else {
                    selected[which] = checked
                }
            }
            .setNegativeButton("ANULUJ", null)
            .setPositiveButton("DALEJ", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chosen = recipients.filterIndexed { index, _ -> selected[index] }
                if (chosen.isEmpty()) {
                    Toast.makeText(this, "Wybierz przynajmniej jednego odbiorcę.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val ids = chosen.map { firstValue(it, "account_id", "accountId") }
                val names = chosen.map { firstValue(it, "name").ifBlank { "Odbiorca" } }
                dialog.dismiss()
                showNewMessageComposer(ids, names)
            }
        }
        dialog.show()
    }

    private fun showNewMessageComposer(recipientIds: List<String>, recipientNames: List<String>) {
        val density = resources.displayMetrics.density
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (8 * density).toInt(),
                (18 * density).toInt(), (12 * density).toInt())
        }
        form.addView(label("Do: ${recipientNames.joinToString(", ")}", 12f, muted))
        val topicInput = inputField(
            "Temat wiadomości",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )
        form.addView(topicInput, params(0, 8, 0, 8))
        val bodyInput = EditText(this).apply {
            hint = "Treść wiadomości"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            maxLines = 9
            gravity = Gravity.TOP or Gravity.START
            setTextColor(white)
            setHintTextColor(muted)
            setPadding((14 * density).toInt(), (12 * density).toInt(),
                (14 * density).toInt(), (12 * density).toInt())
            background = roundedBackground(panel, 10)
        }
        form.addView(bodyInput, params(0, 0, 0, 0))
        val dialog = AlertDialog.Builder(this).setTitle("Nowa wiadomość")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("ANULUJ", null).setPositiveButton("DALEJ", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val topic = topicInput.text.toString().trim()
                val body = bodyInput.text.toString().trim()
                if (topic.isBlank()) {
                    topicInput.error = "Wpisz temat wiadomości."
                    return@setOnClickListener
                }
                if (body.isBlank()) {
                    bodyInput.error = "Wpisz treść wiadomości."
                    return@setOnClickListener
                }
                dialog.dismiss()
                val confirmation = AlertDialog.Builder(this).setTitle("Potwierdź wysłanie")
                    .setMessage("Do: ${recipientNames.joinToString(", ")}\n\nTemat: $topic\n\n$body")
                    .setNegativeButton("WRÓĆ", null).setPositiveButton("WYŚLIJ", null).create()
                confirmation.setOnShowListener {
                    confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        confirmation.dismiss()
                        sendNewMessage(recipientIds, recipientNames, topic, body)
                    }
                }
                confirmation.show()
            }
        }
        dialog.show()
    }

    private fun sendNewMessage(
        recipientIds: List<String>, recipientNames: List<String>, topic: String, body: String
    ) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()
        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Wysyłam wiadomość…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/message/send").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 90000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                val payload = JSONObject().apply {
                    put("profile", "parent")
                    put("login", login)
                    put("password", password)
                    put("recipient_ids", JSONArray().apply { recipientIds.forEach { put(it) } })
                    put("topic", topic)
                    put("text", body)
                    librusSessionMemory?.let { put("session", it) }
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                "$status|${readResponse(connection, status)}"
            } catch (e: Exception) {
                "ERROR|${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("ERROR|")) {
                    setLibrusStatus("Nie udało się potwierdzić wysyłki: ${result.substringAfter("|")}", false)
                    AlertDialog.Builder(this).setTitle("Nieznany wynik wysyłki")
                        .setMessage("Nie ponawiaj teraz wysyłania. Sprawdź folder Wysłane w Librusie.")
                        .setPositiveButton("OK", null).show()
                    return@runOnUiThread
                }
                val status = result.substringBefore("|").toIntOrNull() ?: 0
                val responseBody = result.substringAfter("|")
                if (status in 200..299) {
                    try {
                        val json = JSONObject(responseBody)
                        librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                        librusVault.updateSession(librusSessionMemory?.toString())
                    } catch (_: Exception) { }
                    setLibrusStatus("Wiadomość wysłana.", true)
                    AlertDialog.Builder(this).setTitle("Wiadomość wysłana")
                        .setMessage("Librus przyjął wiadomość do: ${recipientNames.joinToString(", ")}.")
                        .setPositiveButton("OK", null).show()
                } else {
                    val detail = try {
                        val raw = JSONObject(responseBody).opt("detail")
                        if (raw is JSONObject) raw.optString("message", raw.toString())
                        else raw?.toString() ?: "HTTP $status"
                    } catch (_: Exception) { "HTTP $status" }
                    setLibrusStatus(detail, false)
                    AlertDialog.Builder(this)
                        .setTitle(if (status == 502) "Sprawdź folder Wysłane" else "Nie wysłano wiadomości")
                        .setMessage(detail).setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun renderMessageArray(
        parent: LinearLayout,
        array: JSONArray?,
        heading: String
    ) {
        if (array == null || array.length() == 0) {
            parent.addView(infoCard("Brak wiadomości do wyświetlenia: $heading."))
            return
        }

        parent.addView(sectionHeader(heading), params(0, 18, 0, 8))

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = firstValue(item, "title", "subject", "topic")
                .ifBlank { heading.removeSuffix("i") }
            val sender = firstValue(item, "sender_name", "sender", "author", "teacher_name", "from")
            val date = firstValue(item, "send_date", "date", "created_at", "sent_at")
            val body = firstValue(
                item,
                "content", "message", "body", "text",
                "content_html", "message_content", "description"
            )

            val card = card()
            card.addView(label(title, 15f, white, true))
            if (sender.isNotBlank()) card.addView(label("Od: $sender", 12f, muted))
            if (date.isNotBlank()) card.addView(label(date, 12f, successColor))

            val messageReadDate = if (heading == "Wiadomości") {
                firstValue(item, "read_date", "readDate")
            } else {
                "not-a-message"
            }
            val readChip = if (heading == "Wiadomości") {
                statusChip(
                    if (messageReadDate.isBlank()) "NIEPRZECZYTANA" else "PRZECZYTANA",
                    if (messageReadDate.isBlank()) warningColor else successColor
                ).also { card.addView(it, params(0, 5, 0, 0)) }
            } else null

            if (heading == "Ogłoszenia") {
                if (body.isNotBlank()) {
                    card.addView(label(body.take(220) + if (body.length > 220) "…" else "", 13f, white))
                    card.addView(actionButton("OTWÓRZ OGŁOSZENIE") {
                        showMessageDetails(title, sender, date, body)
                    })
                } else {
                    card.addView(label("Brak treści ogłoszenia.", 12f, muted))
                }
            } else {
                val messageId = firstValue(item, "id", "message_id", "messageId")
                if (body.isNotBlank()) {
                    card.addView(label(body.take(220) + if (body.length > 220) "…" else "", 13f, white))
                } else {
                    card.addView(label("Podgląd wiadomości niedostępny.", 12f, muted))
                }

                if (messageId.isNotBlank()) {
                    card.addView(actionButton("OTWÓRZ CAŁĄ WIADOMOŚĆ") {
                        fetchFullMessage(messageId, title, sender, date) {
                            item.put("read_date", "read")
                            readChip?.apply {
                                text = "PRZECZYTANA"
                                setTextColor(successColor)
                                background = statusChip("PRZECZYTANA", successColor).background
                            }
                        }
                    })
                } else {
                    card.addView(label("Brak identyfikatora wiadomości — nie można pobrać pełnej treści.", 12f, warningColor))
                }
            }

            parent.addView(card, params(0, 0, 0, 8))
        }
    }

    private fun fetchFullMessage(
        messageId: String,
        title: String,
        sender: String,
        date: String,
        onRead: (() -> Unit)? = null
    ) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()

        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }

        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Pobieram pełną treść wiadomości…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/message").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 90000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")

                val payload = JSONObject().apply {
                    put("profile", "parent")
                    put("login", login)
                    put("password", password)
                    put("message_id", messageId)
                    put("mailbox", "inbox")
                    librusSessionMemory?.let { put("session", it) }
                }
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode
                val responseBody = readResponse(connection, code)
                if (code in 200..299) {
                    val json = JSONObject(responseBody)
                    val message = json.optJSONObject("message")
                    val fullBody = message?.let {
                        firstValue(it, "content", "message", "body", "text", "content_html", "message_content")
                    }.orEmpty()
                    val attachments = message?.optJSONArray("attachments") ?: JSONArray()
                    if (fullBody.isBlank() && attachments.length() == 0) {
                        "ERROR|Librus nie zwrócił treści ani załączników tej wiadomości."
                    } else {
                        librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                        librusVault.updateSession(librusSessionMemory?.toString())
                        "SUCCESS|${message?.toString().orEmpty()}"
                    }
                } else {
                    "ERROR|Nie udało się pobrać wiadomości ($code): ${errorDetail(responseBody, "HTTP $code")}"
                }
            } catch (e: Exception) {
                "ERROR|Błąd pobierania wiadomości: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("SUCCESS|")) {
                    setLibrusStatus("Pobrano pełną treść wiadomości.", true)
                    onRead?.invoke()
                    val messageJson = JSONObject(result.substringAfter("|"))
                    val fullBody = firstValue(messageJson, "content", "message", "body", "text")
                    val mailbox = firstValue(messageJson, "mailbox").ifBlank { "inbox" }
                    val noReply = messageJson.optBoolean("no_reply", false) ||
                        firstValue(messageJson, "sender_id", "senderId").isBlank()
                    val attachments = messageJson.optJSONArray("attachments") ?: JSONArray()
                    showMessageDetails(title, sender, date, fullBody, messageId, mailbox, noReply, attachments)
                } else {
                    setLibrusStatus(result.substringAfter("|"), false)
                }
            }
        }
    }

    private fun saveAndOpenAttachment(attachment: JSONObject, fallbackFilename: String, prefix: String) {
        val encoded = attachment.optString("content_base64")
        if (encoded.isBlank()) throw IllegalStateException("Librus zwrócił pusty plik.")
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        if (bytes.size > 20 * 1024 * 1024) {
            throw IllegalStateException("Załącznik przekracza limit 20 MB.")
        }
        val serverName = attachment.optString("filename").ifBlank { fallbackFilename }
        val safeOriginal = serverName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
            .ifBlank { "zalacznik" }
        val safeName = "${prefix}_$safeOriginal".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(cacheDir, "attachments").apply { mkdirs() }
        val file = File(directory, safeName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val extension = safeName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = attachment.optString("content_type").takeIf { it.contains("/") }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, "Otwórz załącznik"))
            setLibrusStatus("Załącznik pobrany.", true)
        } catch (_: Exception) {
            AlertDialog.Builder(this).setTitle("Załącznik pobrany")
                .setMessage("Plik zapisano w pamięci aplikacji, ale nie znaleziono aplikacji do jego otwarcia.")
                .setPositiveButton("OK", null).show()
            setLibrusStatus("Załącznik pobrany.", true)
        }
    }

    private fun downloadHomeworkAttachment(attachmentId: String, filename: String) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()
        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Pobieram załącznik zadania…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/homework/attachment").openConnection() as HttpURLConnection
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
                    put("attachment_id", attachmentId)
                    librusSessionMemory?.let { put("session", it) }
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                "$status|${readResponse(connection, status)}"
            } catch (e: Exception) {
                "ERROR|${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("ERROR|")) {
                    setLibrusStatus("Nie udało się pobrać załącznika: ${result.substringAfter("|")}", false)
                    return@runOnUiThread
                }
                val status = result.substringBefore("|").toIntOrNull() ?: 0
                val responseBody = result.substringAfter("|")
                if (status !in 200..299) {
                    val detail = errorDetail(responseBody, "HTTP $status")
                    setLibrusStatus(detail, false)
                    AlertDialog.Builder(this).setTitle("Nie udało się pobrać załącznika")
                        .setMessage(detail).setPositiveButton("OK", null).show()
                    return@runOnUiThread
                }
                try {
                    val json = JSONObject(responseBody)
                    librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                    librusVault.updateSession(librusSessionMemory?.toString())
                    val attachment = json.optJSONObject("attachment")
                        ?: throw IllegalStateException("Brak danych załącznika.")
                    saveAndOpenAttachment(attachment, filename, "homework_$attachmentId")
                } catch (e: Exception) {
                    setLibrusStatus("Błąd zapisu załącznika: ${e.message}", false)
                    AlertDialog.Builder(this).setTitle("Błąd załącznika")
                        .setMessage(e.message ?: "Nie udało się otworzyć pliku.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun downloadMessageAttachment(messageId: String, attachmentId: String, filename: String) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()
        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Pobieram załącznik…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/message/attachment").openConnection() as HttpURLConnection
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
                    put("message_id", messageId)
                    put("attachment_id", attachmentId)
                    librusSessionMemory?.let { put("session", it) }
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                "$status|${readResponse(connection, status)}"
            } catch (e: Exception) {
                "ERROR|${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("ERROR|")) {
                    setLibrusStatus("Nie udało się pobrać załącznika: ${result.substringAfter("|")}", false)
                    return@runOnUiThread
                }
                val status = result.substringBefore("|").toIntOrNull() ?: 0
                val responseBody = result.substringAfter("|")
                if (status !in 200..299) {
                    val detail = errorDetail(responseBody, "HTTP $status")
                    setLibrusStatus(detail, false)
                    AlertDialog.Builder(this).setTitle("Nie udało się pobrać załącznika")
                        .setMessage(detail).setPositiveButton("OK", null).show()
                    return@runOnUiThread
                }
                try {
                    val json = JSONObject(responseBody)
                    librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                    librusVault.updateSession(librusSessionMemory?.toString())
                    val attachment = json.optJSONObject("attachment")
                        ?: throw IllegalStateException("Brak danych załącznika.")
                    saveAndOpenAttachment(
                        attachment,
                        filename,
                        "message_${messageId}_${attachmentId}"
                    )
                } catch (e: Exception) {
                    setLibrusStatus("Błąd zapisu załącznika: ${e.message}", false)
                    AlertDialog.Builder(this).setTitle("Błąd załącznika")
                        .setMessage(e.message ?: "Nie udało się otworzyć pliku.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun showReplyComposer(messageId: String, mailbox: String, title: String, sender: String) {
        val density = resources.displayMetrics.density
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (8 * density).toInt(),
                (18 * density).toInt(), (12 * density).toInt())
        }
        form.addView(label("Do: $sender", 13f, muted, true))
        form.addView(label("Wątek: $title", 12f, accentColor))
        val bodyInput = EditText(this).apply {
            hint = "Treść odpowiedzi"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            maxLines = 9
            gravity = Gravity.TOP or Gravity.START
            setText("Dzień dobry,\n\n")
            setTextColor(white)
            setHintTextColor(muted)
            setPadding((14 * density).toInt(), (12 * density).toInt(),
                (14 * density).toInt(), (12 * density).toInt())
            background = roundedBackground(panel, 10)
        }
        form.addView(bodyInput, params(0, 8, 0, 0))
        val dialog = AlertDialog.Builder(this).setTitle("Odpowiedz na wiadomość")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("ANULUJ", null).setPositiveButton("DALEJ", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val body = bodyInput.text.toString().trim()
                if (body.isBlank() || body == "Dzień dobry,") {
                    bodyInput.error = "Wpisz treść odpowiedzi."
                    return@setOnClickListener
                }
                dialog.dismiss()
                val confirmation = AlertDialog.Builder(this).setTitle("Potwierdź odpowiedź")
                    .setMessage("Odpowiedź zostanie wysłana do: $sender\n\nWątek: $title\n\n$body")
                    .setNegativeButton("WRÓĆ", null).setPositiveButton("WYŚLIJ", null).create()
                confirmation.setOnShowListener {
                    confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        confirmation.dismiss()
                        sendMessageReply(messageId, mailbox, sender, body)
                    }
                }
                confirmation.show()
            }
        }
        dialog.show()
    }

    private fun sendMessageReply(messageId: String, mailbox: String, sender: String, body: String) {
        val login = librusLoginMemory
        val password = librusPasswordMemory
        val token = supabase.auth.currentAccessTokenOrNull()
        if (login.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) {
            setLibrusStatus("Brak aktywnej sesji. Zaloguj się ponownie.", false)
            return
        }
        if (syncInProgress) return
        syncInProgress = true
        setLibrusStatus("Wysyłam odpowiedź…", null)

        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL("$BACKEND_URL/v1/message/reply").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 30000
                connection.readTimeout = 90000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                val payload = JSONObject().apply {
                    put("profile", "parent")
                    put("login", login)
                    put("password", password)
                    put("message_id", messageId)
                    put("mailbox", mailbox)
                    put("text", body)
                    librusSessionMemory?.let { put("session", it) }
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                "$status|${readResponse(connection, status)}"
            } catch (e: Exception) {
                "ERROR|${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                syncInProgress = false
                if (isFinishing) return@runOnUiThread
                if (result.startsWith("ERROR|")) {
                    setLibrusStatus("Nie udało się potwierdzić wysyłki odpowiedzi: ${result.substringAfter("|")}", false)
                    AlertDialog.Builder(this).setTitle("Nieznany wynik wysyłki")
                        .setMessage("Nie ponawiaj teraz odpowiedzi. Sprawdź folder Wysłane w Librusie.")
                        .setPositiveButton("OK", null).show()
                    return@runOnUiThread
                }
                val status = result.substringBefore("|").toIntOrNull() ?: 0
                val responseBody = result.substringAfter("|")
                if (status in 200..299) {
                    try {
                        val json = JSONObject(responseBody)
                        librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                        librusVault.updateSession(librusSessionMemory?.toString())
                    } catch (_: Exception) { }
                    setLibrusStatus("Odpowiedź wysłana do $sender.", true)
                    AlertDialog.Builder(this).setTitle("Odpowiedź wysłana")
                        .setMessage("Librus przyjął odpowiedź w istniejącym wątku.")
                        .setPositiveButton("OK", null).show()
                } else {
                    val detail = try {
                        val raw = JSONObject(responseBody).opt("detail")
                        if (raw is JSONObject) raw.optString("message", raw.toString())
                        else raw?.toString() ?: "HTTP $status"
                    } catch (_: Exception) { "HTTP $status" }
                    setLibrusStatus(detail, false)
                    AlertDialog.Builder(this).setTitle(if (status == 502) "Sprawdź folder Wysłane" else "Nie wysłano odpowiedzi")
                        .setMessage(detail).setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun showMessageDetails(
        title: String,
        sender: String,
        date: String,
        body: String,
        messageId: String = "",
        mailbox: String = "inbox",
        noReply: Boolean = true,
        attachments: JSONArray? = null
    ) {
        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (12 * density).toInt(),
                (20 * density).toInt(),
                (20 * density).toInt()
            )
            setBackgroundColor(bgColor)
        }

        if (sender.isNotBlank()) {
            content.addView(label("Nadawca: $sender", 13f, muted))
        }
        if (date.isNotBlank()) {
            content.addView(label(date, 12f, successColor))
        }

        content.addView(View(this).apply {
            setBackgroundColor(panelLight)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (1 * density).toInt()
        ).apply {
            topMargin = (10 * density).toInt()
            bottomMargin = (10 * density).toInt()
        })

        val messageText = TextView(this).apply {
            text = body
            textSize = 16f
            setTextColor(white)
            setPadding(0, (8 * density).toInt(), 0, (16 * density).toInt())
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.2f)
            gravity = Gravity.TOP or Gravity.START
            linksClickable = true
        }

        content.addView(messageText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        if (attachments != null && attachments.length() > 0 && messageId.isNotBlank()) {
            content.addView(sectionHeader("ZAŁĄCZNIKI"), params(0, 14, 0, 6))
            for (index in 0 until attachments.length()) {
                val attachment = attachments.optJSONObject(index) ?: continue
                val attachmentId = firstValue(attachment, "id")
                val filename = firstValue(attachment, "filename").ifBlank { "Załącznik ${index + 1}" }
                if (attachmentId.isNotBlank()) {
                    content.addView(actionButton("POBIERZ • $filename") {
                        downloadMessageAttachment(messageId, attachmentId, filename)
                    }, params(0, 0, 0, 6))
                }
            }
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            setBackgroundColor(bgColor)
            addView(content)
        }

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(title.ifBlank { "Wiadomość" })
            .setView(scrollView)
        if (messageId.isNotBlank() && !noReply) {
            dialogBuilder.setNeutralButton("ODPOWIEDZ", null)
        }
        val dialog = dialogBuilder.setPositiveButton("ZAMKNIJ", null).create()
        dialog.setOnShowListener {
            if (messageId.isNotBlank() && !noReply) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    dialog.dismiss()
                    showReplyComposer(messageId, mailbox, title, sender)
                }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(roundedBackground(bgColor, 16))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.78f).toInt()
        )
    }

    // ---------------------------------------------------------
    // PROFILE I SŁOWNIKI
    // ---------------------------------------------------------

    private fun renderProfileSection(parent: LinearLayout, data: JSONObject) {
        val keys = listOf(
            "me",
            "student",
            "students",
            "classes",
            "subjects",
            "teachers",
            "classrooms"
        )

        val headings = mapOf(
            "me" to "Dane konta",
            "student" to "Uczeń",
            "students" to "Uczniowie",
            "classes" to "Klasy",
            "subjects" to "Przedmioty",
            "teachers" to "Nauczyciele",
            "classrooms" to "Sale lekcyjne"
        )

        var found = false

        for (key in keys) {
            val value = data.opt(key) ?: continue
            if (value == JSONObject.NULL || value.toString() == "{}" ||
                value.toString() == "[]"
            ) continue

            found = true
            parent.addView(
                sectionHeader(headings[key] ?: key),
                params(0, 18, 0, 8)
            )

            when (value) {
                is JSONObject -> renderObjectCard(parent, value, null, null)
                is JSONArray -> renderGenericArray(parent, value)
                else -> parent.addView(infoCard(readableValue(value)))
            }
        }

        if (!found) parent.addView(infoCard("Brak danych profilu."))
    }

    // ---------------------------------------------------------
    // FUNKCJE SZKOLNE: ZADANIA, TERMINARZ, UWAGI I NUMEREK
    // ---------------------------------------------------------

    private fun renderFeatureArray(
        parent: LinearLayout,
        data: JSONObject,
        keys: List<String>,
        heading: String
    ) {
        val key = keys.firstOrNull { data.has(it) && data.opt(it) != JSONObject.NULL }
        val value = key?.let { data.opt(it) }

        if (value == null) {
            parent.addView(infoCard("Backend nie zwrócił sekcji „$heading”."))
            return
        }

        val items = when (value) {
            is JSONArray -> value
            is JSONObject -> {
                value.optJSONArray("items")
                    ?: value.optJSONArray("entries")
                    ?: JSONArray().apply { put(value) }
            }
            else -> JSONArray()
        }

        if (items.length() == 0) {
            parent.addView(infoCard("Brak wpisów: $heading."))
            return
        }

        val sorted = (0 until items.length())
            .mapNotNull { items.optJSONObject(it) }
            .sortedWith(compareBy<JSONObject> { featureDateSortKey(firstValue(it, "due_date", "deadline", "date", "start_date")) }
                .thenBy { firstValue(it, "title", "topic", "name") })

        val now = java.util.Date()
        val datedItems = sorted.count {
            firstValue(it, "due_date", "deadline", "date", "start_date").isNotBlank()
        }
        parent.addView(infoCard("Liczba wpisów: ${items.length()}" +
            if (datedItems > 0) " • z terminem: $datedItems" else ""))

        for (item in sorted) {
            val title = firstValue(item, "title", "topic", "name", "subject", "text")
                .ifBlank { heading.removeSuffix("a").removeSuffix("y") }
            val date = firstValue(item, "due_date", "deadline", "date", "start_date")
            val subjectId = firstValue(item, "subject_id", "subject")
            val subject = lookupName(data.optJSONObject("subjects"), subjectId)
                .ifBlank { item.optString("subject_name") }
            val teacherId = firstValue(item, "teacher_id")
            val teacher = lookupName(data.optJSONObject("teachers"), teacherId)
                .ifBlank { item.optString("teacher_name") }
            val body = firstValue(item, "description", "content", "body", "details", "comment", "text")
            val state = firstValue(item, "status", "state", "completion_status", "is_done")
            val dueState = featureDueState(date, now)

            val itemCard = card()
            itemCard.addView(label(title, 15f, white, true))
            if (date.isNotBlank()) {
                val dueColor = when (dueState) {
                    "Po terminie" -> errorColor
                    "Na dziś" -> warningColor
                    else -> successColor
                }
                val dueRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                dueRow.addView(statusChip("TERMIN: $date", dueColor),
                    LinearLayout.LayoutParams(0, -2, 1f))
                if (dueState.isNotBlank()) dueRow.addView(statusChip(dueState.uppercase(Locale("pl", "PL")), dueColor))
                itemCard.addView(dueRow, params(0, 5, 0, 2))
            }
            if (state.isNotBlank()) itemCard.addView(statusChip(state, featureStateColor(state)), params(0, 3, 0, 4))
            if (subject.isNotBlank()) itemCard.addView(label("Przedmiot: $subject", 12f, muted))
            if (teacher.isNotBlank()) itemCard.addView(label("Nauczyciel: $teacher", 12f, muted))
            if (body.isNotBlank() && body != title) itemCard.addView(label(body, 13f, white))
            if (heading == "Zadania domowe") {
                val attachments = item.optJSONArray("attachments")
                if (attachments != null && attachments.length() > 0) {
                    itemCard.addView(sectionHeader("ZAŁĄCZNIKI"), params(0, 8, 0, 4))
                    for (attachmentIndex in 0 until attachments.length()) {
                        val attachment = attachments.optJSONObject(attachmentIndex) ?: continue
                        val attachmentId = firstValue(attachment, "id")
                        val filename = firstValue(attachment, "filename").ifBlank {
                            "Załącznik ${attachmentIndex + 1}"
                        }
                        if (attachmentId.isNotBlank()) {
                            itemCard.addView(actionButton("POBIERZ • $filename") {
                                downloadHomeworkAttachment(attachmentId, filename)
                            }, params(0, 0, 0, 6))
                        }
                    }
                }
            }
            parent.addView(itemCard, params(0, 0, 0, 8))
        }
    }

    private fun featureDateSortKey(value: String): Long {
        val parsed = parseFeatureDate(value) ?: return Long.MAX_VALUE
        return parsed.time
    }

    private fun featureDueState(value: String, now: java.util.Date): String {
        if (value.isBlank()) return ""
        val due = parseFeatureDate(value) ?: return ""
        val day = java.text.SimpleDateFormat("yyyyMMdd", Locale.US)
        val dueDay = day.format(due)
        val today = day.format(now)
        return when {
            dueDay < today -> "Po terminie"
            dueDay == today -> "Na dziś"
            else -> "Nadchodzące"
        }
    }

    private fun parseFeatureDate(value: String): java.util.Date? {
        val normalized = value.trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd",
            "dd.MM.yyyy",
            "dd-MM-yyyy"
        )
        for (pattern in formats) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(normalized)?.let { return it }
            } catch (_: Exception) {
                // Nieznany format pozostaje bez oceny terminu — nie zgadujemy.
            }
        }
        return null
    }

    private fun featureStateColor(value: String): Int {
        val normalized = value.lowercase(Locale("pl", "PL"))
        return when {
            normalized.contains("zrob") || normalized.contains("ukończ") ||
                normalized.contains("ukoncz") || normalized == "true" -> successColor
            normalized.contains("niezrob") || normalized.contains("zaleg") ||
                normalized.contains("brak") -> errorColor
            normalized.contains("w trakcie") || normalized.contains("oczek") -> warningColor
            else -> accentColor
        }
    }

    private fun renderLuckyNumber(parent: LinearLayout, data: JSONObject) {
        val value = data.opt("lucky_number")
        if (value == null || value == JSONObject.NULL) {
            parent.addView(infoCard("Brak informacji o szczęśliwym numerku."))
            return
        }

        val card = card()
        when (value) {
            is JSONObject -> {
                val number = firstValue(value, "number", "lucky_number", "value")
                val date = firstValue(value, "date", "valid_date", "day")
                card.addView(label(number.ifBlank { readableValue(value) }, 36f, successColor, true))
                if (date.isNotBlank()) card.addView(label("Obowiązuje: $date", 13f, muted))
            }
            else -> card.addView(label(readableValue(value), 30f, successColor, true))
        }
        parent.addView(card)
    }

    // ---------------------------------------------------------
    // UNIWERSALNE RENDEROWANIE DANYCH
    // ---------------------------------------------------------

    private fun renderGenericArray(
        parent: LinearLayout,
        items: JSONArray
    ) {
        if (items.length() == 0) {
            parent.addView(infoCard("Brak wpisów."))
            return
        }

        for (i in 0 until items.length()) {
            when (val item = items.opt(i)) {
                is JSONObject -> renderObjectCard(parent, item, null, null)
                null, JSONObject.NULL -> Unit
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
        val card = card()

        val preferred = listOf(
            "name", "title", "subject", "subject_id",
            "date", "value", "type", "status",
            "description", "content", "message",
            "teacher_id", "class_id", "room"
        )

        val keys = preferred.filter { obj.has(it) } +
            obj.keys().asSequence().filter { it !in preferred }.toList()

        for (key in keys) {
            val raw = obj.opt(key) ?: continue
            if (raw == JSONObject.NULL) continue

            val value = when (key) {
                "subject_id" -> lookupName(subjects, raw.toString())
                    .ifBlank { raw.toString() }

                "teacher_id" -> lookupName(teachers, raw.toString())
                    .ifBlank { raw.toString() }

                else -> readableValue(raw)
            }

            if (value.isBlank() || value == "null" || value == "[]") continue

            card.addView(
                label(
                    "${key.replace("_", " ").replaceFirstChar { it.uppercase() }}: $value",
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

        is JSONArray -> (0 until value.length()).joinToString(", ") {
            readableValue(value.opt(it) ?: "")
        }

        JSONObject.NULL -> ""
        else -> value.toString()
    }

    // ---------------------------------------------------------
    // SPRAWDZENIE BACKENDU
    // ---------------------------------------------------------

    private fun checkBackendConnection() {
        if (!::backendStatusText.isInitialized) return

        backendStatusText.text = "Sprawdzam sesję Supabase i połączenie z serwerem…"
        backendStatusText.setTextColor(muted)

        val accessToken = supabase.auth.currentAccessTokenOrNull()

        if (accessToken.isNullOrBlank()) {
            backendStatusText.text = "Brak aktywnego tokenu. Zaloguj się ponownie."
            backendStatusText.setTextColor(errorColor)
            return
        }

        thread {
            var connection: HttpURLConnection? = null

            val result = try {
                connection = URL("$BACKEND_URL/v1/session-check")
                    .openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Accept", "application/json")

                val code = connection.responseCode
                val body = readResponse(connection, code)

                if (code in 200..299) {
                    val json = JSONObject(body)
                    val authenticated = json.optBoolean("authenticated", false)
                    val userId = json.optString("user_id", "")

                    if (json.optBoolean("ok", false) &&
                        authenticated && userId.isNotBlank()
                    ) {
                        "SUCCESS|Serwer potwierdził Twoje konto Supabase.\nID użytkownika: $userId"
                    } else {
                        "ERROR|Serwer odpowiedział, ale nie potwierdził użytkownika."
                    }
                } else {
                    "ERROR|Błąd serwera ($code): ${errorDetail(body, "HTTP $code")}"
                }
            } catch (e: Exception) {
                "ERROR|Nie udało się połączyć: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                if (!isFinishing && ::backendStatusText.isInitialized) {
                    val ok = result.startsWith("SUCCESS|")
                    backendStatusText.text = result.substringAfter("|")
                    backendStatusText.setTextColor(if (ok) successColor else errorColor)
                }
            }
        }
    }

    // ---------------------------------------------------------
    // LOGOWANIE I WYLOGOWANIE SUPABASE
    // ---------------------------------------------------------

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
                if (isFinishing) return@runOnUiThread
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4101)
        }
    }

    private fun showNotificationSettings() {
        val keys = arrayOf("grades", "messages", "notes", "behaviour_notes")
        val labels = arrayOf("Nowe oceny", "Wiadomości", "Uwagi", "Uwagi o zachowaniu")
        val prefs = getSharedPreferences("librus_notification_settings", MODE_PRIVATE)
        val checked = BooleanArray(keys.size) { prefs.getBoolean(keys[it], true) }
        AlertDialog.Builder(this)
            .setTitle("Powiadomienia Librus")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Zapisz") { _, _ ->
                prefs.edit().apply {
                    keys.forEachIndexed { index, key -> putBoolean(key, checked[index]) }
                }.apply()
            }
            .setNegativeButton("Anuluj", null)
            .setNeutralButton("Uprawnienia") { _, _ -> requestNotificationPermissionIfNeeded() }
            .show()
    }

    private fun showAppearanceSettings(activeSection: String) {
        val options = arrayOf("Niebieski — klasyczny", "Turkusowy — świeży", "Fioletowy — nowoczesny", "Bursztynowy — ciepły")
        val keys = arrayOf("blue", "teal", "violet", "amber")
        val selected = keys.indexOf(uiPrefs.getString("accent", "blue")).coerceAtLeast(0)
        val motionEnabled = uiPrefs.getBoolean("motion", true)

        AlertDialog.Builder(this)
            .setTitle("Wygląd aplikacji")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                uiPrefs.edit().putString("accent", keys[which]).apply()
                dialog.dismiss()
                if (activeSection == "START") {
                    showHome(supabase.auth.currentSessionOrNull()?.user?.email.orEmpty())
                } else {
                    loadLibrusSection(activeSection)
                }
            }
            .setNeutralButton(if (motionEnabled) "Wyłącz animacje" else "Włącz animacje") { _, _ ->
                uiPrefs.edit().putBoolean("motion", !motionEnabled).apply()
                showAppearanceSettings(activeSection)
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    private fun signOut() {
        LibrusNotificationScheduler.cancel(applicationContext)
        thread {
            val result = try {
                runBlocking { supabase.auth.signOut() }
                null
            } catch (e: Exception) {
                e.message ?: "Nie udało się wylogować."
            }

            runOnUiThread {
                if (isFinishing) return@runOnUiThread

                if (result == null) {
                    librusDataMemory = null
                    librusSessionMemory = null
                    librusLoginMemory = null
                    librusPasswordMemory = null
                    autoRestoreStarted = false
                    showLogin("Wylogowano z konta aplikacji.")
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Błąd wylogowania")
                        .setMessage(result)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    // ---------------------------------------------------------
    // DOLNA NAWIGACJA — układ inspirowany Szkolny.eu
    // ---------------------------------------------------------

    private fun buildBottomNavigation(active: String): LinearLayout {
        val density = resources.displayMetrics.density
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((7 * density).toInt(), (7 * density).toInt(), (7 * density).toInt(), (8 * density).toInt())
            setBackgroundColor(Color.rgb(18, 26, 41))
            elevation = 12f * density
        }

        // Android 15+ enforces edge-to-edge for apps targeting SDK 35.
        // Reserve the real system navigation-bar inset inside this bottom bar,
        // so app tabs stay above gesture/3-button navigation on every device.
        ViewCompat.setOnApplyWindowInsetsListener(nav) { view, insets ->
            val navigationBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom
            view.setPadding(
                (7 * density).toInt(),
                (7 * density).toInt(),
                (7 * density).toInt(),
                (8 * density).toInt() + navigationBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(nav)

        data class NavItem(val key: String, val icon: String, val title: String)
        val items = listOf(
            NavItem("START", "⌂", "Start"),
            NavItem("PLAN LEKCJI", "▦", "Plan"),
            NavItem("OCENY", "✦", "Oceny"),
            NavItem("WIADOMOŚCI", "✉", "Wiadomości"),
            NavItem("WIĘCEJ", "•••", "Więcej")
        )

        items.forEach { item ->
            val selected = when (active) {
                "START" -> item.key == "START"
                "PLAN LEKCJI" -> item.key == "PLAN LEKCJI"
                "OCENY" -> item.key == "OCENY"
                "WIADOMOŚCI" -> item.key == "WIADOMOŚCI"
                else -> item.key == "WIĘCEJ"
            }
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding((2 * density).toInt(), (4 * density).toInt(), (2 * density).toInt(), (3 * density).toInt())
                background = roundedBackground(
                    if (selected) Color.argb(48, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)) else Color.TRANSPARENT,
                    13
                )
                isClickable = true
                isFocusable = true
                alpha = if (selected) 1f else 0.82f
                scaleX = if (selected) 1.03f else 1f
                scaleY = if (selected) 1.03f else 1f
            }
            if (selected) {
                val indicator = View(this).apply {
                    background = roundedBackground(accentColor, 4)
                }
                tile.addView(indicator, LinearLayout.LayoutParams((22 * density).toInt(), (3 * density).toInt()).apply {
                    bottomMargin = (2 * density).toInt()
                })
            } else {
                tile.addView(View(this), LinearLayout.LayoutParams(1, (5 * density).toInt()))
            }
            tile.addView(TextView(this).apply {
                text = item.icon
                textSize = if (item.key == "WIĘCEJ") 17f else 21f
                gravity = Gravity.CENTER
                setTextColor(if (selected) accentColor else muted)
                setTypeface(null, Typeface.BOLD)
            })
            tile.addView(TextView(this).apply {
                text = item.title
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(if (selected) white else muted)
                if (selected) setTypeface(null, Typeface.BOLD)
                maxLines = 1
            })
            tile.setOnClickListener {
                if (uiPrefs.getBoolean("motion", true)) {
                    tile.animate().cancel()
                    tile.scaleX = 0.96f
                    tile.scaleY = 0.96f
                    tile.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }
                when (item.key) {
                    "START" -> showHome(supabase.auth.currentSessionOrNull()?.user?.email.orEmpty())
                    "WIĘCEJ" -> showMoreMenu(active)
                    else -> loadLibrusSection(item.key)
                }
            }
            nav.addView(tile, LinearLayout.LayoutParams(0, (54 * density).toInt(), 1f).apply {
                marginStart = (2 * density).toInt()
                marginEnd = (2 * density).toInt()
            })
        }
        return nav
    }

    private fun showMoreMenu(activeSection: String) {
        val labels = arrayOf(
            "Profile i nauczyciele",
            "Frekwencja",
            "Zadania domowe",
            "Terminarz",
            "Uwagi",
            "Szczęśliwy numerek",
            "Ustawienia powiadomień",
            "Wygląd aplikacji"
        )
        val sections = arrayOf(
            "PROFILE",
            "FREKWENCJA",
            "ZADANIA DOMOWE",
            "TERMINARZ",
            "UWAGI",
            "SZCZĘŚLIWY NUMEREK"
        )
        AlertDialog.Builder(this)
            .setTitle("Więcej modułów")
            .setItems(labels) { _, which ->
                when (which) {
                    6 -> showNotificationSettings()
                    7 -> showAppearanceSettings(activeSection)
                    else -> loadLibrusSection(sections[which])
                }
            }
            .show()
    }

    // ---------------------------------------------------------
    // KOMPONENTY UI
    // ---------------------------------------------------------

    private fun addHomeSection(
        parent: LinearLayout,
        heading: String,
        description: String,
        emoji: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(17, 15, 16, 15)
            minimumHeight = (76 * resources.displayMetrics.density).toInt()
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(35, 49, 72), Color.rgb(25, 35, 53))).apply {
                cornerRadius = 20f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(54, 72, 100))
            }
            isClickable = true
            isFocusable = true
            foreground = android.util.TypedValue().let {
                val out = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                getDrawable(out.resourceId)
            }
            elevation = 2f * resources.displayMetrics.density
        }

        val iconTile = TextView(this).apply {
            text = emoji
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accentColor, Color.rgb(66, 111, 190))).apply { cornerRadius = 15f * resources.displayMetrics.density }
            setPadding(8, 8, 8, 8)
        }
        card.addView(iconTile, LinearLayout.LayoutParams(48, 48))

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 0, 8, 0)
        }
        texts.addView(label(heading, 14f, white, true))
        texts.addView(label(description, 12f, Color.rgb(174, 190, 213)))

        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(label("›", 28f, Color.rgb(145, 190, 255), true))

        card.setOnClickListener { loadLibrusSection(heading) }
        card.alpha = 0f
        card.translationY = 12f * resources.displayMetrics.density
        parent.addView(card, params(0, 6, 0, 6))
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(parent.childCount.coerceAtMost(12) * 35L)
            .setDuration(320)
            .start()
    }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(31, 45, 66), Color.rgb(22, 32, 49))
            ).apply {
                cornerRadius = 18f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(49, 67, 94))
            }
            elevation = 2f * resources.displayMetrics.density
        }

    private fun sectionHeader(title: String): TextView =
        TextView(this).apply {
            text = title.uppercase(Locale("pl", "PL"))
            textSize = 13f
            setTextColor(accentColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(2, 8, 2, 5)
        }

    private fun infoCard(message: String): TextView =
        TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.rgb(218, 228, 243))
            setLineSpacing(2f, 1.05f)
            setPadding(17, 15, 17, 15)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(33, 51, 77), Color.rgb(25, 38, 59))
            ).apply {
                cornerRadius = 16f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(), blendColor(accentColor, Color.rgb(61, 85, 119), 0.62f))
            }
        }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, 5, 0, 5)
    }

    private fun inputField(hintText: String, type: Int): EditText =
        EditText(this).apply {
            hint = hintText
            setSingleLine(true)
            inputType = type
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(15, 12, 15, 12)
            background = roundedBackground(panel, 10)
        }

    private fun actionButton(
        title: String,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.WHITE)
        minHeight = (48 * resources.displayMetrics.density).toInt()
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(accentColor, blendColor(accentColor, Color.rgb(30, 48, 78), 0.55f))
        ).apply { cornerRadius = 14f * resources.displayMetrics.density }
        elevation = 2f * resources.displayMetrics.density
        setOnClickListener { action() }
    }

    private fun attendanceStatusCategory(value: String): String {
        val normalized = value.lowercase(Locale("pl", "PL"))
        return when {
            normalized.contains("nieobec") -> "Nieobecności"
            normalized.contains("spóź") || normalized.contains("spoz") -> "Spóźnienia"
            normalized.contains("zwoln") -> "Zwolnienia"
            normalized.contains("obec") -> "Obecności"
            else -> "Pozostałe"
        }
    }

    private fun attendanceStatusColor(value: String): Int {
        val normalized = value.lowercase(Locale("pl", "PL"))
        return when {
            normalized.contains("nieobec") -> errorColor
            normalized.contains("spóź") || normalized.contains("spoz") -> warningColor
            normalized.contains("zwoln") -> Color.rgb(116, 174, 255)
            normalized.contains("obec") -> successColor
            else -> accentColor
        }
    }

    private fun statusChip(value: String, tint: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(tint)
            setPadding((9 * resources.displayMetrics.density).toInt(), (5 * resources.displayMetrics.density).toInt(),
                (9 * resources.displayMetrics.density).toInt(), (5 * resources.displayMetrics.density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.argb(35, Color.red(tint), Color.green(tint), Color.blue(tint)))
                cornerRadius = 20f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(),
                    Color.argb(100, Color.red(tint), Color.green(tint), Color.blue(tint)))
            }
        }

    private fun blendColor(from: Int, to: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        return Color.rgb(
            (Color.red(from) * inverse + Color.red(to) * ratio).toInt(),
            (Color.green(from) * inverse + Color.green(to) * ratio).toInt(),
            (Color.blue(from) * inverse + Color.blue(to) * ratio).toInt()
        )
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
        }

    private fun params(l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            val d = resources.displayMetrics.density
            setMargins(
                (l * d).toInt(),
                (t * d).toInt(),
                (r * d).toInt(),
                (b * d).toInt()
            )
        }

    // ---------------------------------------------------------
    // FUNKCJE POMOCNICZE
    // ---------------------------------------------------------

    private fun setLibrusStatus(message: String, success: Boolean?) {
        if (!::librusStatusText.isInitialized) return
        librusStatusText.text = message
        librusStatusText.setTextColor(
            when (success) {
                true -> successColor
                false -> errorColor
                null -> muted
            }
        )
    }

    private fun readResponse(
        connection: HttpURLConnection,
        code: Int
    ): String {
        return if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
    }

    private fun errorDetail(body: String, fallback: String): String =
        try {
            JSONObject(body).optString("detail", fallback)
        } catch (_: Exception) {
            fallback
        }

    private fun firstValue(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.opt(key)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private fun lookupName(dictionary: JSONObject?, id: String): String {
        if (id.isBlank() || dictionary == null) return ""

        val direct = dictionary.opt(id)

        return when (direct) {
            is JSONObject -> firstValue(direct, "name", "subject_name", "teacher_name", "value")
            is String -> direct
            null, JSONObject.NULL -> {
                var found = ""
                val iterator = dictionary.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val item = dictionary.optJSONObject(key) ?: continue
                    val itemId = firstValue(item, "id", "subject_id", "teacher_id", "classroom_id")
                    if (itemId == id) {
                        found = firstValue(item, "name", "title", "value")
                        break
                    }
                }
                found
            }
            else -> direct.toString()
        }
    }

    private fun dateLabel(date: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = input.parse(date) ?: return date
            val output = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("pl", "PL"))
            output.format(parsed).replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            date
        }
    }

    private fun gradeColor(value: String): Int {
        // Koloruj wyłącznie jednoznaczne oceny liczbowe. Opisów typu
        // "5 umiejętności opanowanych" nie klasyfikuj jak stopnia 5.
        val normalized = value.trim()
        if (!normalized.matches(Regex("^[1-6][+-]?$"))) return white
        return when (normalized.first()) {
            '5', '6' -> successColor
            '1', '2' -> errorColor
            else -> white
        }
    }

    private fun firstDisplayValue(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val rendered = displayJsonValue(obj.opt(key))
            if (rendered.isNotBlank()) return rendered
        }
        return ""
    }

    private fun displayJsonValue(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> ""
            is JSONObject -> {
                val preferred = arrayOf(
                    "value", "text", "name", "title", "description",
                    "content", "label", "skill", "comment", "result"
                )
                for (key in preferred) {
                    if (value.has(key) && !value.isNull(key)) {
                        val rendered = displayJsonValue(value.opt(key))
                        if (rendered.isNotBlank()) return rendered
                    }
                }
                val parts = mutableListOf<String>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val rendered = displayJsonValue(value.opt(key))
                    if (rendered.isNotBlank()) parts.add(rendered)
                }
                parts.distinct().joinToString(" · ")
            }
            is JSONArray -> {
                val parts = mutableListOf<String>()
                for (i in 0 until value.length()) {
                    val rendered = displayJsonValue(value.opt(i))
                    if (rendered.isNotBlank()) parts.add(rendered)
                }
                parts.distinct().joinToString(" · ")
            }
            else -> value.toString().trim().takeUnless {
                it.isBlank() || it == "null"
            }.orEmpty()
        }
    }

    private fun appendJsonDetails(
        parent: LinearLayout,
        grade: JSONObject,
        key: String,
        heading: String
    ) {
        if (!grade.has(key) || grade.isNull(key)) return
        val raw = grade.opt(key)
        val values = when (raw) {
            is JSONArray -> (0 until raw.length()).map { displayJsonValue(raw.opt(it)) }
            else -> listOf(displayJsonValue(raw))
        }
        values.filter { it.isNotBlank() }.distinct().forEach { value ->
            parent.addView(label("$heading: $value", 12f, muted))
        }
    }

    override fun onDestroy() {
        timetableTicker?.let { android.os.Handler(mainLooper).removeCallbacks(it) }
        timetableTicker = null
        activityScope.cancel()
        super.onDestroy()
    }

}

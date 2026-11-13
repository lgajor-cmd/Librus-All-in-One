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
import android.text.InputType
import android.view.Gravity
import android.view.View
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
import org.json.JSONArray
import org.json.JSONObject
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

        val homeScroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(content)
        }
        installNavigationDrawer(homeScroll, "PULPIT")
    }

    // ---------------------------------------------------------
    // EKRAN GŁÓWNY
    // ---------------------------------------------------------

    private fun showHome(email: String) {
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

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(content)
        })

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
    // GLOBALNE MENU BOCZNE
    // ---------------------------------------------------------

    private fun installNavigationDrawer(screen: View, activeSection: String) {
        val density = resources.displayMetrics.density
        val frame = android.widget.FrameLayout(this).apply {
            setBackgroundColor(bgColor)
        }
        frame.addView(screen, android.widget.FrameLayout.LayoutParams(-1, -1))

        val scrim = View(this).apply {
            setBackgroundColor(Color.argb(165, 0, 0, 0))
            alpha = 0f
            visibility = View.GONE
        }
        frame.addView(scrim, android.widget.FrameLayout.LayoutParams(-1, -1))

        val drawerWidth = (minOf(320f, resources.displayMetrics.widthPixels / density - 52f) * density).toInt()
        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (28 * density).toInt(), (16 * density).toInt(), (20 * density).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(29, 48, 78), Color.rgb(17, 25, 41), Color.rgb(13, 18, 30))
            ).apply {
                cornerRadii = floatArrayOf(0f, 0f, 22f*density, 22f*density, 22f*density, 22f*density, 0f, 0f)
                setStroke((1*density).toInt(), Color.rgb(65, 91, 130))
            }
            elevation = 18f * density
            translationX = -drawerWidth.toFloat()
        }
        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 18)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(45, 91, 157), Color.rgb(31, 53, 88))).apply {
                cornerRadius = 20f * density
            }
        }
        drawerHeader.addView(label("SP14  •  LIBRUS", 11f, Color.rgb(198, 220, 255), true))
        drawerHeader.addView(label("Centrum szkolne", 23f, white, true), params(0, 6, 0, 2))
        drawerHeader.addView(label("Wybierz miejsce, do którego chcesz przejść", 12f, Color.rgb(211, 222, 239)))
        drawer.addView(drawerHeader, params(0, 0, 0, 18))

        fun drawerItem(title: String, subtitle: String, icon: String, section: String, tint: Int) {
            val selected = activeSection == section
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 10, 10, 10)
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    if (selected) intArrayOf(Color.rgb(48, 83, 132), Color.rgb(35, 59, 91))
                    else intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)).apply {
                    cornerRadius = 15f * density
                    if (selected) setStroke((1*density).toInt(), Color.rgb(91, 139, 205))
                }
                isClickable = true
                isFocusable = true
            }
            val iconView = TextView(this).apply {
                text = icon
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedBackground(tint, 12)
                setPadding(8, 8, 8, 8)
            }
            item.addView(iconView, LinearLayout.LayoutParams((42*density).toInt(), (42*density).toInt()))
            val copy = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 0, 0, 0)
            }
            copy.addView(label(title, 14f, white, true))
            copy.addView(label(subtitle, 11f, Color.rgb(165, 183, 208)))
            item.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            if (selected) item.addView(label("●", 10f, Color.rgb(117, 190, 255), true))
            drawer.addView(item, params(0, 3, 0, 3))
            item.setOnClickListener {
                drawer.animate().translationX(-drawerWidth.toFloat()).setDuration(220).start()
                scrim.animate().alpha(0f).setDuration(180).withEndAction { scrim.visibility = View.GONE }.start()
                if (section == "PULPIT") showHome(supabase.auth.currentSessionOrNull()?.user?.email.orEmpty())
                else if (section == "USTAWIENIA") showAppSettings()
                else if (section == "POWIADOMIENIA") showNotificationSettings()
                else loadLibrusSection(section)
            }
        }

        drawerItem("Pulpit", "Ekran główny", "⌂", "PULPIT", Color.rgb(55, 111, 190))
        drawer.addView(sectionHeader("DZIENNIK"), params(0, 13, 0, 5))
        drawerItem("Profil ucznia", "Konto i dane", "👤", "PROFILE", Color.rgb(65, 112, 176))
        drawerItem("Oceny", "Postępy i przedmioty", "✦", "OCENY", Color.rgb(38, 139, 119))
        drawerItem("Frekwencja", "Obecności i spóźnienia", "◷", "FREKWENCJA", Color.rgb(124, 93, 181))
        drawerItem("Plan lekcji", "Dni i godziny zajęć", "▦", "PLAN LEKCJI", Color.rgb(53, 126, 159))
        drawerItem("Wiadomości", "Skrzynka i korespondencja", "✉", "WIADOMOŚCI", Color.rgb(190, 126, 61))
        drawerItem("Zadania domowe", "Prace i materiały", "▤", "ZADANIA DOMOWE", Color.rgb(78, 128, 183))
        drawerItem("Terminarz", "Wydarzenia i sprawdziany", "◷", "TERMINARZ", Color.rgb(157, 104, 153))
        drawerItem("Uwagi", "Informacje o zachowaniu", "✎", "UWAGI", Color.rgb(177, 91, 103))
        drawerItem("Szczęśliwy numerek", "Numer na dziś", "🍀", "SZCZĘŚLIWY NUMEREK", Color.rgb(73, 143, 99))
        drawer.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        drawer.addView(View(this).apply { setBackgroundColor(Color.rgb(55, 73, 101)) },
            LinearLayout.LayoutParams(-1, (1*density).toInt()))
        drawerItem("Ustawienia", "Wygląd i działanie aplikacji", "⚙", "USTAWIENIA", Color.rgb(91, 105, 132))
        drawerItem("Powiadomienia", "Wybierz, co monitorować", "♧", "POWIADOMIENIA", Color.rgb(65, 130, 151))

        frame.addView(drawer, android.widget.FrameLayout.LayoutParams(drawerWidth, -1, Gravity.START))
        fun openDrawer() {
            scrim.visibility = View.VISIBLE
            scrim.animate().alpha(1f).setDuration(180).start()
            drawer.animate().translationX(0f).setDuration(260).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
        fun closeDrawer() {
            drawer.animate().translationX(-drawerWidth.toFloat()).setDuration(220).setInterpolator(android.view.animation.AccelerateInterpolator()).start()
            scrim.animate().alpha(0f).setDuration(180).withEndAction { scrim.visibility = View.GONE }.start()
        }
        scrim.setOnClickListener { closeDrawer() }

        val menuButton = TextView(this).apply {
            text = "☰  MENU"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(15, 10, 15, 10)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(accentColor, Color.rgb(54, 105, 170))).apply { cornerRadius = 18f*density }
            elevation = 5f*density
            setOnClickListener { openDrawer() }
        }
        val menuParams = android.widget.FrameLayout.LayoutParams(-2, (46*density).toInt(), Gravity.TOP or Gravity.START).apply {
            leftMargin = (16*density).toInt()
            topMargin = (14*density).toInt()
        }
        frame.addView(menuButton, menuParams)
        setContentView(frame)
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
            "ZADANIA DOMOWE" -> renderFeatureArray(root, data, listOf("homework", "homeworks"), "Zadania domowe")
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
        installNavigationDrawer(scroll, section)
        root.alpha = 0f
        root.translationY = 12f * resources.displayMetrics.density
        root.animate().alpha(1f).translationY(0f).setDuration(280).start()
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

        parent.addView(
            infoCard("Znaleziono ${lessons.size} lekcji w ${grouped.size} dniach.")
        )

        for ((date, dayLessons) in grouped) {
            val dayCard = card()

            val dayTitle = dateLabel(date)
            dayCard.addView(label(dayTitle, 18f, white, true))
            dayCard.addView(
                label("${dayLessons.size} zaplanowanych lekcji", 12f, muted)
            )

            for (lesson in dayLessons.sortedWith(
                compareBy<TimetableLesson> { it.from.ifBlank { "99:99" } }
                    .thenBy { it.number }
            )) {
                dayCard.addView(renderLessonCard(lesson), params(0, 10, 0, 0))
            }

            parent.addView(dayCard, params(0, 10, 0, 4))
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

        val title = when {
            lesson.canceled -> "${lesson.subject} • ODWOŁANA"
            lesson.substitution -> "${lesson.subject} • ZASTĘPSTWO"
            else -> lesson.subject
        }

        details.addView(
            label(
                title,
                15f,
                if (lesson.canceled) errorColor else white,
                true
            )
        )

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
                val value = firstValue(grade, "value", "grade", "symbol")
                    .ifBlank { "—" }
                val date = firstValue(grade, "date", "add_date").take(10)
                val description = firstValue(grade, "skill", "description", "comment")
                val teacherId = firstValue(grade, "teacher_id")
                val teacher = lookupName(teachers, teacherId)
                    .ifBlank { grade.optString("teacher_name") }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 9, 0, 9)
                }

                row.addView(
                    label(value, 23f, gradeColor(value), true),
                    LinearLayout.LayoutParams(54, -2)
                )

                val details = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }

                if (date.isNotBlank()) details.addView(label(date, 12f, muted))
                if (description.isNotBlank()) {
                    details.addView(label(description, 13f, white))
                }
                if (teacher.isNotBlank()) {
                    details.addView(label("Nauczyciel: $teacher", 11f, muted))
                }

                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                subjectCard.addView(row)

                appendStringArray(subjectCard, grade.optJSONArray("comments"), "Komentarz")
                appendStringArray(subjectCard, grade.optJSONArray("requirements"), "Wymaganie")
            }

            parent.addView(subjectCard, params(0, 0, 0, 10))
        }
    }

    // ---------------------------------------------------------
    // FREKWENCJA
    // ---------------------------------------------------------

    private fun renderAttendanceSection(parent: LinearLayout, data: JSONObject) {
        val types = data.optJSONObject("attendance_types")
        val array = data.optJSONArray("attendances")

        if (array == null || array.length() == 0) {
            parent.addView(infoCard("Brak wpisów frekwencji."))
            return
        }

        parent.addView(infoCard("Liczba wpisów: ${array.length()}"))

        val sorted = (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .sortedByDescending { firstValue(it, "date", "lesson_date", "created_at") }

        for (item in sorted) {
            val date = firstValue(item, "date", "lesson_date")
            val typeId = firstValue(item, "type_id", "attendance_type_id", "type")
            val type = lookupName(types, typeId)
                .ifBlank { item.optString("type_name").ifBlank { typeId } }

            val subject = firstValue(item, "subject", "subject_name")
            val lesson = firstValue(item, "lesson_no", "lesson_number")
            val description = firstValue(item, "description", "comment", "note")

            val card = card()
            card.addView(label(type.ifBlank { "Wpis frekwencji" }, 15f, white, true))
            if (date.isNotBlank()) card.addView(label(date, 13f, successColor))
            if (subject.isNotBlank()) card.addView(label("Przedmiot: $subject", 12f, muted))
            if (lesson.isNotBlank()) card.addView(label("Lekcja: $lesson", 12f, muted))
            if (description.isNotBlank()) card.addView(label(description, 12f, muted))

            parent.addView(card, params(0, 0, 0, 8))
        }
    }

    // ---------------------------------------------------------
    // WIADOMOŚCI I OGŁOSZENIA
    // ---------------------------------------------------------

    private fun renderMessagesSection(parent: LinearLayout, data: JSONObject) {
        renderMessageArray(parent, data.optJSONArray("messages"), "Wiadomości")
        renderMessageArray(parent, data.optJSONArray("announcements"), "Ogłoszenia")
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
                        fetchFullMessage(messageId, title, sender, date)
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
        date: String
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
                    if (fullBody.isBlank()) {
                        "ERROR|Librus nie zwrócił treści tej wiadomości."
                    } else {
                        librusSessionMemory = json.optJSONObject("session") ?: librusSessionMemory
                        librusVault.updateSession(librusSessionMemory?.toString())
                        "SUCCESS|$fullBody"
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
                    showMessageDetails(title, sender, date, result.substringAfter("|"))
                } else {
                    setLibrusStatus(result.substringAfter("|"), false)
                }
            }
        }
    }

    private fun showMessageDetails(
        title: String,
        sender: String,
        date: String,
        body: String
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

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            setBackgroundColor(bgColor)
            addView(content)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title.ifBlank { "Wiadomość" })
            .setView(scrollView)
            .setPositiveButton("ZAMKNIJ", null)
            .create()

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

        parent.addView(infoCard("Liczba wpisów: ${items.length()}"))
        val sorted = (0 until items.length())
            .mapNotNull { items.optJSONObject(it) }
            .sortedByDescending {
                firstValue(it, "date", "due_date", "deadline", "start_date", "created_at")
            }

        for (item in sorted) {
            val title = firstValue(item, "title", "topic", "name", "subject", "text")
                .ifBlank { heading.removeSuffix("a").removeSuffix("y") }
            val date = firstValue(item, "date", "due_date", "deadline", "start_date")
            val subjectId = firstValue(item, "subject_id", "subject")
            val subject = lookupName(data.optJSONObject("subjects"), subjectId)
                .ifBlank { item.optString("subject_name") }
            val teacherId = firstValue(item, "teacher_id")
            val teacher = lookupName(data.optJSONObject("teachers"), teacherId)
                .ifBlank { item.optString("teacher_name") }
            val body = firstValue(item, "description", "content", "body", "details", "comment")

            val itemCard = card()
            itemCard.addView(label(title, 15f, white, true))
            if (date.isNotBlank()) itemCard.addView(label("Termin: $date", 13f, successColor))
            if (subject.isNotBlank()) itemCard.addView(label("Przedmiot: $subject", 12f, muted))
            if (teacher.isNotBlank()) itemCard.addView(label("Nauczyciel: $teacher", 12f, muted))
            if (body.isNotBlank() && body != title) itemCard.addView(label(body, 13f, white))
            parent.addView(itemCard, params(0, 0, 0, 8))
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
            setTextColor(successColor)
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
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(61, 85, 119))
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
            intArrayOf(Color.rgb(56, 112, 194), Color.rgb(43, 83, 145))
        ).apply { cornerRadius = 14f * resources.displayMetrics.density }
        elevation = 2f * resources.displayMetrics.density
        setOnClickListener { action() }
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
        val normalized = value.trim()
        return when {
            normalized.startsWith("6") || normalized.startsWith("5") ||
                normalized.startsWith("+5") -> successColor
            normalized.startsWith("1") || normalized.startsWith("2") ->
                errorColor
            else -> white
        }
    }

    private fun appendStringArray(
        parent: LinearLayout,
        array: JSONArray?,
        heading: String
    ) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank() && value != "null") {
                parent.addView(label("$heading: $value", 12f, muted))
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

}

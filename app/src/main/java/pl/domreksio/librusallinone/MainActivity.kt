package pl.domreksio.librusallinone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Toast
import android.util.Base64
import android.webkit.MimeTypeMap
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import kotlin.concurrent.thread

private const val SUPABASE_URL =
    "https://alegkhpqxdzuqjuuoawp.supabase.co"

private const val SUPABASE_PUBLISHABLE_KEY =
    "sb_publishable_gchAnvMxlFgvzdenD0QqWg_YoOtsTPe"

internal const val BACKEND_URL =
    "https://librus-all-in-one-api.onrender.com"

internal val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_PUBLISHABLE_KEY
) {
    install(Auth) {}
}

class MainActivity : Activity() {

    private val activityScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var backendStatusText: TextView

    private lateinit var librusLoginInput: EditText
    private lateinit var librusPasswordInput: EditText
    private lateinit var librusStatusText: TextView
    private lateinit var librusConnectButton: Button

    private lateinit var librusVault: LibrusCredentialVault
    private lateinit var profileStore: LibrusProfileStore
    private var activeProfileId: String = LibrusProfileStore.LEGACY_PROFILE_ID
    private var activeProfileType: String = LibrusProfileStore.TYPE_PARENT

    private var librusLoginMemory: String? = null
    private var librusPasswordMemory: String? = null
    private var librusSessionMemory: JSONObject? = null
    private var librusDataMemory: JSONObject? = null

    private var syncInProgress = false
    private var timetableTicker: Runnable? = null
    private var autoRestoreStarted = false
    private var initialRouteHandled = false
    private var pendingNotificationSection: String? = null
    private var anonymousSignInInProgress = false
    private var pendingTimelineTargetKey: String? = null

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
            "red" -> Color.rgb(220, 65, 78)
            "christmas" -> Color.rgb(58, 166, 112)
            "halloween" -> Color.rgb(235, 126, 48)
            "pink" -> Color.rgb(220, 105, 165)
            "spring" -> Color.rgb(145, 190, 75)
            else -> Color.rgb(105, 165, 255)
        }
    private val warningColor = Color.rgb(255, 190, 95)

    // Delikatne palety tła dopasowane do wybranego motywu.
    // Zachowujemy ciemny, czytelny interfejs — zmienia się klimat, nie kontrast tekstu.
    private val pageGradient: IntArray
        get() = when (uiPrefs.getString("accent", "blue")) {
            "red" -> intArrayOf(Color.rgb(35, 19, 29), Color.rgb(19, 17, 27), Color.rgb(12, 15, 25))
            "christmas" -> intArrayOf(Color.rgb(15, 39, 34), Color.rgb(15, 25, 29), Color.rgb(10, 16, 24))
            "halloween" -> intArrayOf(Color.rgb(43, 29, 20), Color.rgb(23, 21, 25), Color.rgb(12, 15, 25))
            "pink" -> intArrayOf(Color.rgb(39, 22, 38), Color.rgb(22, 19, 30), Color.rgb(12, 15, 25))
            "spring" -> intArrayOf(Color.rgb(29, 39, 25), Color.rgb(19, 26, 27), Color.rgb(10, 16, 24))
            "teal" -> intArrayOf(Color.rgb(17, 39, 43), Color.rgb(17, 25, 32), Color.rgb(10, 16, 24))
            "violet" -> intArrayOf(Color.rgb(33, 25, 48), Color.rgb(21, 21, 34), Color.rgb(10, 16, 24))
            "amber" -> intArrayOf(Color.rgb(43, 34, 23), Color.rgb(24, 24, 30), Color.rgb(10, 16, 24))
            else -> intArrayOf(Color.rgb(15, 24, 42), bgColor, Color.rgb(10, 15, 26))
        }

    private val heroGradient: IntArray
        get() = when (uiPrefs.getString("accent", "blue")) {
            "red" -> intArrayOf(Color.rgb(105, 37, 51), Color.rgb(48, 29, 43), Color.rgb(27, 30, 46))
            "christmas" -> intArrayOf(Color.rgb(28, 91, 67), Color.rgb(25, 48, 51), Color.rgb(22, 31, 48))
            "halloween" -> intArrayOf(Color.rgb(117, 65, 29), Color.rgb(54, 38, 35), Color.rgb(27, 30, 46))
            "pink" -> intArrayOf(Color.rgb(103, 48, 91), Color.rgb(48, 31, 54), Color.rgb(27, 30, 46))
            "spring" -> intArrayOf(Color.rgb(66, 94, 43), Color.rgb(39, 53, 43), Color.rgb(22, 31, 48))
            "teal" -> intArrayOf(Color.rgb(28, 91, 91), Color.rgb(25, 48, 58), Color.rgb(22, 31, 48))
            "violet" -> intArrayOf(Color.rgb(76, 57, 120), Color.rgb(39, 35, 64), Color.rgb(22, 31, 48))
            "amber" -> intArrayOf(Color.rgb(112, 78, 35), Color.rgb(53, 43, 39), Color.rgb(22, 31, 48))
            else -> intArrayOf(Color.rgb(28, 57, 104), Color.rgb(25, 34, 58), Color.rgb(22, 31, 48))
        }

    private val surfaceGradient: IntArray
        get() = when (uiPrefs.getString("accent", "blue")) {
            "red" -> intArrayOf(Color.rgb(58, 36, 48), Color.rgb(30, 29, 43))
            "christmas" -> intArrayOf(Color.rgb(30, 58, 51), Color.rgb(24, 35, 43))
            "halloween" -> intArrayOf(Color.rgb(63, 47, 36), Color.rgb(32, 30, 39))
            "pink" -> intArrayOf(Color.rgb(58, 39, 58), Color.rgb(31, 29, 43))
            "spring" -> intArrayOf(Color.rgb(43, 57, 39), Color.rgb(27, 36, 39))
            "teal" -> intArrayOf(Color.rgb(31, 57, 62), Color.rgb(24, 35, 45))
            "violet" -> intArrayOf(Color.rgb(47, 42, 67), Color.rgb(27, 29, 45))
            "amber" -> intArrayOf(Color.rgb(59, 49, 39), Color.rgb(31, 31, 43))
            else -> intArrayOf(Color.rgb(35, 49, 72), Color.rgb(25, 35, 53))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        profileStore = LibrusProfileStore(applicationContext)
        val defaultProfile = profileStore.ensureDefaultProfile()
        val notificationProfileId = intent?.getStringExtra("profile_id")
        val initialProfile = notificationProfileId?.let { profileStore.getProfile(it) } ?: defaultProfile
        if (initialProfile.id != defaultProfile.id) {
            profileStore.setActiveProfileId(initialProfile.id)
        }
        activeProfileId = initialProfile.id
        activeProfileType = initialProfile.accountType
        librusVault = LibrusCredentialVault(applicationContext, activeProfileId)
        pendingNotificationSection = intent?.getStringExtra("open_librus_section")
        requestNotificationPermissionIfNeeded()
        showLogin("Uruchamiam bezpieczną sesję urządzenia…")
        activityScope.launch {
            supabase.auth.sessionStatus.collect { state ->
                when (state) {
                    is SessionStatus.Authenticated -> {
                        if (!initialRouteHandled) {
                            initialRouteHandled = true
                            val saved = librusVault.load()
                            if (saved != null) {
                                librusLoginMemory = saved.login
                                librusPasswordMemory = saved.password
                                librusSessionMemory = saved.sessionJson?.let {
                                    runCatching { JSONObject(it) }.getOrNull()
                                }
                                showHome()
                            } else {
                                showLibrusLogin()
                            }
                        }
                    }
                    SessionStatus.Initializing -> showLogin("Sprawdzam bezpieczną sesję urządzenia…")
                    is SessionStatus.NotAuthenticated -> ensureAnonymousSession()
                    is SessionStatus.RefreshFailure -> ensureAnonymousSession(replaceExisting = true)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requestedSection = intent.getStringExtra("open_librus_section") ?: return
        val requestedProfileId = intent.getStringExtra("profile_id")
        if (!requestedProfileId.isNullOrBlank() && requestedProfileId != activeProfileId) {
            val requestedProfile = profileStore.getProfile(requestedProfileId)
            if (requestedProfile != null) {
                if (syncInProgress) {
                    Toast.makeText(this, "Zakończ najpierw bieżącą operację.", Toast.LENGTH_SHORT).show()
                    return
                }
                activateProfile(requestedProfile, requestedSection)
                return
            }
        }
        pendingNotificationSection = requestedSection

        // A notification tap must navigate even when MainActivity is already alive.
        // If credentials are still restoring, showHome() will consume the pending section.
        if (!librusLoginMemory.isNullOrBlank() && !librusPasswordMemory.isNullOrBlank()) {
            pendingNotificationSection = null
            loadLibrusSection(requestedSection)
        }
    }

    // ---------------------------------------------------------
    // LOGOWANIE DO APLIKACJI
    // ---------------------------------------------------------

    private fun showLogin(message: String) {
        if (isFinishing) return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 40, 28, 40)
            setBackgroundColor(bgColor)
        }
        content.addView(label("EDUFLOW", 13f, muted, true))
        content.addView(label("Witaj!", 32f, white, true), params(0, 12, 0, 8))
        content.addView(label("Nie potrzebujesz osobnego konta ani adresu e-mail. Aplikacja tworzy bezpieczną sesję urządzenia, a następnie połączysz ją z Librusem.", 15f, muted))
        content.addView(label(message, 14f, muted), params(0, 18, 0, 8))
        content.addView(actionButton("SPRÓBUJ PONOWNIE") {
            ensureAnonymousSession()
        }, params(0, 12, 0, 0))
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(content)
        })
    }

    private fun ensureAnonymousSession(replaceExisting: Boolean = false) {
        if (anonymousSignInInProgress || isFinishing) return
        anonymousSignInInProgress = true
        showLogin("Tworzę bezpieczną sesję urządzenia…")
        activityScope.launch {
            try {
                if (replaceExisting) runCatching { supabase.auth.signOut() }
                supabase.auth.signInAnonymously()
            } catch (error: Exception) {
                anonymousSignInInProgress = false
                showLogin("Nie udało się uruchomić sesji. Sprawdź internet i ustawienie logowania anonimowego w Supabase. ${error.message.orEmpty()}")
                return@launch
            }
            anonymousSignInInProgress = false
        }
    }

    // Subtelne dekoracje sezonowe w nagłówkach — bez zewnętrznych grafik ani pobierania zasobów.
    // Emoji są lekkimi wektorowymi glifami systemowymi i nie wpływają na działanie modułów.
    private fun seasonalDecorationText(sizeSp: Float): TextView? {
        val decoration = when (uiPrefs.getString("accent", "blue")) {
            "red" -> "🎅🏻  ❄️  ✨"          // Mikołajki
            "christmas" -> "🎄  🎁  ✨"    // Boże Narodzenie
            "halloween" -> "🎃  👻  🦇"
            "pink" -> "💕  💗  💞"
            "spring" -> "🌸  🦋  🌼"
            else -> ""
        }
        if (decoration.isBlank()) return null
        return TextView(this).apply {
            text = decoration
            textSize = sizeSp
            gravity = Gravity.CENTER
            alpha = 0.88f
            setPadding(4, 0, 0, 0)
            contentDescription = when (uiPrefs.getString("accent", "blue")) {
                "red" -> "Dekoracja motywu Mikołajki"
                "christmas" -> "Dekoracja motywu Boże Narodzenie"
                "halloween" -> "Dekoracja motywu Halloween"
                "pink" -> "Dekoracja motywu Walentynki"
                "spring" -> "Dekoracja motywu Wiosna"
                else -> "Dekoracja sezonowa"
            }
        }
    }

    private fun showLibrusLogin() {
        if (isFinishing) return
        val saved = librusVault.load()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 28, 26, 28)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, pageGradient)
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 22)
        }
        val mark = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = "Logo EduFlow"
        }
        brand.addView(mark, LinearLayout.LayoutParams(56, 56))
        val school = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 0, 0, 0)
        }
        val schoolName = currentSchoolName()
        school.addView(label(schoolName.ifBlank { "Szkoła z konta Librus" }, 12f, Color.rgb(190, 211, 245), true))
        school.addView(label(
            if (schoolName.isBlank()) "Nazwa szkoły pojawi się po synchronizacji konta" else "Dane szkoły pobrane z Librusa",
            11f, muted
        ))
        brand.addView(school, LinearLayout.LayoutParams(0, -2, 1f))
        seasonalDecorationText(27f)?.let { decoration ->
            brand.addView(decoration, LinearLayout.LayoutParams(-2, 56))
        }
        root.addView(brand, LinearLayout.LayoutParams(-1, -2))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 22, 20, 22)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, heroGradient).apply {
                cornerRadius = 24f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(58, 83, 119))
            }
            elevation = 4f * resources.displayMetrics.density
        }
        val accountLabel = if (activeProfileType == LibrusProfileStore.TYPE_STUDENT) "ucznia" else "rodzica"
        card.addView(label("EDUFLOW", 11f, Color.rgb(190, 211, 245), true))
        card.addView(label("Zaloguj się do Librusa", 27f, white, true), params(0, 10, 0, 6))
        card.addView(label("Wpisz dane konta $accountLabel Librus Synergia, aby przejść do swojego pulpitu szkolnego.", 14f, Color.rgb(205, 217, 235)), params(0, 0, 0, 16))

        librusLoginInput = inputField("Login do Librusa", InputType.TYPE_CLASS_TEXT)
        saved?.let { librusLoginInput.setText(it.login) }
        card.addView(librusLoginInput, params(0, 0, 0, 10))

        librusPasswordInput = inputField(
            "Hasło do Librusa",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        card.addView(librusPasswordInput, params(0, 0, 0, 12))

        librusConnectButton = actionButton("ZALOGUJ I OTWÓRZ PULPIT") {
            connectLibrus()
        }
        card.addView(librusConnectButton, params(0, 2, 0, 8))
        librusStatusText = label(
            if (saved != null) "Znaleziono zapisany login. Podaj hasło, aby ponownie połączyć konto."
            else "Dane logowania są używane wyłącznie do połączenia z Librusem.",
            13f, muted
        )
        card.addView(librusStatusText, params(0, 6, 0, 0))
        card.addView(label("Hasło jest przechowywane lokalnie w zaszyfrowanym magazynie urządzenia. Połączenie z serwerem odbywa się przez HTTPS.", 11f, muted), params(0, 14, 0, 0))
        root.addView(card, LinearLayout.LayoutParams(-1, -2))
        root.addView(
            actionButton("ZMIEŃ LUB DODAJ PROFIL") { showProfileSwitcher() },
            params(0, 14, 0, 0)
        )

        setContentView(ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(bgColor)
            addView(root)
        })
    }

    private fun schoolProfilePrefs() = getSharedPreferences(
        "librus_school_profile_$activeProfileId",
        MODE_PRIVATE
    ).also { current ->
        if (activeProfileId == LibrusProfileStore.LEGACY_PROFILE_ID &&
            current.getString("school_name", null) == null
        ) {
            val legacyName = getSharedPreferences("librus_school_profile", MODE_PRIVATE)
                .getString("school_name", null)
            if (!legacyName.isNullOrBlank()) current.edit().putString("school_name", legacyName).apply()
        }
    }

    private fun currentSchoolName(): String {
        val dataName = librusDataMemory?.let { extractSchoolName(it) }.orEmpty()
        if (dataName.isNotBlank()) {
            schoolProfilePrefs().edit()
                .putString("school_name", dataName).apply()
            return dataName
        }
        val savedName = schoolProfilePrefs()
            .getString("school_name", "").orEmpty()
        return savedName
    }

    private fun schoolBadge(name: String): String {
        if (name.isBlank()) return "L"
        val normalized = name.trim()
        val match = Regex("(?i)(?:nr|numer)\\s*(\\d+)").find(normalized)
            ?: Regex("\\b(\\d{1,3})\\b").find(normalized)
        return match?.groupValues?.getOrNull(1) ?: normalized.split(Regex("\\s+"))
            .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
            .take(2).ifBlank { "L" }
    }

    private fun extractSchoolName(data: JSONObject): String {
        val directKeys = listOf("school_name", "schoolName", "institution_name", "institutionName")
        fun readName(value: Any?): String {
            return when (value) {
                is String -> value.trim().takeIf { it.isNotBlank() && it.lowercase() != "null" }.orEmpty()
                is JSONObject -> {
                    for (key in listOf("name", "full_name", "school_name", "institution_name", "title", "display_name")) {
                        val candidate = readName(value.opt(key))
                        if (candidate.isNotBlank()) return candidate
                    }
                    ""
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        val candidate = readName(value.opt(index))
                        if (candidate.isNotBlank()) return candidate
                    }
                    ""
                }
                else -> ""
            }
        }
        for (key in directKeys) {
            val candidate = readName(data.opt(key))
            if (candidate.isNotBlank()) return candidate
        }
        for (key in listOf("school", "institution", "student", "me", "profile", "account", "user")) {
            val candidate = readName(data.opt(key)?.let { value ->
                if (value is JSONObject && value.has("school")) value.opt("school")
                else if (key == "school" || key == "institution") value
                else null
            })
            if (candidate.isNotBlank()) return candidate
            val nested = data.optJSONObject(key)
            if (nested != null) {
                for (schoolKey in listOf("school", "institution", "school_name", "schoolName", "institution_name")) {
                    val nestedName = readName(nested.opt(schoolKey))
                    if (nestedName.isNotBlank()) return nestedName
                }
            }
        }
        fun searchNested(value: Any?, depth: Int): String {
            if (depth > 4) return ""
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (key.lowercase() in setOf("school", "institution", "school_name", "schoolname", "institution_name")) {
                            val candidate = readName(child)
                            if (candidate.isNotBlank()) return candidate
                        }
                        val nested = searchNested(child, depth + 1)
                        if (nested.isNotBlank()) return nested
                    }
                }
                is JSONArray -> for (index in 0 until value.length()) {
                    val nested = searchNested(value.opt(index), depth + 1)
                    if (nested.isNotBlank()) return nested
                }
            }
            return ""
        }
        return searchNested(data, 0)
    }

    // EKRAN GŁÓWNY
    // ---------------------------------------------------------

    private fun showHome() {
        stopTimetableTicker()
        if (isFinishing) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 30)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, pageGradient)
        }

        val welcomeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                heroGradient
            ).apply { cornerRadius = 24f * resources.displayMetrics.density }
            elevation = 5f * resources.displayMetrics.density
            alpha = 0f
            translationY = 18f * resources.displayMetrics.density
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val schoolName = currentSchoolName()
        val brandMark = TextView(this).apply {
            text = schoolBadge(schoolName)
            textSize = 18f
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
        brandText.addView(label(schoolName.ifBlank { "Szkoła z konta Librus" }, 11f, Color.rgb(190, 211, 245), true))
        brandText.addView(label(
            if (schoolName.isBlank()) "Nazwa zostanie uzupełniona po pobraniu danych konta" else "Dane szkoły pobrane z Librusa",
            11f, muted
        ))
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        welcomeCard.addView(brandRow)

        // Powitanie i status sesji w jednym wierszu; sezonowa ozdoba jest niżej,
        // z dala od nazwy szkoły i bez ryzyka przycięcia w górnym pasku.
        val greetingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val greetingTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        greetingTexts.addView(label("Witaj!", 30f, white, true))
        greetingTexts.addView(label("Bezpieczna sesja urządzenia aktywna", 13f, Color.rgb(210, 220, 237)))
        greetingRow.addView(greetingTexts, LinearLayout.LayoutParams(0, -2, 1f))
        if (uiPrefs.getString("accent", "blue") in setOf("red", "christmas", "halloween", "pink", "spring")) {
            val seasonalBadge = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(34, 255, 255, 255))
                    setStroke((1 * resources.displayMetrics.density).toInt(), Color.argb(75, 220, 235, 255))
                }
            }
            seasonalBadge.addView(SeasonalBannerOverlayView(this),
                FrameLayout.LayoutParams((58 * resources.displayMetrics.density).toInt(), (58 * resources.displayMetrics.density).toInt(), Gravity.CENTER))
            greetingRow.addView(seasonalBadge, LinearLayout.LayoutParams(
                (58 * resources.displayMetrics.density).toInt(), (58 * resources.displayMetrics.density).toInt()
            ).apply { marginStart = (10 * resources.displayMetrics.density).toInt() })
        }
        welcomeCard.addView(greetingRow, params(0, 14, 0, 0))
        welcomeCard.addView(label("Twoje szkolne sprawy w jednym miejscu.", 14f, Color.rgb(130, 220, 180)), params(0, 3, 0, 0))
        content.addView(welcomeCard, params(0, 0, 0, 10))
        welcomeCard.animate().alpha(1f).translationY(0f).setDuration(520).start()
        val activeProfile = profileStore.getProfile(activeProfileId)
        content.addView(
            actionButton("PROFIL: ${activeProfile?.displayName ?: "Konto"}  ·  ZMIEŃ") {
                showProfileSwitcher()
            },
            params(0, 0, 0, 14)
        )

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
                surfaceGradient
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
        content.addView(connectionCard, params(0, 2, 0, 14))

        content.addView(actionButton(if (savedLibrus != null) "ZMIEN DANE KONTA LIBRUS" else "ZALOGUJ DO LIBRUSA") {
            showLibrusLogin()
        }, params(0, 12, 0, 8))

        content.addView(sectionHeader("CENTRUM SZKOLNE"), params(0, 18, 0, 4))
        content.addView(label("Wybierz moduł, aby przejść do danych z dziennika.", 13f, muted), params(0, 0, 0, 10))

        val quickAccess = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun quickTile(title: String, caption: String, tint: Int, section: String) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((9 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(), (9 * resources.displayMetrics.density).toInt(), (12 * resources.displayMetrics.density).toInt())
                minimumHeight = (142 * resources.displayMetrics.density).toInt()
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(tint, Color.rgb(25, 36, 55))
                ).apply {
                    cornerRadius = 18f * resources.displayMetrics.density
                    setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(59, 78, 106))
                }
                isClickable = true
                isFocusable = true
                clipChildren = false
                clipToPadding = false
                setOnClickListener { loadLibrusSection(section) }
            }
            val artFrame = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(34, 255, 255, 255))
                    setStroke((1 * resources.displayMetrics.density).toInt(), Color.argb(60, 220, 235, 255))
                }
            }
            artFrame.addView(ThematicBannerArtView(this@MainActivity, section),
                FrameLayout.LayoutParams((68 * resources.displayMetrics.density).toInt(), (68 * resources.displayMetrics.density).toInt(), Gravity.CENTER))
            if (uiPrefs.getString("accent", "blue") in setOf("red", "christmas", "halloween", "pink", "spring")) {
                artFrame.addView(SeasonalBannerOverlayView(this@MainActivity),
                    FrameLayout.LayoutParams((68 * resources.displayMetrics.density).toInt(), (68 * resources.displayMetrics.density).toInt(), Gravity.CENTER))
            }
            tile.addView(artFrame, LinearLayout.LayoutParams(
                (68 * resources.displayMetrics.density).toInt(), (68 * resources.displayMetrics.density).toInt()
            ))
            tile.addView(label(title, 13f, white, true), params(0, 7, 0, 1))
            tile.addView(label(caption, 10f, Color.rgb(187, 201, 222)).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            })
            quickAccess.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (quickAccess.childCount == 0) marginEnd = (5 * resources.displayMetrics.density).toInt()
                else marginStart = (5 * resources.displayMetrics.density).toInt()
            })
        }
        quickTile("Oceny", "Postępy ucznia", Color.rgb(40, 78, 126), "OCENY")
        quickTile("Plan", "Lekcje i godziny", Color.rgb(47, 78, 100), "PLAN LEKCJI")
        quickTile("Wiadomości", "Ogłoszenia", Color.rgb(83, 62, 112), "WIADOMOŚCI")
        content.addView(quickAccess, params(0, 0, 0, 10))
        content.addView(actionButton("▤  OTWÓRZ OŚ CZASU") {
            loadLibrusSection("OŚ CZASU")
        }, params(0, 0, 0, 12))
        addTodayPanel(content)

        addHomeSection(content, "PROFILE", "Dane konta, przedmioty, nauczyciele i sale lekcyjne", "🏫")
        addHomeSection(content, "OCENY", "Oceny i postępy ucznia", "📊")
        addHomeSection(content, "FREKWENCJA", "Obecności, nieobecności i spóźnienia", "📅")
        addHomeSection(content, "PLAN LEKCJI", "Tygodniowy harmonogram zajęć", "🗓")
        addHomeSection(content, "WIADOMOŚCI", "Wiadomości i ogłoszenia", "✉")
        addHomeSection(content, "ZADANIA DOMOWE", "Zadania, terminy i załączniki", "📚")
        addHomeSection(content, "TERMINARZ", "Sprawdziany, wydarzenia i wycieczki", "🗓")
        addHomeSection(content, "UWAGI", "Uwagi pozytywne, neutralne i negatywne", "📝")
        addHomeSection(content, "SZCZĘŚLIWY NUMEREK", "Numer obowiązujący danego dnia", "🍀")

        content.addView(sectionHeader("TWOJE NARZĘDZIA"), params(0, 18, 0, 5))
        content.addView(label("Przydatne rzeczy poza dziennikiem szkolnym.", 13f, muted), params(0, 0, 0, 8))
        addPersonalToolCard(content, "NOTATKI", "Szybkie zapiski, pomysły i ważne informacje", "📝") { showPersonalNotes() }
        addPersonalToolCard(content, "LISTA ZAKUPÓW", "Dodawaj produkty i odhaczaj je w sklepie", "🛒") { showShoppingList() }
        addPersonalToolCard(content, "O AUTORZE", "Poznaj twórcę aplikacji EduFlow", "⌘") { showAbout() }

        content.addView(actionButton("USTAWIENIA POWIADOMIEŃ") {
            showNotificationSettings()
        }, params(0, 12, 0, 8))

        content.addView(actionButton("ODŁĄCZ KONTO LIBRUS") {
            disconnectLibrus()
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

        if (savedLibrus != null) LibrusNotificationScheduler.schedule(applicationContext, activeProfileId)

        if (savedLibrus != null && !autoRestoreStarted) {
            autoRestoreStarted = true
            val requestedSection = pendingNotificationSection
            pendingNotificationSection = null
            if (requestedSection != null) loadLibrusSection(requestedSection)
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
        syncInProgress = true
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
                    put("profile", activeProfileType)
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
                        extractSchoolName(json).takeIf { it.isNotBlank() }?.let { schoolName ->
                            schoolProfilePrefs().edit()
                                .putString("school_name", schoolName).apply()
                        }
                        librusLoginMemory = login
                        librusPasswordMemory = password
                        librusSessionMemory = json.optJSONObject("session")
                        librusDataMemory = null

                        librusVault.save(login, password, librusSessionMemory?.toString())
                        LibrusNotificationScheduler.schedule(applicationContext, activeProfileId)

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
                syncInProgress = false
                if (isFinishing || isDestroyed) return@runOnUiThread

                val ok = result.startsWith("SUCCESS|")
                setLibrusStatus(result.substringAfter("|"), ok)
                librusConnectButton.isEnabled = true
                librusPasswordInput.text.clear()

                if (ok) {
                    initialRouteHandled = true
                    showHome()
                }
            }
        }
    }

    // ---------------------------------------------------------
    // SYNCHRONIZACJA
    // ---------------------------------------------------------

    private fun loadLibrusSection(section: String) {
        // Zatrzymaj licznik poprzedniego planu natychmiast po zmianie sekcji,
        // również podczas oczekiwania na synchronizację danych.
        stopTimetableTicker()
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

        if (syncInProgress) {
            // Nawigacja pozostaje aktywna podczas pobierania. Zapamiętujemy
            // ostatnią wybraną zakładkę i od razu pokazujemy jej ekran ładowania.
            pendingNotificationSection = section
            showSectionLoading(section, "Pobieranie danych trwa. Otworzę tę sekcję, gdy dane będą gotowe.")
            return
        }

        librusDataMemory?.let {
            showLibrusSection(section, it)
            return
        }

        if (token.isNullOrBlank()) {
            setLibrusStatus("Sesja aplikacji wygasła. Zaloguj się ponownie.", false)
            return
        }

        syncInProgress = true
        pendingNotificationSection = section
        val syncStartedAt = SystemClock.elapsedRealtime()
        setLibrusStatus("Pobieram dane z Librusa…", null)
        showSectionLoading(section, "Pobieram dane z Librusa. Nie musisz klikać ponownie — ekran otworzy się automatycznie.")

        thread {
            var connection: HttpURLConnection? = null
            var payloadBuildMs = 0L
            var requestWriteMs = 0L
            var responseWaitMs = 0L
            var responseReadMs = 0L
            var jsonParseMs = 0L
            var responseBytes = 0

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

                val payloadStartedAt = SystemClock.elapsedRealtime()
                val payload = JSONObject().apply {
                    put("profile", activeProfileType)
                    put("login", login)
                    put("password", password)
                    librusSessionMemory?.let { put("session", it) }
                }
                val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)
                payloadBuildMs = SystemClock.elapsedRealtime() - payloadStartedAt

                val writeStartedAt = SystemClock.elapsedRealtime()
                connection.outputStream.use { it.write(payloadBytes) }
                requestWriteMs = SystemClock.elapsedRealtime() - writeStartedAt

                val responseWaitStartedAt = SystemClock.elapsedRealtime()
                val code = connection.responseCode
                responseWaitMs = SystemClock.elapsedRealtime() - responseWaitStartedAt

                val responseReadStartedAt = SystemClock.elapsedRealtime()
                val body = readResponse(connection, code)
                responseReadMs = SystemClock.elapsedRealtime() - responseReadStartedAt
                responseBytes = body.toByteArray(Charsets.UTF_8).size

                if (code in 200..299) {
                    val parseStartedAt = SystemClock.elapsedRealtime()
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data")
                    jsonParseMs = SystemClock.elapsedRealtime() - parseStartedAt

                    if (data != null) {
                        librusDataMemory = data
                        extractSchoolName(data).takeIf { it.isNotBlank() }?.let { schoolName ->
                            schoolProfilePrefs().edit()
                                .putString("school_name", schoolName).apply()
                        }
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

            val syncDurationMs = SystemClock.elapsedRealtime() - syncStartedAt
            Log.d(
                "EduFlowPerf",
                "section_sync section=$section total_ms=$syncDurationMs payload_ms=$payloadBuildMs " +
                    "write_ms=$requestWriteMs response_wait_ms=$responseWaitMs response_read_ms=$responseReadMs " +
                    "json_parse_ms=$jsonParseMs response_bytes=$responseBytes result=" +
                    if (result == "SUCCESS") "success" else "error"
            )

            runOnUiThread {
                syncInProgress = false
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (result == "SUCCESS") {
                    setLibrusStatus("Dane Librusa pobrane.", true)
                    val destination = pendingNotificationSection ?: section
                    pendingNotificationSection = null
                    when (destination) {
                        "START" -> showHome()
                        "WIĘCEJ" -> showMoreMenu(section)
                        else -> librusDataMemory?.let { showLibrusSection(destination, it) }
                    }
                } else {
                    pendingNotificationSection = null
                    val errorMessage = result.substringAfter("|")
                    setLibrusStatus(errorMessage, false)
                    showSectionLoadError(section, errorMessage)
                }
            }
        }
    }

    private fun showSectionLoading(section: String, message: String) {
        if (isFinishing || isDestroyed) return
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }
        content.addView(label(section, 24f, white, true))
        content.addView(ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accentColor)
        }, LinearLayout.LayoutParams(
            (42 * resources.displayMetrics.density).toInt(),
            (42 * resources.displayMetrics.density).toInt()
        ).apply { topMargin = (24 * resources.displayMetrics.density).toInt() })
        content.addView(label("Chwileczkę…", 16f, white, true), params(0, 18, 0, 4))
        content.addView(label(message, 13f, muted), params(0, 4, 0, 0))
        page.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        page.addView(buildBottomNavigation(section))
        setContentView(page)
    }

    private fun showSectionLoadError(section: String, message: String) {
        if (isFinishing || isDestroyed) return
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }
        content.addView(label(section, 24f, white, true))
        content.addView(label("Nie udało się pobrać danych", 17f, errorColor, true), params(0, 18, 0, 6))
        content.addView(label(message, 13f, muted), params(0, 4, 0, 18))
        content.addView(actionButton("SPRÓBUJ PONOWNIE") {
            loadLibrusSection(section)
        })
        page.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        page.addView(buildBottomNavigation(section))
        setContentView(page)
    }

    // ---------------------------------------------------------
    // WYŚWIETLANIE SEKCJI
    // ---------------------------------------------------------

    private fun showLibrusSection(section: String, data: JSONObject) {
        val renderStartedAt = SystemClock.elapsedRealtime()
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
            "PROFILE" -> "Dane konta i informacje szkolne"
            "NAUCZYCIELE" -> "Katalog nauczycieli i wyszukiwarka kontaktów"
            "OŚ CZASU" -> "Najnowsze oceny, wiadomości, zadania i wydarzenia w jednym miejscu"
            else -> "Dane z Librusa"
        }

        val density = resources.displayMetrics.density
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 18)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                heroGradient
            ).apply {
                cornerRadius = 24f * density
                setStroke((1 * density).toInt(), blendColor(accentColor, Color.rgb(73, 105, 151), 0.55f))
            }
            elevation = 4f * density
        }
        hero.addView(label("LIBRUS  /  CENTRUM UCZNIA", 10f, Color.rgb(181, 211, 255), true))
        hero.addView(label(section, 25f, white, true), params(0, 5, 0, 0))
        hero.addView(label(subtitle, 13f, Color.rgb(202, 215, 235)))

        // Duża ilustracja tematyczna wewnątrz banera sekcji, a nie tylko na pulpicie.
        // Rysowana lokalnie na Canvas — działa offline i nie wymaga pobierania obrazów.
        val activeTheme = uiPrefs.getString("accent", "blue").orEmpty()
        if (activeTheme in setOf("red", "christmas", "halloween", "pink", "spring")) {
            val seasonalTitle = when (activeTheme) {
                "red" -> "MIKOŁAJKI  •  ŚWIĄTECZNY KLIMAT"
                "christmas" -> "BOŻE NARODZENIE  •  MAGIA ŚWIĄT"
                "halloween" -> "HALLOWEEN  •  TROCHĘ MAGII"
                "pink" -> "WALENTYNKI  •  MIŁY AKCENT"
                "spring" -> "WIOSNA  •  CZAS NA KOLORY"
                else -> "MOTYW SEZONOWY"
            }
            val seasonalRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, (7 * density).toInt(), 8, (7 * density).toInt())
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.argb(48, 255, 255, 255), Color.argb(18, 255, 255, 255))
                ).apply {
                    cornerRadius = 17f * density
                    setStroke((1 * density).toInt(), Color.argb(55, 225, 238, 255))
                }
            }
            val seasonalCopy = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            seasonalCopy.addView(label("WYJĄTKOWY CZAS W SZKOLE", 9f, Color.argb(205, 226, 237, 255), true))
            seasonalCopy.addView(label(seasonalTitle, 11f, white, true))
            seasonalRow.addView(seasonalCopy, LinearLayout.LayoutParams(0, -2, 1f))
            val seasonalArt = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(38, 255, 255, 255))
                    setStroke((1 * density).toInt(), Color.argb(75, 235, 244, 255))
                }
                clipChildren = false
                clipToPadding = false
            }
            seasonalArt.addView(ThematicBannerArtView(this, section),
                FrameLayout.LayoutParams((72 * density).toInt(), (72 * density).toInt(), Gravity.CENTER))
            seasonalArt.addView(SeasonalBannerOverlayView(this),
                FrameLayout.LayoutParams((72 * density).toInt(), (72 * density).toInt(), Gravity.CENTER))
            seasonalRow.addView(seasonalArt, LinearLayout.LayoutParams(
                (76 * density).toInt(), (76 * density).toInt()
            ))
            hero.addView(seasonalRow, params(0, 12, 0, 0))

            if (uiPrefs.getBoolean("motion", true)) {
                val floatAnimation = android.animation.ObjectAnimator.ofFloat(
                    seasonalArt, View.TRANSLATION_Y, 0f, -4f * density, 0f
                ).apply {
                    duration = 2400L
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                seasonalArt.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) { if (!floatAnimation.isStarted) floatAnimation.start() }
                    override fun onViewDetachedFromWindow(view: View) { floatAnimation.cancel() }
                })
            }
        }
        root.addView(hero, params(0, 8, 0, 16))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = actionButton("‹  PULPIT") {
            showHome()
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

        // Rozdzielamy koszt przygotowania treści od budowania reszty ekranu.
        // Dzięki temu log z urządzenia wskaże konkretny ciężki moduł.
        val contentRenderStartedAt = SystemClock.elapsedRealtime()
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
            "TERMINARZ" -> renderAgendaCalendar(root, data)
            "UWAGI" -> renderFeatureArray(root, data, listOf("notes", "behaviour_notes"), "Uwagi")
            "SZCZĘŚLIWY NUMEREK" -> renderLuckyNumber(root, data)
            "PROFILE" -> renderProfileSection(root, data)
            "NAUCZYCIELE" -> renderTeachersDirectory(root, data)
            "OŚ CZASU" -> renderSchoolTimeline(root, data)
            else -> root.addView(infoCard("Nieznana sekcja."))
        }
        val contentRenderDurationMs = SystemClock.elapsedRealtime() - contentRenderStartedAt
        if (contentRenderDurationMs >= 80L) {
            Log.d("EduFlowPerf", "section_content_render section=$section duration_ms=$contentRenderDurationMs child_views=${root.childCount}")
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
        pendingTimelineTargetKey?.let { targetKey ->
            scroll.post {
                val target = root.findViewWithTag<View>("timeline_target:$targetKey")
                if (target != null) {
                    val targetBounds = android.graphics.Rect()
                    root.offsetDescendantRectToMyCoords(target, targetBounds)
                    scroll.smoothScrollTo(0, (targetBounds.top - (24f * density).toInt()).coerceAtLeast(0))
                }
                pendingTimelineTargetKey = null
            }
        }
        if (uiPrefs.getBoolean("motion", true)) {
            root.alpha = 0f
            root.translationY = 12f * resources.displayMetrics.density
            root.animate().alpha(1f).translationY(0f).setDuration(280).start()
        }
        val renderDurationMs = SystemClock.elapsedRealtime() - renderStartedAt
        if (renderDurationMs >= 120L) {
            Log.d("EduFlowPerf", "section_render section=$section duration_ms=$renderDurationMs")
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

            // Referencje w planie mogą przyjść jako ID, tekst albo obiekt {id, name}.
            // Nie używaj JSONObject.toString() jako identyfikatora ani etykiety.
            val subjectObject = obj.optJSONObject("subject")
            val subjectId = firstValue(obj, "subject_id")
                .ifBlank { subjectObject?.let { firstValue(it, "id", "subject_id") }.orEmpty() }
                .ifBlank { (obj.opt("subject") as? String).orEmpty() }
            val teacherObject = obj.optJSONObject("teacher")
            val teacherId = firstValue(obj, "teacher_id")
                .ifBlank { teacherObject?.let { firstValue(it, "id", "teacher_id") }.orEmpty() }
                .ifBlank { (obj.opt("teacher") as? String).orEmpty() }
            val roomObject = obj.optJSONObject("classroom")
                ?: obj.optJSONObject("room")
            val roomId = firstValue(obj, "classroom_id", "room_id")
                .ifBlank { roomObject?.let { firstValue(it, "id", "classroom_id", "room_id") }.orEmpty() }
                .ifBlank {
                    val rawRoom = obj.opt("classroom") ?: obj.opt("room")
                    (rawRoom as? String).orEmpty()
                }

            val subject = firstDisplayValue(obj, "subject_name")
                .ifBlank { subjectObject?.let { firstDisplayValue(it, "name", "title", "value") }.orEmpty() }
                .ifBlank { lookupName(subjects, subjectId) }
                .ifBlank { (obj.opt("subject") as? String).orEmpty() }

            val teacher = firstDisplayValue(obj, "teacher_name")
                .ifBlank {
                    teacherObject?.let {
                        firstDisplayValue(it, "name", "full_name", "display_name", "title", "value")
                            .ifBlank {
                                listOf(
                                    firstValue(it, "first_name", "firstname"),
                                    firstValue(it, "last_name", "surname", "lastname")
                                ).filter { part -> part.isNotBlank() }.joinToString(" ")
                            }
                    }.orEmpty()
                }
                .ifBlank { lookupName(teachers, teacherId) }
                .ifBlank { (obj.opt("teacher") as? String).orEmpty() }

            val room = firstDisplayValue(obj, "classroom_name", "room_name")
                .ifBlank { roomObject?.let { firstDisplayValue(it, "name", "number", "room_number", "title", "value") }.orEmpty() }
                .ifBlank { lookupName(classrooms, roomId) }
                .ifBlank {
                    val rawRoom = obj.opt("classroom") ?: obj.opt("room")
                    (rawRoom as? String).orEmpty()
                }

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
            stopTimetableTicker()
            dayContent.removeAllViews()
            val dayLessons = grouped[date] ?: return
            val dayCard = card()
            dayCard.addView(label(dateLabel(date), 18f, white, true))
            dayCard.addView(
                label("${dayLessons.size} zaplanowanych lekcji", 12f, muted)
            )

            val sortedDayLessons = dayLessons.sortedWith(
                compareBy<TimetableLesson> { it.from.ifBlank { "99:99" } }
                    .thenBy { it.number }
            )
            val timelineList = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            sortedDayLessons.forEach { lesson ->
                timelineList.addView(renderLessonCard(lesson), params(0, 10, 0, 0))
            }

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

                // Znacznik czasu na osi planu: przemieszcza się nad trwającą
                // lekcję, nad następną lekcję podczas przerwy albo na koniec dnia.
                val timelineMarker = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (7 * resources.displayMetrics.density).toInt(),
                        0, (3 * resources.displayMetrics.density).toInt())
                }
                val timelineMarkerLabel = TextView(this).apply {
                    textSize = 10f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accentColor)
                    setPadding(0, 0, (9 * resources.displayMetrics.density).toInt(), 0)
                }
                timelineMarker.addView(timelineMarkerLabel)
                timelineMarker.addView(View(this).apply {
                    background = roundedBackground(accentColor, 2)
                }, LinearLayout.LayoutParams(0, (2 * resources.displayMetrics.density).toInt(), 1f))
                timelineList.addView(timelineMarker)

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
                                liveStatus.text = getString(R.string.timetable_now, activeLesson.subject)
                                liveDetail.text = getString(R.string.timetable_lesson_detail, activeLesson.number, activeLesson.from, activeLesson.to, remaining)
                                val startMinute = minuteOf(activeLesson.from) ?: correctedMinute
                                val duration = (endMinute - startMinute).coerceAtLeast(1)
                                liveProgress.progress = (((correctedMinute - startMinute).toFloat() / duration) * 100).toInt().coerceIn(0, 100)
                            }
                            nextLesson != null && previousLesson != null -> {
                                val startMinute = minuteOf(nextLesson.from) ?: correctedMinute
                                val endMinute = minuteOf(previousLesson.to) ?: correctedMinute
                                val remaining = (startMinute - correctedMinute).coerceAtLeast(0)
                                stateKey = "break-${nextLesson.number}"
                                liveStatus.text = getString(R.string.timetable_break)
                                liveDetail.text = getString(R.string.timetable_next_lesson, nextLesson.number, nextLesson.subject, remaining)
                                liveProgress.progress = if (startMinute > endMinute)
                                    (((correctedMinute - endMinute).toFloat() / (startMinute - endMinute)) * 100).toInt().coerceIn(0, 100)
                                else 0
                            }
                            nextLesson != null -> {
                                val startMinute = minuteOf(nextLesson.from) ?: correctedMinute
                                stateKey = "before"
                                liveStatus.text = getString(R.string.timetable_before_lessons)
                                liveDetail.text = getString(R.string.timetable_first_lesson, nextLesson.subject, nextLesson.from, (startMinute - correctedMinute).coerceAtLeast(0))
                                liveProgress.progress = 0
                            }
                            else -> {
                                stateKey = "after"
                                liveStatus.text = getString(R.string.timetable_day_complete)
                                liveDetail.text = getString(R.string.timetable_done)
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
                        timelineMarkerLabel.text = "TERAZ • " +
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
                        val markerTarget = when {
                            activeLesson != null -> sortedDayLessons.indexOf(activeLesson)
                            nextLesson != null -> sortedDayLessons.indexOf(nextLesson)
                            else -> sortedDayLessons.size
                        }.coerceIn(0, sortedDayLessons.size)
                        timelineList.removeView(timelineMarker)
                        timelineList.addView(timelineMarker, markerTarget)

                        previousLiveState = stateKey
                        handler.postDelayed(this, 30_000L)
                    }
                }
                timetableTicker = ticker
                ticker.run()
            }

            dayCard.addView(timelineList, params(0, 4, 0, 0))
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
            renderGradeItems(parent, array, subjects, teachers, key, data.optJSONObject("grade_categories"))
        }

        data.optJSONObject("grading_system")?.let {
            parent.addView(sectionHeader("Skala ocen"), params(0, 18, 0, 8))
            renderObjectCard(parent, it, null, null)
        }

        if (total == 0) parent.addView(infoCard("Brak ocen do wyświetlenia."))
    }

    private fun showGradeSimulator(subject: String, currentGrades: List<Double>) {
        val currentAverage = currentGrades.average()
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 0)
        }
        fields.addView(label("Przedmiot: " + subject, 13f, muted))
        fields.addView(label(
            "Uwzględnione oceny (plus/minus liczone po 0,5): " +
                currentGrades.joinToString(", ") { GradeAverageParser.format(it) },
            13f, white
        ))
        fields.addView(label(
            "Obecna średnia arytmetyczna: " +
                String.format(Locale("pl", "PL"), "%.2f", currentAverage),
            18f, accentColor, true
        ))
        fields.addView(label(
            "Założenie symulacji: 4+ = 4,5 i 4- = 3,5. Szkoła może stosować własne zasady liczenia średniej.",
            11f, muted
        ), params(0, 5, 0, 0))
        val proposed = EditText(this).apply {
            hint = "Wpisz proponowaną ocenę 1–6"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
        }
        val result = label("Wpisz ocenę i wybierz „Oblicz”. Symulacja nie zmienia ocen w Librusie.", 13f, muted)
        fields.addView(proposed, params(0, 10, 0, 6))
        fields.addView(result, params(0, 6, 0, 0))
        val dialog = AlertDialog.Builder(this).setTitle("Kalkulator ocen")
            .setView(fields).setNegativeButton("Zamknij", null)
            .setPositiveButton("Oblicz", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = proposed.text.toString().toIntOrNull()
                if (value == null || value !in 1..6) {
                    result.text = getString(R.string.grade_input_error)
                    result.setTextColor(errorColor)
                } else {
                    val projected = (currentGrades.sum() + value) / (currentGrades.size + 1)
                    result.text = getString(
                        R.string.grade_average_result,
                        value.toString(),
                        String.format(Locale("pl", "PL"), "%.2f", projected),
                        currentGrades.size + 1
                    )
                    result.setTextColor(successColor)
                }
            }
        }
        dialog.show()
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
        teachers: JSONObject?,
        sourceKey: String = "grades",
        gradeCategories: JSONObject? = null
    ) {
        val grouped = linkedMapOf<String, MutableList<JSONObject>>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            // W zależności od wersji danych Librus zwraca przedmiot jako ID,
            // tekst albo zagnieżdżony obiekt { id, name }. Obsłuż wszystkie
            // warianty bez wyświetlania surowego JSON jako nazwy przedmiotu.
            val subjectObject = item.optJSONObject("subject")
            val sid = firstValue(item, "subject_id")
                .ifBlank { subjectObject?.let { firstValue(it, "id", "subject_id") }.orEmpty() }
            val subject = firstDisplayValue(item, "subject_name")
                .ifBlank { subjectObject?.let { firstDisplayValue(it, "name", "title", "value") }.orEmpty() }
                .ifBlank { lookupName(subjects, sid) }
                .ifBlank {
                    val rawSubject = item.opt("subject")
                    if (rawSubject is String) lookupName(subjects, rawSubject).ifBlank { rawSubject }
                    else ""
                }
                .ifBlank { "Pozostałe" }

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
            // Średnią arytmetyczną licz wyłącznie z ocen szkolnych.
            // Oceny opisowe, cząstkowe, punktowe, tekstowe i zachowania
            // mogą zawierać cyfry 1–6, ale nie są zwykłymi stopniami do średniej.
            val simulatorGrades = if (sourceKey == "grades") {
                grades.mapNotNull { grade ->
                    // Librus report-card grades are not ordinary running marks.
                    if (grade.optBoolean("is_semester_proposition", false) ||
                        grade.optBoolean("is_final_proposition", false) ||
                        grade.optBoolean("is_semester", false) ||
                        grade.optBoolean("is_final", false)
                    ) return@mapNotNull null

                    // Respect Librus's "Licz do śr." setting. Category data is
                    // keyed by category_id and defaults to counted only when
                    // Librus has not explicitly disabled it.
                    val categoryId = firstValue(grade, "category_id")
                    val gradeCategory = gradeCategories?.optJSONObject(categoryId)
                    val countsToAverage = when {
                        gradeCategory == null -> true
                        gradeCategory.has("count_to_average") &&
                            !gradeCategory.isNull("count_to_average") ->
                            gradeCategory.optBoolean("count_to_average", true)
                        gradeCategory.has("counts_to_average") &&
                            !gradeCategory.isNull("counts_to_average") ->
                            gradeCategory.optBoolean("counts_to_average", true)
                        else -> true
                    }
                    if (!countsToAverage) return@mapNotNull null

                    val raw = firstDisplayValue(
                        grade,
                        "value", "grade", "symbol", "grade_value", "mark"
                    ).trim()
                    GradeAverageParser.parse(raw)
                }
            } else {
                emptyList()
            }
            if (simulatorGrades.isNotEmpty()) {
                subjectCard.addView(actionButton("SYMULUJ ŚREDNIĄ (bez wag)") {
                    showGradeSimulator(subject, simulatorGrades)
                }, params(0, 8, 0, 4))
            }

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
                val categoryId = firstValue(grade, "category_id")
                val category = firstDisplayValue(grade, "category_name", "category")
                    .ifBlank {
                        if (sourceKey == "grades") lookupName(gradeCategories, categoryId) else ""
                    }
                    .ifBlank { grade.optJSONObject("category")?.let { firstDisplayValue(it, "name", "title", "value") }.orEmpty() }
                val comments = displayJsonValue(grade.opt("comments"))
                val teacherObject = grade.optJSONObject("teacher")
                val teacherId = firstValue(grade, "teacher_id")
                    .ifBlank { teacherObject?.let { firstValue(it, "id", "teacher_id") }.orEmpty() }
                val teacher = firstDisplayValue(grade, "teacher_name")
                    .ifBlank { teacherObject?.let { firstDisplayValue(it, "name", "title", "value") }.orEmpty() }
                    .ifBlank { lookupName(teachers, teacherId) }
                    .ifBlank {
                        val rawTeacher = grade.opt("teacher")
                        if (rawTeacher is String) lookupName(teachers, rawTeacher).ifBlank { rawTeacher }
                        else ""
                    }

                val numericGrade = GradeAverageParser.parse(value) != null
                val valueColor = gradeColor(value)
                val row = LinearLayout(this).apply {
                    val focusKey = gradeFocusKey(grade, sourceKey)
                    if (pendingTimelineTargetKey == focusKey) tag = "timeline_target:$focusKey"
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
                val cleanCategory = cleanGradeDetail(category, "Za co")
                val cleanDescription = cleanGradeDetail(description, "Opis", "Za co")
                val cleanComments = cleanGradeDetail(comments, "Komentarz")
                if (cleanCategory.isNotBlank()) details.addView(label("Za co: $cleanCategory", 13f, white, true))
                if (cleanDescription.isNotBlank() && !sameGradeDetail(cleanDescription, cleanCategory)) {
                    details.addView(label(cleanDescription, 13f, white))
                }
                if (cleanComments.isNotBlank() &&
                    !sameGradeDetail(cleanComments, cleanDescription) &&
                    !sameGradeDetail(cleanComments, cleanCategory)) {
                    details.addView(label("Komentarz: $cleanComments", 12f, muted))
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

                appendGradeExtraDetails(subjectCard, grade, sourceKey)
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

        // Nie ukrywaj kalendarza, gdy Librus nie zwrócił jeszcze wpisów.
        // Dzięki temu użytkownik nadal widzi bieżący miesiąc i dzisiejszą datę.

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
        // Kalendarz zawsze otwieraj na bieżącym miesiącu i dniu.
        // Historia frekwencji może nie zawierać jeszcze żadnego wpisu z dzisiaj.
        var selectedDate = today
        val monthCursor = java.util.Calendar.getInstance()

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
                            hasEntries -> byDate[dateKey]?.firstOrNull()?.first?.let { attendanceStatusColor(typeOf(it)) } ?: muted
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
                // Zawsze zmieniaj miesiąc od jego pierwszego dnia.
                // Zapobiega to przesuwaniu daty (np. 31 stycznia → 28 lutego → 28 marca).
                monthCursor.set(java.util.Calendar.DAY_OF_MONTH, 1)
                monthCursor.add(java.util.Calendar.MONTH, delta)
                val prefix = SimpleDateFormat("yyyy-MM", Locale.US).format(monthCursor.time)
                selectedDate = if (today.startsWith(prefix)) today else
                    byDate.keys.firstOrNull { it.startsWith(prefix) }
                        ?: iso((monthCursor.clone() as java.util.Calendar).apply { set(java.util.Calendar.DAY_OF_MONTH, 1) })
                renderCalendar()
                renderSelectedDay()
            }
        }
        val todayButton = TextView(this).apply {
            text = "DZIŚ"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding((9 * resources.displayMetrics.density).toInt(), 0,
                (9 * resources.displayMetrics.density).toInt(), 0)
            background = roundedBackground(panelLight, 10)
            isClickable = true
            isFocusable = true
            contentDescription = "Przejdź do dzisiejszej daty"
            setOnClickListener {
                monthCursor.timeInMillis = java.util.Calendar.getInstance().timeInMillis
                selectedDate = today
                renderCalendar()
                renderSelectedDay()
            }
        }
        monthControls.addView(monthButton("‹", -1), LinearLayout.LayoutParams((44 * resources.displayMetrics.density).toInt(), (42 * resources.displayMetrics.density).toInt()))
        monthControls.addView(monthTitle, LinearLayout.LayoutParams(0, -2, 1f))
        monthControls.addView(todayButton, LinearLayout.LayoutParams(-2, (34 * resources.displayMetrics.density).toInt()).apply {
            marginEnd = (5 * resources.displayMetrics.density).toInt()
        })
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
        if (!requireParentProfile("e-Usprawiedliwienie")) return
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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
                                "Status początkowy to „Wysłane” — oczekuje na decyzję wychowawcy.\n\n" +
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
        if (!requireParentProfile("Wiadomości do wychowawcy")) return
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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
        if (!requireParentProfile("Wysyłanie wiadomości")) return
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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

    // Kalendarz miesięczny łączący wydarzenia szkolne z Librusa i prywatne wydarzenia.
    // Siatka używa wymiarów dp i pełnej szerokości, aby nie ściskać dni na różnych ekranach.
    private fun renderAgendaCalendar(parent: LinearLayout, data: JSONObject) {
        val density = resources.displayMetrics.density
        val schoolEvents = AgendaCalendarParser.parse(data)
        fun dp(value: Int): Int = (value * density).toInt()
        val month = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        var selected = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Calendar.getInstance().time)
        val today = selected
        val host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        parent.addView(sectionHeader("TWÓJ KALENDARZ"), params(0, 6, 0, 8))
        parent.addView(host, params(0, 0, 0, 14))

        fun redraw() {
            host.removeAllViews()
            val title = SimpleDateFormat("LLLL yyyy", Locale("pl", "PL")).format(month.time)
                .replaceFirstChar { it.uppercase(Locale("pl", "PL")) }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(8))
            }
            header.addView(actionButton("‹") {
                month.add(Calendar.MONTH, -1)
                month.set(Calendar.DAY_OF_MONTH, 1)
                selected = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(month.time)
                redraw()
            }, LinearLayout.LayoutParams(dp(48), dp(44)))
            header.addView(label(title, 19f, white, true).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            header.addView(actionButton("›") {
                month.add(Calendar.MONTH, 1)
                month.set(Calendar.DAY_OF_MONTH, 1)
                selected = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(month.time)
                redraw()
            }, LinearLayout.LayoutParams(dp(48), dp(44)))
            host.addView(header)

            host.addView(actionButton("DZIŚ") {
                month.timeInMillis = Calendar.getInstance().timeInMillis
                month.set(Calendar.DAY_OF_MONTH, 1)
                selected = today
                redraw()
            }, LinearLayout.LayoutParams(-1, dp(38)).apply {
                bottomMargin = dp(8)
            })

            val names = listOf("PON", "WT", "ŚR", "CZW", "PT", "SOB", "ND")
            val weekHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            names.forEachIndexed { index, name ->
                weekHeader.addView(label(name, 10f, if (index >= 5) accentColor else muted, true).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }, LinearLayout.LayoutParams(0, dp(30), 1f))
            }
            host.addView(weekHeader)

            val first = (month.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val count = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            val weeks = (offset + count + 6) / 7
            val eventDates = readLocalArray("personal_events")
            val datesWithEvents = (0 until eventDates.length()).mapNotNull { index ->
                eventDates.optJSONObject(index)?.optString("date")?.takeIf { it.isNotBlank() }
            }.toSet()
            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(2), 0, dp(4))
            }

            repeat(weeks) { week ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                repeat(7) { column ->
                    val position = week * 7 + column
                    val dayNumber = position - offset + 1
                    val cellParams = LinearLayout.LayoutParams(0, dp(70), 1f).apply {
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    }
                    if (dayNumber !in 1..count) {
                        row.addView(View(this).apply {
                            background = roundedBackground(Color.TRANSPARENT, 12)
                        }, cellParams)
                    } else {
                        val date = String.format(
                            Locale.ROOT, "%04d-%02d-%02d",
                            first.get(Calendar.YEAR), first.get(Calendar.MONTH) + 1, dayNumber
                        )
                        val isToday = date == today
                        val isSelected = date == selected
                        val hasPersonalEvent = date in datesWithEvents
                        val hasSchoolEvent = schoolEvents.any { it.date == date }
                        val weekend = column >= 5
                        val cell = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(dp(2), dp(4), dp(2), dp(4))
                            background = roundedBackground(
                                when {
                                    isSelected -> accentColor
                                    isToday -> Color.argb(48, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                                    else -> Color.argb(18, 255, 255, 255)
                                }, 12
                            )
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                selected = date
                                redraw()
                            }
                            addView(TextView(this@MainActivity).apply {
                                text = dayNumber.toString()
                                gravity = Gravity.CENTER
                                textAlignment = View.TEXT_ALIGNMENT_CENTER
                                textSize = 15f
                                setTypeface(typeface, if (isToday || isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                                setTextColor(when {
                                    isSelected -> Color.WHITE
                                    isToday -> accentColor
                                    weekend -> Color.rgb(190, 165, 220)
                                    else -> white
                                })
                            }, LinearLayout.LayoutParams(-1, dp(28)))
                            val markers = LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER
                            }
                            if (hasSchoolEvent) {
                                markers.addView(View(this@MainActivity).apply {
                                    background = roundedBackground(
                                        if (isSelected) Color.WHITE else Color.rgb(83, 190, 255), 8
                                    )
                                }, LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                                    marginEnd = if (hasPersonalEvent) dp(4) else 0
                                })
                            }
                            if (hasPersonalEvent) {
                                markers.addView(View(this@MainActivity).apply {
                                    background = roundedBackground(if (isSelected) Color.WHITE else accentColor, 8)
                                }, LinearLayout.LayoutParams(dp(7), dp(7)))
                            }
                            addView(markers, LinearLayout.LayoutParams(-1, dp(10)).apply {
                                topMargin = dp(3)
                            })
                        }
                        row.addView(cell, cellParams)
                    }
                }
                grid.addView(row)
            }
            host.addView(grid)

            host.addView(label("WYDARZENIA • " + selected, 11f, Color.rgb(180, 205, 239), true),
                params(0, 8, 0, 6))
            host.addView(actionButton("+  DODAJ WYDARZENIE") {
                addAgendaEvent(selected) { redraw() }
            }, params(0, 0, 0, 8))

            val events = readLocalArray("personal_events")
            val selectedEvents = (0 until events.length()).mapNotNull { events.optJSONObject(it) }
                .filter { it.optString("date") == selected }
            if (selectedEvents.isNotEmpty()) {
                selectedEvents.forEach { event ->
                    val itemCard = card()
                    itemCard.addView(label(event.optString("title"), 15f, white, true))
                    if (event.optString("description").isNotBlank()) {
                        itemCard.addView(label(event.optString("description"), 12f, muted), params(0, 4, 0, 0))
                    }
                    itemCard.addView(actionButton("EDYTUJ WYDARZENIE") {
                        addAgendaEvent(selected, event) { redraw() }
                    }, params(0, 6, 0, 4))
                    itemCard.addView(actionButton("USUŃ WYDARZENIE") {
                        AlertDialog.Builder(this).setTitle("Usunąć wydarzenie?")
                            .setMessage(event.optString("title"))
                            .setNegativeButton("Anuluj", null)
                            .setPositiveButton("Usuń") { _, _ ->
                                val kept = JSONArray()
                                for (i in 0 until events.length()) {
                                    val item = events.optJSONObject(i) ?: continue
                                    if (item.optString("id") != event.optString("id")) kept.put(item)
                                }
                                saveLocalArray("personal_events", kept)
                                redraw()
                            }.show()
                    })
                    host.addView(itemCard, params(0, 0, 0, 8))
                }
            }

            val selectedSchoolEvents = schoolEvents.filter { it.date == selected }
            if (selectedSchoolEvents.isNotEmpty()) {
                host.addView(label("WYDARZENIA SZKOLNE Z LIBRUSA", 11f, Color.rgb(83, 190, 255), true),
                    params(0, 10, 0, 6))
                selectedSchoolEvents.forEach { (_, event, eventDate) ->
                    val eventCard = card()
                    val title = firstValue(event, "title", "topic", "name", "subject")
                        .ifBlank { "Wydarzenie szkolne" }
                    eventCard.addView(label(title, 15f, white, true))
                    val time = firstValue(event, "time", "start_time", "hour_from")
                    if (time.isNotBlank() && time != eventDate) {
                        eventCard.addView(label(time, 12f, accentColor), params(0, 3, 0, 0))
                    }
                    val description = firstValue(event, "description", "content", "details", "comment")
                    if (description.isNotBlank() && description != title) {
                        eventCard.addView(label(description, 12f, muted), params(0, 3, 0, 0))
                    }
                    host.addView(eventCard, params(0, 0, 0, 8))
                }
            }
            if (selectedEvents.isEmpty() && selectedSchoolEvents.isEmpty()) {
                host.addView(infoCard("Brak wydarzeń w wybranym dniu."))
            }
        }
        redraw()
    }

    private fun addAgendaEvent(defaultDate: String, existing: JSONObject? = null, onSaved: () -> Unit) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 0)
        }
        val title = EditText(this).apply {
            hint = "Nazwa wydarzenia"
            setText(existing?.optString("title", "").orEmpty())
            setTextColor(white)
            setHintTextColor(muted)
            setSingleLine(true)
        }
        val date = EditText(this).apply {
            hint = "Data RRRR-MM-DD"
            setText(existing?.optString("date", defaultDate) ?: defaultDate)
            setTextColor(white)
            setHintTextColor(muted)
            setSingleLine(true)
        }
        val description = EditText(this).apply {
            hint = "Opis (opcjonalnie)"
            setText(existing?.optString("description", "").orEmpty())
            setTextColor(white)
            setHintTextColor(muted)
            minLines = 2
        }
        fields.addView(title)
        fields.addView(date, params(0, 6, 0, 0))
        fields.addView(description, params(0, 6, 0, 0))
        AlertDialog.Builder(this).setTitle(if (existing == null) "Nowe wydarzenie" else "Edytuj wydarzenie").setView(fields)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz") { _, _ ->
                val eventTitle = title.text.toString().trim()
                val eventDate = date.text.toString().trim()
                // Wymagaj pełnej, poprawnej daty ISO; SimpleDateFormat.parse()
                // potrafił zaakceptować poprawny prefiks z błędnym dopiskiem.
                val valid = FeatureDateParser.parseIsoDateOnly(eventDate) != null
                if (eventTitle.isBlank() || !valid) {
                    Toast.makeText(this, "Podaj nazwę i poprawną datę RRRR-MM-DD.", Toast.LENGTH_LONG).show()
                } else {
                    val events = readLocalArray("personal_events")
                    val eventId = existing?.optString("id")?.takeIf { it.isNotBlank() }
                        ?: System.currentTimeMillis().toString()
                    val updated = JSONArray()
                    for (index in 0 until events.length()) {
                        val item = events.optJSONObject(index) ?: continue
                        if (item.optString("id") != eventId) updated.put(item)
                    }
                    updated.put(JSONObject().put("id", eventId)
                        .put("title", eventTitle).put("date", eventDate)
                        .put("description", description.text.toString().trim()))
                    saveLocalArray("personal_events", updated)
                    onSaved()
                }
            }.show()
    }

    private fun renderMessagesSection(parent: LinearLayout, data: JSONObject) {
        parent.addView(actionButton("NAPISZ WIADOMOŚĆ") { openNewMessageComposer() }, params(0, 0, 0, 10))
        val search = EditText(this).apply {
            hint = "Szukaj po temacie, nadawcy lub treści…"
            setSingleLine(true)
            textSize = 14f
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(16, 12, 16, 12)
            background = roundedBackground(panelLight, 14)
        }
        parent.addView(search, params(0, 0, 0, 10))
        var selectedFilter = "all"
        var currentQuery = ""
        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        parent.addView(filters, params(0, 0, 0, 10))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(results)

        fun savedKeys(key: String): Set<String> {
            val array = readLocalArray(key)
            return (0 until array.length()).map { array.optString(it) }.toSet()
        }
        fun messageKey(item: JSONObject, category: String): String {
            val id = firstValue(item, "id", "message_id", "messageId", "announcement_id")
            val identity = id.ifBlank {
                listOf(
                    firstValue(item, "title", "subject", "topic"),
                    firstValue(item, "sender_name", "sender", "author", "teacher_name", "from"),
                    firstValue(item, "send_date", "date", "created_at", "sent_at")
                ).joinToString("|")
            }
            return "$category:$identity"
        }
        fun toggleSaved(key: String, storageKey: String, value: Boolean) {
            val old = readLocalArray(storageKey)
            val updated = JSONArray()
            for (index in 0 until old.length()) {
                val saved = old.optString(index)
                if (saved != key) updated.put(saved)
            }
            if (value) updated.put(key)
            saveLocalArray(storageKey, updated)
        }
        fun renderFiltered() {
            results.removeAllViews()
            filters.removeAllViews()
            val choices = listOf(
                "all" to "WSZYSTKIE", "unread" to "NIEPRZECZYTANE",
                "pinned" to "PRZYPINANE", "archived" to "ARCHIWUM"
            )
            choices.forEach { (filter, caption) ->
                val chip = TextView(this).apply {
                    text = caption
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(7, 11, 7, 11)
                    setTextColor(if (selectedFilter == filter) Color.WHITE else muted)
                    background = roundedBackground(if (selectedFilter == filter) accentColor else panelLight, 9)
                    setOnClickListener {
                        selectedFilter = filter
                        renderFiltered()
                    }
                }
                filters.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = (3 * resources.displayMetrics.density).toInt()
                })
            }
            val needle = currentQuery.trim().lowercase(Locale.ROOT)
            val pinned = savedKeys("message_pinned")
            val archived = savedKeys("message_archived")
            fun filtered(source: JSONArray?, category: String): JSONArray {
                val output = JSONArray()
                if (source == null) return output
                for (i in 0 until source.length()) {
                    val item = source.optJSONObject(i) ?: continue
                    val key = messageKey(item, category)
                    val isPinned = key in pinned
                    val isArchived = key in archived
                    val isUnread = category == "Wiadomości" && firstValue(item, "read_date", "readDate").isBlank()
                    if (selectedFilter == "pinned" && !isPinned) continue
                    if (selectedFilter == "archived" && !isArchived) continue
                    if (selectedFilter == "unread" && (!isUnread || isArchived)) continue
                    if (selectedFilter != "archived" && isArchived) continue
                    val searchable = listOf(
                        firstValue(item, "title", "subject", "topic"),
                        firstValue(item, "sender_name", "sender", "author", "teacher_name", "from"),
                        firstValue(item, "send_date", "date", "created_at", "sent_at"),
                        firstValue(item, "content", "message", "body", "text", "content_html", "message_content", "description")
                    ).joinToString(" ").lowercase(Locale.ROOT)
                    if (needle.isBlank() || searchable.contains(needle)) output.put(item)
                }
                return output
            }
            val messages = filtered(data.optJSONArray("messages"), "Wiadomości")
            val announcements = filtered(data.optJSONArray("announcements"), "Ogłoszenia")
            if (messages.length() + announcements.length() == 0) {
                results.addView(infoCard(when (selectedFilter) {
                    "pinned" -> "Nie masz przypiętych wiadomości."
                    "archived" -> "Archiwum jest puste."
                    "unread" -> "Nie ma nieprzeczytanych wiadomości."
                    else -> if (needle.isNotBlank()) "Nie znaleziono wiadomości pasujących do: $currentQuery" else "Brak wiadomości."
                }))
            } else {
                renderMessageArray(results, messages, "Wiadomości", { item, category -> messageKey(item, category) }, { storageKey -> savedKeys(storageKey) }, { key, storageKey, value -> toggleSaved(key, storageKey, value) }, { renderFiltered() })
                renderMessageArray(results, announcements, "Ogłoszenia", { item, category -> messageKey(item, category) }, { storageKey -> savedKeys(storageKey) }, { key, storageKey, value -> toggleSaved(key, storageKey, value) }, { renderFiltered() })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString().orEmpty()
                renderFiltered()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderFiltered()
    }

    private fun openNewMessageComposer() {
        if (!requireParentProfile("Nowa wiadomość")) return
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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
        if (!requireParentProfile("Wysyłanie wiadomości")) return
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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
        heading: String,
        messageKey: (JSONObject, String) -> String,
        savedKeys: (String) -> Set<String>,
        toggleSaved: (String, String, Boolean) -> Unit,
        onChanged: () -> Unit
    ) {
        if (array == null || array.length() == 0) return
        parent.addView(sectionHeader(heading), params(0, 18, 0, 8))
        val pinnedKeys = savedKeys("message_pinned")
        val archivedKeys = savedKeys("message_archived")
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = firstValue(item, "title", "subject", "topic").ifBlank { heading.removeSuffix("i") }
            val sender = firstValue(item, "sender_name", "sender", "author", "teacher_name", "from")
            val date = firstValue(item, "send_date", "date", "created_at", "sent_at")
            val body = firstValue(item, "content", "message", "body", "text", "content_html", "message_content", "description")
            val key = messageKey(item, heading)
            val pinned = key in pinnedKeys
            val archived = key in archivedKeys
            val isUnread = heading == "Wiadomości" &&
                firstValue(item, "read_date", "readDate").isBlank()
            // Każdy wpis ma własną, lekko odróżnioną powierzchnię.
            // Nie zmieniamy wspólnego card(), aby nie naruszyć pozostałych zakładek.
            val card = card().apply {
                setPadding(16, 15, 16, 15)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    if (isUnread) {
                        intArrayOf(Color.rgb(37, 48, 69), Color.rgb(25, 36, 55))
                    } else {
                        intArrayOf(Color.rgb(29, 41, 59), Color.rgb(21, 31, 47))
                    }
                ).apply {
                    cornerRadius = 18f * resources.displayMetrics.density
                    setStroke(
                        (1 * resources.displayMetrics.density).toInt(),
                        if (isUnread) blendColor(accentColor, Color.rgb(91, 112, 143), 0.48f)
                        else Color.rgb(55, 70, 92)
                    )
                }
                elevation = 3f * resources.displayMetrics.density
            }
            val focusKey = messageKey(item, heading)
            if (pendingTimelineTargetKey == focusKey) card.tag = "timeline_target:$focusKey"
            card.addView(label(title, 15f, white, true))
            if (sender.isNotBlank()) card.addView(label("OD  $sender", 11f, Color.rgb(170, 188, 211), true))
            if (date.isNotBlank()) card.addView(label(date, 11f, muted))
            if (heading == "Wiadomości") {
                val messageReadDate = firstValue(item, "read_date", "readDate")
                val readChip = statusChip(
                    if (messageReadDate.isBlank()) "NIEPRZECZYTANA" else "PRZECZYTANA",
                    if (messageReadDate.isBlank()) warningColor else successColor
                )
                card.addView(readChip, params(0, 5, 0, 0))
                if (body.isNotBlank()) card.addView(label(body.take(220) + if (body.length > 220) "…" else "", 13f, white))
                else card.addView(label("Podgląd wiadomości niedostępny.", 12f, muted))
                val messageId = firstValue(item, "id", "message_id", "messageId")
                if (messageId.isNotBlank()) {
                    card.addView(actionButton("OTWÓRZ CAŁĄ WIADOMOŚĆ") {
                        fetchFullMessage(messageId, title, sender, date) {
                            item.put("read_date", "read")
                            readChip.text = getString(R.string.message_read)
                            readChip.setTextColor(successColor)
                        }
                    })
                } else {
                    card.addView(label("Brak identyfikatora wiadomości — nie można pobrać pełnej treści.", 12f, warningColor))
                }
            } else if (body.isNotBlank()) {
                card.addView(label(body.take(220) + if (body.length > 220) "…" else "", 13f, white))
                card.addView(actionButton("OTWÓRZ OGŁOSZENIE") { showMessageDetails(title, sender, date, body) })
            } else {
                card.addView(label("Brak treści ogłoszenia.", 12f, muted))
            }
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actions.addView(actionButton(if (pinned) "ODPINAJ" else "PRZYPNIJ") {
                toggleSaved(key, "message_pinned", !pinned)
                onChanged()
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = (5 * resources.displayMetrics.density).toInt()
            })
            actions.addView(actionButton(if (archived) "PRZYWRÓĆ" else "ARCHIWIZUJ") {
                toggleSaved(key, "message_archived", !archived)
                onChanged()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(actions, params(0, 8, 0, 0))
            // 12 dp odstępu zapobiega zlewaniu się kolejnych wiadomości na ekranie.
            parent.addView(card, params(0, 0, 0, 12))
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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
        if (!requireParentProfile("Pobieranie załączników zadań")) return
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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
        if (!requireParentProfile("Pobieranie załączników wiadomości")) return
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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
        if (!requireParentProfile("Odpowiadanie na wiadomości")) return
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
                    put("profile", activeProfileType)
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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
    // KATALOG NAUCZYCIELI
    // ---------------------------------------------------------

    private data class TeacherDirectoryEntry(
        val id: String,
        val name: String,
        val subjects: Set<String>,
        val email: String,
        val phone: String,
        val room: String,
        val searchable: String
    )

    private fun renderTeachersDirectory(parent: LinearLayout, data: JSONObject) {
        val rawTeachers = data.opt("teachers")
        val teachers = rawTeachers as? JSONObject
        val subjects = data.optJSONObject("subjects")
        val byId = linkedMapOf<String, MutableMap<String, Any>>()

        fun scalarField(obj: JSONObject, vararg keys: String): String {
            for (key in keys) {
                val raw = obj.opt(key)
                if (raw == null || raw == JSONObject.NULL || raw is JSONObject || raw is JSONArray) continue
                val value = raw.toString().trim()
                if (value.isNotBlank() && value != "null") return value
            }
            return ""
        }

        fun addTeacher(
            id: String,
            name: String,
            subject: String = "",
            email: String = "",
            phone: String = "",
            room: String = ""
        ) {
            val key = id.ifBlank { name.lowercase(Locale("pl", "PL")) }
            if (key.isBlank() && name.isBlank()) return
            val entry = byId.getOrPut(key) {
                mutableMapOf(
                    "name" to name.ifBlank { "Nauczyciel" },
                    "subjects" to linkedSetOf<String>(),
                    "email" to email,
                    "phone" to phone,
                    "room" to room
                )
            }
            if (name.isNotBlank() && (
                    (entry["name"] as? String).isNullOrBlank() ||
                        entry["name"] == "Nauczyciel"
                    )
            ) {
                entry["name"] = name
            }
            if (subject.isNotBlank()) (entry["subjects"] as MutableSet<String>).add(subject)
            if (email.isNotBlank() && (entry["email"] as? String).isNullOrBlank()) entry["email"] = email
            if (phone.isNotBlank() && (entry["phone"] as? String).isNullOrBlank()) entry["phone"] = phone
            if (room.isNotBlank() && (entry["room"] as? String).isNullOrBlank()) entry["room"] = room
        }

        fun readTeacher(idHint: String, value: Any?) {
            when (value) {
                is String -> addTeacher(idHint, value)
                is JSONObject -> {
                    val id = firstValue(value, "id", "teacher_id", "user_id").ifBlank { idHint }
                    val first = firstValue(value, "first_name", "firstname", "given_name")
                    val last = firstValue(value, "last_name", "surname", "lastname", "family_name")
                    val fullName = firstDisplayValue(
                        value, "name", "full_name", "teacher_name", "display_name", "title"
                    ).ifBlank { listOf(first, last).filter { it.isNotBlank() }.joinToString(" ") }
                    val subject = firstDisplayValue(
                        value, "subject_name", "subject", "subjects", "teaching_subjects"
                    )
                    addTeacher(
                        id,
                        fullName.ifBlank { "Nauczyciel" },
                        subject,
                        firstValue(value, "email", "mail", "email_address"),
                        firstValue(value, "phone", "phone_number", "telephone", "mobile"),
                        firstValue(value, "room", "room_number", "classroom")
                    )
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        readTeacher("$idHint:$index", value.opt(index))
                    }
                }
            }
        }

        when (rawTeachers) {
            is JSONObject -> {
                val keys = rawTeachers.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    readTeacher(key, rawTeachers.opt(key))
                }
            }
            is JSONArray -> {
                for (index in 0 until rawTeachers.length()) {
                    readTeacher(index.toString(), rawTeachers.opt(index))
                }
            }
        }

        // Librus często zwraca słownik ID -> nazwisko, a powiązania
        // nauczyciel–przedmiot są dopiero w planie lekcji. Uzupełnij katalog
        // na podstawie rzeczywiście pobranych lekcji, nie zgaduj przedmiotów.
        fun collectLesson(value: Any?) {
            when (value) {
                is JSONArray -> {
                    for (index in 0 until value.length()) collectLesson(value.opt(index))
                }
                is JSONObject -> {
                    val teacherId = scalarField(value, "teacher_id", "teacher")
                    val subjectId = scalarField(value, "subject_id", "subject")
                    val teacherName = firstDisplayValue(value, "teacher_name")
                    val subjectName = lookupName(subjects, subjectId)
                        .ifBlank { firstDisplayValue(value, "subject_name") }
                    if (teacherId.isNotBlank() || teacherName.isNotBlank()) {
                        val teacherObject = teachers?.optJSONObject(teacherId)
                        val dictionaryName = teacherObject?.let { teacher ->
                            firstDisplayValue(
                                teacher, "name", "full_name", "teacher_name", "display_name"
                            ).ifBlank {
                                listOf(
                                    firstValue(teacher, "first_name", "firstname"),
                                    firstValue(teacher, "last_name", "surname", "lastname")
                                ).filter { it.isNotBlank() }.joinToString(" ")
                            }
                        }.orEmpty()
                        val resolvedName = dictionaryName
                            .ifBlank { lookupName(teachers, teacherId) }
                            .ifBlank { teacherName }
                        addTeacher(teacherId, resolvedName, subjectName)
                    }
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = value.opt(key)
                        if (child is JSONObject || child is JSONArray) collectLesson(child)
                    }
                }
            }
        }
        collectLesson(data.opt("timetable"))

        val entries = byId.map { (id, fields) ->
            val name = fields["name"] as? String ?: "Nauczyciel"
            val subjectSet = fields["subjects"] as? Set<String> ?: emptySet()
            val email = fields["email"] as? String ?: ""
            val phone = fields["phone"] as? String ?: ""
            val room = fields["room"] as? String ?: ""
            val searchable = listOf(name, subjectSet.joinToString(" "), email, phone, room, id)
                .joinToString(" ")
                .lowercase(Locale("pl", "PL"))
            TeacherDirectoryEntry(id, name, subjectSet, email, phone, room, searchable)
        }.distinctBy { it.id }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        parent.addView(sectionHeader("KATALOG NAUCZYCIELI"), params(0, 2, 0, 6))
        val summary = label("", 12f, muted)
        parent.addView(summary, params(0, 0, 0, 10))

        if (entries.isEmpty()) {
            parent.addView(infoCard("Librus nie zwrócił listy nauczycieli. Po odświeżeniu sprawdź, czy konto ma dostęp do danych kadry."))
            return
        }

        val search = EditText(this).apply {
            hint = "Szukaj nazwiska, przedmiotu lub kontaktu"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 14f
            setPadding(
                (15 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (15 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt()
            )
            background = roundedBackground(panelLight, 13)
        }
        parent.addView(search, params(0, 0, 0, 12))

        val resultList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        parent.addView(resultList)

        fun renderResults(query: String) {
            resultList.removeAllViews()
            val normalized = query.trim().lowercase(Locale("pl", "PL"))
            val filtered = entries.filter {
                normalized.isBlank() || it.searchable.contains(normalized)
            }
            summary.text = if (normalized.isBlank()) {
                "${entries.size} nauczycieli"
            } else {
                "Wyniki wyszukiwania: ${filtered.size} z ${entries.size}"
            }

            if (filtered.isEmpty()) {
                resultList.addView(infoCard("Nie znaleziono nauczyciela dla podanej frazy."))
                return
            }

            filtered.forEach { teacher ->
                val itemCard = card()
                val header = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val initials = teacher.name
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .joinToString("")
                    .ifBlank { "N" }
                val avatar = TextView(this).apply {
                    text = initials
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(white)
                    background = roundedBackground(accentColor, 14)
                }
                header.addView(
                    avatar,
                    LinearLayout.LayoutParams(
                        (44 * resources.displayMetrics.density).toInt(),
                        (44 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginEnd = (12 * resources.displayMetrics.density).toInt()
                    }
                )
                val identity = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }
                identity.addView(label(teacher.name, 15f, white, true))
                val subjectLine = teacher.subjects.sortedWith(String.CASE_INSENSITIVE_ORDER)
                    .joinToString(" • ")
                identity.addView(
                    label(
                        subjectLine.ifBlank { "Brak przypisanego przedmiotu w pobranych danych" },
                        12f,
                        if (subjectLine.isBlank()) muted else Color.rgb(174, 196, 224)
                    )
                )
                header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
                itemCard.addView(header)

                if (teacher.email.isNotBlank()) {
                    itemCard.addView(label("E-mail: ${teacher.email}", 12f, muted), params(0, 9, 0, 0))
                }
                if (teacher.phone.isNotBlank()) {
                    itemCard.addView(label("Telefon: ${teacher.phone}", 12f, muted), params(0, 3, 0, 0))
                }
                if (teacher.room.isNotBlank()) {
                    itemCard.addView(label("Pokój: ${teacher.room}", 12f, muted), params(0, 3, 0, 0))
                }
                resultList.addView(itemCard, params(0, 0, 0, 8))
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderResults("")
    }

    // ---------------------------------------------------------
    // FUNKCJE SZKOLNE: ZADANIA, TERMINARZ, UWAGI I NUMEREK
    // ---------------------------------------------------------

    private fun gradeFocusKey(item: JSONObject, sourceKey: String): String {
        val id = firstValue(item, "id", "grade_id", "gradeId", "assessment_id")
        val identity = id.ifBlank {
            listOf(
                firstValue(item, "subject_id", "subject_name", "subject"),
                firstValue(item, "date", "grade_date", "add_date", "created_at", "modified_at"),
                firstDisplayValue(item, "value", "grade", "symbol", "text", "result", "assessment",
                    "grade_value", "grade_text", "mark", "grade_description", "skill", "name"),
                firstDisplayValue(item, "description", "skill_description", "comment",
                    "teacher_comment", "feedback", "note", "details")
            ).joinToString("|")
        }
        return "grade:$sourceKey:$identity"
    }

    private fun featureFocusKey(sourceKey: String, item: JSONObject): String {
        val id = firstValue(item, "id", "assignment_id", "homework_id", "event_id", "note_id")
        val identity = id.ifBlank {
            listOf(
                firstValue(item, "title", "topic", "name", "subject", "text"),
                firstValue(item, "due_date", "deadline", "date", "start_date", "event_date"),
                firstValue(item, "subject_name", "subject"),
                firstValue(item, "description", "content", "details", "comment")
            ).joinToString("|")
        }
        return "feature:$sourceKey:$identity"
    }

    private fun renderSchoolTimeline(parent: LinearLayout, data: JSONObject) {
        data class TimelineEntry(
            val section: String,
            val category: String,
            val title: String,
            val detail: String,
            val date: String,
            val icon: String,
            val targetKey: String
        )

        val entries = mutableListOf<TimelineEntry>()

        fun collect(
            keys: List<String>,
            section: String,
            category: String,
            icon: String,
            dateKeys: Array<String>,
            titleKeys: Array<String>,
            detailKeys: Array<String>
        ) {
            keys.forEach { key ->
                val array = data.optJSONArray(key) ?: return@forEach
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val date = firstValue(item, *dateKeys)
                    val title = firstValue(item, *titleKeys).ifBlank { category }
                    val detail = firstValue(item, *detailKeys)
                    val targetKey = if (section == "WIADOMOŚCI") {
                        val messageCategory = if (key == "announcements") "Ogłoszenia" else "Wiadomości"
                        val identity = firstValue(item, "id", "message_id", "messageId", "announcement_id")
                            .ifBlank {
                                listOf(
                                    firstValue(item, "title", "subject", "topic"),
                                    firstValue(item, "sender_name", "sender", "author", "teacher_name", "from"),
                                    firstValue(item, "send_date", "date", "created_at", "sent_at")
                                ).joinToString("|")
                            }
                        "$messageCategory:$identity"
                    } else featureFocusKey(key, item)
                    entries.add(TimelineEntry(section, category, title, detail, date, icon, targetKey))
                }
            }
        }

        val subjects = data.optJSONObject("subjects")
        val gradeCategories = data.optJSONObject("grade_categories")
        val gradeSources = listOf("grades", "descriptive_grades", "partial_grades", "point_grades", "text_grades", "behaviour_grades")
        gradeSources.forEach { sourceKey ->
            val array = data.optJSONArray(sourceKey) ?: return@forEach
            for (index in 0 until array.length()) {
                val grade = array.optJSONObject(index) ?: continue
                val subjectObject = grade.optJSONObject("subject")
                val subjectId = firstValue(grade, "subject_id")
                    .ifBlank { subjectObject?.let { firstValue(it, "id", "subject_id") }.orEmpty() }
                val subject = firstDisplayValue(grade, "subject_name")
                    .ifBlank { subjectObject?.let { firstDisplayValue(it, "name", "title", "value") }.orEmpty() }
                    .ifBlank { lookupName(subjects, subjectId) }
                    .ifBlank {
                        val rawSubject = grade.opt("subject")
                        if (rawSubject is String) lookupName(subjects, rawSubject).ifBlank { rawSubject } else ""
                    }
                    .ifBlank { "Pozostałe" }
                val value = firstDisplayValue(grade, "value", "grade", "symbol", "text", "result", "assessment",
                    "grade_value", "grade_text", "mark", "grade_description", "skill", "name")
                    .ifBlank { "Ocena opisowa" }
                val description = firstDisplayValue(grade, "description", "skill_description", "comment",
                    "teacher_comment", "feedback", "note", "details")
                val categoryId = firstValue(grade, "category_id")
                val categoryName = firstDisplayValue(grade, "category_name", "category")
                    .ifBlank {
                        if (sourceKey == "grades") lookupName(gradeCategories, categoryId) else ""
                    }
                    .ifBlank { grade.optJSONObject("category")?.let { firstDisplayValue(it, "name", "title", "value") }.orEmpty() }
                val comments = displayJsonValue(grade.opt("comments"))
                val teacherObject = grade.optJSONObject("teacher")
                val teacherId = firstValue(grade, "teacher_id")
                    .ifBlank { teacherObject?.let { firstValue(it, "id", "teacher_id") }.orEmpty() }
                val teacher = firstDisplayValue(grade, "teacher_name")
                    .ifBlank { teacherObject?.let { firstDisplayValue(it, "name", "title", "value") }.orEmpty() }
                    .ifBlank { lookupName(data.optJSONObject("teachers"), teacherId) }
                    .ifBlank {
                        val rawTeacher = grade.opt("teacher")
                        if (rawTeacher is String) lookupName(data.optJSONObject("teachers"), rawTeacher).ifBlank { rawTeacher } else ""
                    }
                val detailParts = mutableListOf<String>()
                val seenDetails = mutableSetOf<String>()
                fun addGradeDetail(heading: String, rawValue: String) {
                    val value = cleanGradeDetail(rawValue, heading, "Za co")
                    if (heading == "Komentarz" && sameGradeDetail(value, description)) return
                    val identity = heading.lowercase(Locale("pl", "PL")) + "|" +
                        value.lowercase(Locale("pl", "PL")).replace(Regex("\\s+"), " ").trim()
                    if (value.isBlank() || !seenDetails.add(identity)) return
                    detailParts.add(if (heading.isBlank()) value else "$heading: $value")
                }
                addGradeDetail("Za co", categoryName)
                addGradeDetail("", description)
                addGradeDetail("Komentarz", comments)
                if (teacher.isNotBlank()) addGradeDetail("Nauczyciel", teacher)
                val skillHeading = if (sourceKey == "descriptive_grades" || sourceKey == "partial_grades") {
                    "Kategoria"
                } else {
                    "Umiejętność"
                }
                listOf(
                    "requirements" to "Wymaganie",
                    "criteria" to "Kryterium",
                    "skills" to skillHeading,
                    "skill" to skillHeading
                ).forEach { (field, heading) ->
                    val raw = grade.opt(field)
                    if (raw != null && raw != JSONObject.NULL) {
                        val values = when (raw) {
                            is JSONArray -> (0 until raw.length()).map { displayJsonValue(raw.opt(it)) }
                            else -> listOf(displayJsonValue(raw))
                        }
                        values.forEach { addGradeDetail(heading, it) }
                    }
                }
                val date = firstValue(grade, "date", "add_date", "created_at", "modified_at")
                val category = when (sourceKey) {
                    "descriptive_grades" -> "Ocena opisowa"
                    "partial_grades" -> "Ocena cząstkowa"
                    "point_grades" -> "Ocena punktowa"
                    "text_grades" -> "Ocena tekstowa"
                    "behaviour_grades" -> "Ocena zachowania"
                    else -> "Ocena"
                }
                entries.add(TimelineEntry("OCENY", category, "$value • $subject",
                    detailParts.joinToString(" · "),
                    date, "✦", gradeFocusKey(grade, sourceKey)))
            }
        }
        collect(
            listOf("messages", "announcements"),
            "WIADOMOŚCI", "Wiadomość", "✉",
            arrayOf("send_date", "date", "created_at", "sent_at"),
            arrayOf("title", "subject", "topic", "name"),
            arrayOf("sender_name", "sender", "author", "teacher_name", "from")
        )
        val homeworkSource = listOf("homework_assignments", "homework", "homeworks")
            .firstOrNull { data.has(it) && data.opt(it) != JSONObject.NULL } ?: "homework"
        collect(
            listOf(homeworkSource),
            "ZADANIA DOMOWE", "Zadanie domowe", "▤",
            arrayOf("due_date", "deadline", "date", "created_at", "start_date"),
            arrayOf("title", "topic", "name", "subject_name"),
            arrayOf("description", "content", "teacher_name", "subject_name", "status")
        )
        val agendaSource = listOf("agenda", "events", "calendar_events").firstOrNull {
            data.has(it) && data.opt(it) != JSONObject.NULL
        } ?: "agenda"
        collect(
            listOf(agendaSource),
            "TERMINARZ", "Wydarzenie", "▦",
            arrayOf("start_date", "date", "event_date", "due_date"),
            arrayOf("title", "name", "topic", "subject"),
            arrayOf("description", "details", "type", "location")
        )
        val notesSource = listOf("notes", "behaviour_notes").firstOrNull {
            data.has(it) && data.opt(it) != JSONObject.NULL
        } ?: "notes"
        collect(
            listOf(notesSource),
            "UWAGI", "Uwaga", "✎",
            arrayOf("date", "created_at", "added_at"),
            arrayOf("title", "content", "text", "description", "type"),
            arrayOf("teacher_name", "author", "category", "comment")
        )

        val ordered = entries
            .distinctBy { listOf(it.section, it.date, it.title, it.detail).joinToString("|") }
            .sortedByDescending { parseFeatureDate(it.date)?.time ?: 0L }
            .take(60)

        parent.addView(infoCard(
            "Oś czasu łączy dane, które Librus rzeczywiście zwrócił. " +
                "Znaleziono wpisów: ${entries.size} • pokazano: ${ordered.size}. " +
                "Dotknij wpisu, aby otworzyć odpowiedni moduł."
        ), params(0, 0, 0, 10))

        val search = EditText(this).apply {
            hint = "Filtruj oś czasu…"
            setSingleLine(true)
            textSize = 14f
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(15, 12, 15, 12)
            background = roundedBackground(panelLight, 14)
        }
        parent.addView(search, params(0, 0, 0, 12))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(list)

        fun draw(query: String) {
            list.removeAllViews()
            val needle = query.trim().lowercase(Locale("pl", "PL"))
            val filtered = ordered.filter {
                needle.isBlank() || listOf(it.category, it.title, it.detail, it.date)
                    .joinToString(" ").lowercase(Locale("pl", "PL")).contains(needle)
            }
            if (filtered.isEmpty()) {
                list.addView(infoCard(if (needle.isBlank()) "Brak wpisów w dostępnych danych Librusa."
                    else "Nie znaleziono wpisów pasujących do wyszukiwania."))
                return
            }

            filtered.forEach { entry ->
                val density = resources.displayMetrics.density
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * density).toInt(), (12 * density).toInt(),
                        (14 * density).toInt(), (12 * density).toInt())
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.rgb(37, 51, 72), Color.rgb(25, 35, 52))).apply {
                        cornerRadius = 17f * density
                        setStroke((1 * density).toInt(), Color.rgb(59, 82, 111))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        pendingTimelineTargetKey = entry.targetKey
                        loadLibrusSection(entry.section)
                    }
                }
                val icon = TextView(this).apply {
                    text = entry.icon
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(accentColor)
                    background = roundedBackground(Color.argb(35,
                        Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)), 13)
                }
                item.addView(icon, LinearLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()))
                val copy = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((12 * density).toInt(), 0, 0, 0)
                }
                val top = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                top.addView(label(entry.category.uppercase(Locale("pl", "PL")), 9f, accentColor, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                val displayDate = parseFeatureDate(entry.date)?.let {
                    SimpleDateFormat("d MMM yyyy", Locale("pl", "PL")).format(it)
                } ?: entry.date.takeIf { it.isNotBlank() } ?: "Bez daty"
                top.addView(label(displayDate, 10f, muted))
                copy.addView(top)
                copy.addView(label(entry.title, 14f, white, true), params(0, 3, 0, 0))
                if (entry.detail.isNotBlank()) {
                    copy.addView(label(entry.detail, 11f, Color.rgb(185, 201, 222)), params(0, 2, 0, 0))
                }
                item.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
                list.addView(item, params(0, 0, 0, 8))
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                draw(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        draw("")
    }

    private fun renderFeatureArray(
        parent: LinearLayout,
        data: JSONObject,
        keys: List<String>,
        heading: String
    ) {
        val key = keys.firstOrNull { candidate ->
            val raw = data.opt(candidate)
            when (raw) {
                is JSONArray -> raw.length() > 0
                is JSONObject -> {
                    val nested = raw.optJSONArray("items") ?: raw.optJSONArray("entries")
                    (nested?.length() ?: raw.length()) > 0
                }
                else -> false
            }
        } ?: keys.firstOrNull { data.has(it) && data.opt(it) != JSONObject.NULL }
        val value = key?.let { data.opt(it) }

        if (value == null) {
            parent.addView(infoCard("Backend nie zwrócił sekcji „$heading”."))
            return
        }

        val items = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("items")
                ?: value.optJSONArray("entries")
                ?: JSONArray().apply { put(value) }
            else -> JSONArray()
        }
        if (items.length() == 0) {
            parent.addView(infoCard("Brak wpisów: $heading."))
            return
        }

        val sorted = (0 until items.length())
            .mapNotNull { items.optJSONObject(it) }
            .sortedWith(compareBy<JSONObject> {
                featureDateSortKey(firstValue(it, "due_date", "deadline", "date", "start_date"))
            }.thenBy { firstValue(it, "title", "topic", "name") })

        val isHomework = heading == "Zadania domowe"
        fun localKey(item: JSONObject): String {
            val title = firstValue(item, "title", "topic", "name", "subject", "text")
            val date = firstValue(item, "due_date", "deadline", "date", "start_date")
            val subject = firstValue(item, "subject_name", "subject")
            return firstValue(item, "id", "assignment_id", "homework_id")
                .ifBlank { "$title|$date|$subject" }
        }
        fun completedKeys(): Set<String> {
            val saved = readLocalArray("homework_done")
            return (0 until saved.length()).map { saved.optString(it) }.toSet()
        }

        val now = java.util.Date()
        val datedItems = sorted.count {
            firstValue(it, "due_date", "deadline", "date", "start_date").isNotBlank()
        }
        val doneAtStart = if (isHomework) sorted.count { localKey(it) in completedKeys() } else 0
        if (isHomework) {
            parent.addView(sectionHeader("TWOJA LISTA ZADAŃ"), params(0, 0, 0, 5))
            parent.addView(label(
                "Postęp zapisuje się lokalnie na tym urządzeniu. Nie zmienia statusu zadania w dzienniku Librus.",
                12f, muted
            ), params(0, 0, 0, 10))
            val summary = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBackground(panelLight, 14)
                setPadding(14, 12, 14, 12)
            }
            val summaryText = label("", 13f, white, true)
            summary.addView(summaryText, LinearLayout.LayoutParams(0, -2, 1f))
            val progress = TextView(this).apply {
                textSize = 19f
                setTextColor(successColor)
                typeface = Typeface.DEFAULT_BOLD
            }
            summary.addView(progress)
            parent.addView(summary, params(0, 0, 0, 10))

            var selectedFilter = "all"
            val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val filters = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            parent.addView(filters, params(0, 0, 0, 10))
            parent.addView(results)

            fun renderHomework() {
                val completed = completedKeys()
                val done = sorted.count { localKey(it) in completed }
                val open = sorted.size - done
                summaryText.text = getString(R.string.homework_summary, sorted.size, open, done)
                progress.text = if (sorted.isEmpty()) "0%" else "${(done * 100) / sorted.size}%"
                filters.removeAllViews()
                val choices = listOf("all" to "WSZYSTKIE", "open" to "DO ZROBIENIA", "done" to "WYKONANE")
                choices.forEach { (filter, caption) ->
                    val chip = TextView(this).apply {
                        text = caption
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setPadding(9, 10, 9, 10)
                        setTextColor(if (selectedFilter == filter) Color.WHITE else muted)
                        background = roundedBackground(
                            if (selectedFilter == filter) accentColor else panelLight, 10
                        )
                        setOnClickListener {
                            selectedFilter = filter
                            renderHomework()
                        }
                    }
                    filters.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        marginEnd = (4 * resources.displayMetrics.density).toInt()
                    })
                }
                results.removeAllViews()
                val visible = sorted.filter {
                    val isDone = localKey(it) in completed
                    selectedFilter == "all" || (selectedFilter == "done" && isDone) ||
                        (selectedFilter == "open" && !isDone)
                }
                if (visible.isEmpty()) {
                    results.addView(infoCard(
                        if (selectedFilter == "done") "Nie oznaczono jeszcze żadnego zadania jako wykonanego."
                        else if (selectedFilter == "open") "Świetnie! Nie masz zadań do zrobienia."
                        else "Brak zadań domowych."
                    ))
                    return
                }
                renderFeatureItems(results, data, visible, heading, now, ::localKey, ::renderHomework)
            }
            renderHomework()
            return
        }

        parent.addView(infoCard("Liczba wpisów: ${items.length()}" +
            if (datedItems > 0) " • z terminem: $datedItems" else ""))
        renderFeatureItems(parent, data, sorted, heading, now, ::localKey, null, key.orEmpty())
    }

    private fun renderFeatureItems(
        parent: LinearLayout,
        data: JSONObject,
        items: List<JSONObject>,
        heading: String,
        now: java.util.Date,
        localKey: (JSONObject) -> String,
        onHomeworkChanged: (() -> Unit)?,
        sourceKey: String = "homework"
    ) {
        // Odczyt i parsowanie lokalnego stanu tylko raz na render listy,
        // zamiast ponownego parsowania całego JSON dla każdego zadania.
        val completedHomework = if (heading == "Zadania domowe") {
            val saved = readLocalArray("homework_done")
            (0 until saved.length()).map { saved.optString(it) }.toSet()
        } else emptySet()

        for (item in items) {
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
            val focusKey = featureFocusKey(sourceKey, item)
            if (pendingTimelineTargetKey == focusKey) itemCard.tag = "timeline_target:$focusKey"
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
                val key = localKey(item)
                val isDone = key in completedHomework
                val completion = CheckBox(this).apply {
                    text = if (isDone) "Wykonane • zapisane na tym urządzeniu" else "Oznacz jako wykonane"
                    isChecked = isDone
                    setTextColor(if (isDone) successColor else muted)
                    textSize = 12f
                    setOnCheckedChangeListener { _, checked ->
                        val saved = readLocalArray("homework_done")
                        val updated = JSONArray()
                        for (index in 0 until saved.length()) {
                            val savedKey = saved.optString(index)
                            if (savedKey != key) updated.put(savedKey)
                        }
                        if (checked) updated.put(key)
                        saveLocalArray("homework_done", updated)
                        onHomeworkChanged?.invoke()
                    }
                }
                itemCard.addView(completion, params(0, 8, 0, 4))
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
        val date = FeatureDateParser.parse(value) ?: return null
        return java.util.Date.from(
            date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        )
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

        backendStatusText.text = getString(R.string.backend_checking_session)
        backendStatusText.setTextColor(muted)

        val accessToken = supabase.auth.currentAccessTokenOrNull()

        if (accessToken.isNullOrBlank()) {
            backendStatusText.text = getString(R.string.backend_no_active_token)
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
                if (!isFinishing && !isDestroyed && ::backendStatusText.isInitialized) {
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
        val prefs = LibrusNotificationSettings.preferences(applicationContext, activeProfileId)
        val checked = BooleanArray(keys.size) { prefs.getBoolean(keys[it], true) }
        AlertDialog.Builder(this)
            .setTitle("Powiadomienia EduFlow")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Zapisz") { _, _ ->
                prefs.edit().apply {
                    keys.forEachIndexed { index, key -> putBoolean(key, checked[index]) }
                }.apply()
            }
            .setNegativeButton("Diagnostyka") { _, _ -> showNotificationDiagnostics() }
            .setNeutralButton("Uprawnienia") { _, _ -> requestNotificationPermissionIfNeeded() }
            .show()
    }

    private fun showNotificationDiagnostics() {
        val message = LibrusNotificationDiagnostics.summary(applicationContext, activeProfileId)
        AlertDialog.Builder(this)
            .setTitle("Diagnostyka powiadomień")
            .setMessage(message)
            .setPositiveButton("Odśwież") { _, _ -> showNotificationDiagnostics() }
            .setNeutralButton("Test lokalny") { _, _ ->
                val sent = LibrusNotificationDiagnostics.sendTest(applicationContext, activeProfileId)
                val explanation = if (sent) {
                    "Wysłano testowe powiadomienie. Jeśli go nie widzisz, sprawdź ustawienia kanału „Aktualizacje EduFlow” i tryb Nie przeszkadzać."
                } else {
                    "Android blokuje powiadomienia aplikacji. Sprawdź uprawnienie i kanał „Aktualizacje EduFlow” w ustawieniach systemowych."
                }
                AlertDialog.Builder(this)
                    .setTitle("Test powiadomień")
                    .setMessage(explanation)
                    .setPositiveButton("OK", null)
                    .show()
                if (!sent && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermissionIfNeeded()
                }
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    private fun showAppearanceSettings(activeSection: String) {
        val options = arrayOf(
            "Niebieski — klasyczny",
            "Turkusowy — świeży",
            "Fioletowy — nowoczesny",
            "Bursztynowy — ciepły",
            "Mikołajki — świąteczna czerwień",
            "Boże Narodzenie — leśna zieleń",
            "Halloween — dyniowy pomarańcz",
            "Walentynki — różowy",
            "Wiosna — zielony"
        )
        val keys = arrayOf("blue", "teal", "violet", "amber", "red", "christmas", "halloween", "pink", "spring")
        val selected = keys.indexOf(uiPrefs.getString("accent", "blue")).coerceAtLeast(0)
        val motionEnabled = uiPrefs.getBoolean("motion", true)

        AlertDialog.Builder(this)
            .setTitle("Wygląd aplikacji")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                uiPrefs.edit().putString("accent", keys[which]).apply()
                dialog.dismiss()
                if (activeSection == "START" || activeSection == "WIĘCEJ") {
                    showHome()
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

    private fun requireParentProfile(feature: String): Boolean {
        if (activeProfileType == LibrusProfileStore.TYPE_PARENT) return true
        AlertDialog.Builder(this)
            .setTitle("Funkcja dla konta rodzica")
            .setMessage("$feature jest dostępne po przełączeniu na profil rodzica. Konto ucznia pozostaje oddzielne i służy do przeglądania danych ucznia.")
            .setPositiveButton("OK", null)
            .show()
        return false
    }

    private fun showProfileSwitcher() {
        if (syncInProgress) {
            Toast.makeText(this, "Poczekaj na zakończenie bieżącej operacji.", Toast.LENGTH_SHORT).show()
            return
        }
        val profiles = profileStore.getProfiles()
        val labels = profiles.map { profile ->
            val typeLabel = if (profile.accountType == LibrusProfileStore.TYPE_STUDENT) "Uczeń" else "Rodzic"
            val activeLabel = if (profile.id == activeProfileId) "  •  aktywny" else ""
            "${profile.displayName}  ·  $typeLabel$activeLabel"
        }.toTypedArray()
        val builder = AlertDialog.Builder(this)
            .setTitle("Profile EduFlow")
            .setItems(labels) { _, which ->
                val selected = profiles.getOrNull(which) ?: return@setItems
                if (selected.id != activeProfileId) activateProfile(selected)
            }
            .setPositiveButton("Dodaj konto") { _, _ -> showAddProfileTypePicker() }
            .setNegativeButton("Zamknij", null)
        if (profiles.size > 1) {
            builder.setNeutralButton("Usuń aktywne") { _, _ -> confirmRemoveActiveProfile() }
        }
        builder.show()
    }

    private fun showAddProfileTypePicker() {
        AlertDialog.Builder(this)
            .setTitle("Jakie konto dodajesz?")
            .setItems(arrayOf("Konto rodzica", "Konto ucznia / dziecka")) { _, which ->
                val type = if (which == 1) LibrusProfileStore.TYPE_STUDENT
                    else LibrusProfileStore.TYPE_PARENT
                showAddProfileNameDialog(type)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun showAddProfileNameDialog(accountType: String) {
        val nameInput = EditText(this).apply {
            hint = if (accountType == LibrusProfileStore.TYPE_STUDENT) "Imię dziecka lub własna nazwa" else "Np. Mama, Tata"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(nameInput, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle("Nazwa profilu")
            .setMessage("Nazwa będzie widoczna tylko na tym urządzeniu.")
            .setView(container)
            .setPositiveButton("Dodaj i połącz") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Wpisz nazwę profilu.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val profile = runCatching { profileStore.addProfile(name, accountType) }.getOrElse {
                    Toast.makeText(this, it.message ?: "Nie udało się dodać profilu.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                activateProfile(profile)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun activateProfile(profile: LibrusProfileStore.Profile, notificationSection: String? = null) {
        if (syncInProgress) return
        profileStore.setActiveProfileId(profile.id)
        activeProfileId = profile.id
        activeProfileType = profile.accountType
        librusVault = LibrusCredentialVault(applicationContext, activeProfileId)
        librusLoginMemory = null
        librusPasswordMemory = null
        librusSessionMemory = null
        librusDataMemory = null
        pendingNotificationSection = notificationSection
        autoRestoreStarted = false
        stopTimetableTicker()

        val saved = librusVault.load()
        if (saved == null) {
            showLibrusLogin()
            return
        }
        librusLoginMemory = saved.login
        librusPasswordMemory = saved.password
        librusSessionMemory = saved.sessionJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        LibrusNotificationScheduler.schedule(applicationContext, activeProfileId)
        showHome()
    }

    private fun confirmRemoveActiveProfile() {
        val profile = profileStore.getProfile(activeProfileId) ?: return
        if (profileStore.getProfiles().size <= 1) {
            Toast.makeText(this, "Nie można usunąć ostatniego profilu.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Usunąć profil ${profile.displayName}?")
            .setMessage("Zostaną usunięte zapisane na tym urządzeniu dane logowania i sesja tego profilu. Pozostałe konta pozostaną bez zmian.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Usuń") { _, _ ->
                val removedId = activeProfileId
                runCatching { librusVault.clear() }.onFailure {
                    Toast.makeText(this, "Nie udało się bezpiecznie usunąć danych: ${it.message}", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                runCatching {
                    LibrusProfileDataCleaner.clear(applicationContext, removedId)
                }.onFailure {
                    Toast.makeText(
                        this,
                        "Nie udało się wyczyścić historii profilu: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                LibrusNotificationScheduler.cancel(applicationContext, removedId)
                if (profileStore.removeProfile(removedId)) {
                    val next = profileStore.getProfiles().first()
                    activateProfile(next)
                }
            }
            .show()
    }

    private fun disconnectLibrus() {
        AlertDialog.Builder(this)
            .setTitle("Odłączyć konto Librus?")
            .setMessage("Z urządzenia zostaną usunięte zapisane dane logowania i sesja Librusa. Nie zmieni to danych na koncie szkolnym.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Odłącz") { _, _ ->
                try {
                    librusVault.clear()
                    LibrusNotificationScheduler.cancel(applicationContext, activeProfileId)
                    librusDataMemory = null
                    librusSessionMemory = null
                    librusLoginMemory = null
                    librusPasswordMemory = null
                    autoRestoreStarted = false
                    showHome()
                } catch (error: Exception) {
                    AlertDialog.Builder(this)
                        .setTitle("Nie udało się odłączyć Librusa")
                        .setMessage(error.message ?: "Nieznany błąd.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .show()
    }

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
                val motionEnabled = uiPrefs.getBoolean("motion", true)
                val indicator = View(this).apply {
                    background = roundedBackground(accentColor, 4)
                    if (motionEnabled) {
                        alpha = 0f
                        scaleX = 0.55f
                        scaleY = 0.8f
                    }
                }
                tile.addView(indicator, LinearLayout.LayoutParams((22 * density).toInt(), (3 * density).toInt()).apply {
                    bottomMargin = (2 * density).toInt()
                })
                if (motionEnabled) {
                    indicator.post {
                        indicator.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(190)
                            .start()
                    }
                }
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
                    "START" -> {
                        if (syncInProgress) pendingNotificationSection = "START"
                        else pendingNotificationSection = null
                        showHome()
                    }
                    "WIĘCEJ" -> {
                        if (syncInProgress) pendingNotificationSection = "WIĘCEJ"
                        else pendingNotificationSection = null
                        showMoreMenu(active)
                    }
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

    // ---------------------------------------------------------
    // O AUTORZE — niezależna karta informacyjna aplikacji
    // ---------------------------------------------------------

    private fun showAbout() {
        stopTimetableTicker()
        val density = resources.displayMetrics.density
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt(), (26 * density).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, pageGradient)
        }

        val hero = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(12, 35, 65), Color.rgb(20, 29, 54), Color.rgb(34, 25, 62))).apply {
                cornerRadius = 26f * density
                setStroke((1 * density).toInt(), Color.argb(150, 87, 142, 205))
            }
            clipChildren = false
            clipToPadding = false
            elevation = 5f * density
        }
        hero.addView(TechAboutArtView(this),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (190 * density).toInt(), Gravity.TOP))
        val heroText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding((20 * density).toInt(), (95 * density).toInt(), (20 * density).toInt(), (18 * density).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(220, 10, 18, 34)))
        }
        heroText.addView(label("POZNAJ TWÓRCĘ", 10f, Color.rgb(133, 205, 255), true))
        heroText.addView(label("EduFlow", 25f, Color.WHITE, true), params(0, 3, 0, 0))
        heroText.addView(label("Technologia, edukacja i wygoda w jednym miejscu", 12f, Color.rgb(205, 220, 240)))
        hero.addView(heroText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM))
        content.addView(hero, params(0, 0, 0, 18))

        val authorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (18 * density).toInt(), (20 * density).toInt(), (18 * density).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, surfaceGradient).apply {
                cornerRadius = 22f * density
                setStroke((1 * density).toInt(), Color.rgb(59, 83, 116))
            }
            elevation = 3f * density
        }
        authorCard.addView(label("AUTOR APLIKACJI", 10f, muted, true))
        authorCard.addView(label("Łukasz Gajor", 26f, white, true), params(0, 5, 0, 0))
        authorCard.addView(label("Niezależny projekt tworzony z myślą o wygodnym dostępie do szkolnych informacji.", 13f, Color.rgb(197, 211, 232)), params(0, 3, 0, 12))

        val emailRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((13 * density).toInt(), (12 * density).toInt(), (13 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(55, 70, 145, 220), Color.argb(28, 70, 145, 220))).apply {
                cornerRadius = 15f * density
                setStroke((1 * density).toInt(), Color.argb(105, 105, 175, 235))
            }
            isClickable = true
            isFocusable = true
        }
        emailRow.addView(label("✉", 21f, Color.rgb(133, 205, 255), true),
            LinearLayout.LayoutParams((38 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        val emailText = TextView(this).apply {
            text = "LGajor@gmail.com"
            textSize = 15f
            setTextColor(Color.rgb(145, 205, 255))
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setTypeface(null, Typeface.BOLD)
        }
        emailRow.addView(emailText, LinearLayout.LayoutParams(0, -2, 1f))
        emailRow.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:LGajor@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Kontakt — EduFlow")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Brak aplikacji pocztowej na urządzeniu.", Toast.LENGTH_SHORT).show()
            }
        }
        authorCard.addView(emailRow)
        authorCard.addView(label("Dotknij adresu, aby napisać wiadomość e-mail.", 11f, muted), params(0, 5, 0, 0))
        content.addView(authorCard, params(0, 0, 0, 14))

        val infoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt())
            background = roundedBackground(panel, 18)
        }
        infoCard.addView(label("O PROJEKCIE", 10f, accentColor, true))
        infoCard.addView(label("EduFlow to niezależna aplikacja pomocnicza do obsługi szkolnych spraw. Nie jest oficjalnym produktem ani aplikacją firmy Librus. Dane szkolne są pobierane z konta użytkownika w zakresie udostępnianym przez dziennik i szkołę.", 12f, Color.rgb(190, 204, 224)), params(0, 5, 0, 0))
        content.addView(infoCard, params(0, 0, 0, 12))
        content.addView(actionButton("ZMIEN PROFIL / DODAJ KONTO") { showProfileSwitcher() }, params(0, 4, 0, 8))
        content.addView(actionButton("‹  WRÓĆ DO PULPITU") { showHome() }, params(0, 4, 0, 0))

        val scroll = ScrollView(this).apply { addView(content) }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(buildBottomNavigation("WIĘCEJ"))
        setContentView(page)
    }

    private inner class TechAboutArtView(context: android.content.Context) : View(context) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val tracePath = android.graphics.Path()
        private val traces = arrayOf(
            floatArrayOf(8f, 34f, 65f, 34f, 65f, 52f, 108f, 52f),
            floatArrayOf(16f, 112f, 72f, 112f, 72f, 94f, 116f, 94f),
            floatArrayOf(245f, 38f, 300f, 38f, 300f, 58f, 350f, 58f),
            floatArrayOf(235f, 126f, 292f, 126f, 292f, 105f, 352f, 105f),
            floatArrayOf(130f, 12f, 130f, 30f, 158f, 30f),
            floatArrayOf(208f, 12f, 208f, 29f, 184f, 29f)
        )
        private val traceDots = arrayOf(
            65f to 34f, 108f to 52f, 72f to 112f, 116f to 94f,
            300f to 38f, 350f to 58f, 292f to 126f, 352f to 105f
        )

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val sx = width / 360f
            val sy = height / 190f
            canvas.save()
            canvas.scale(sx, sy)
            p.style = android.graphics.Paint.Style.STROKE
            p.strokeWidth = 1.8f
            p.strokeCap = android.graphics.Paint.Cap.ROUND
            p.color = Color.argb(100, 83, 174, 255)
            traces.forEach { pts ->
                tracePath.reset()
                tracePath.moveTo(pts[0], pts[1])
                var n = 2
                while (n < pts.size) {
                    tracePath.lineTo(pts[n], pts[n + 1])
                    n += 2
                }
                canvas.drawPath(tracePath, p)
            }
            p.style = android.graphics.Paint.Style.FILL
            p.color = Color.rgb(95, 185, 255)
            traceDots.forEach {
                canvas.drawCircle(it.first, it.second, 3.2f, p)
            }

            // Centralny układ scalony z delikatnym neonowym obrysem.
            p.color = Color.argb(40, 91, 173, 255)
            canvas.drawRoundRect(128f, 49f, 232f, 135f, 15f, 15f, p)
            p.style = android.graphics.Paint.Style.STROKE
            p.strokeWidth = 2.4f
            p.color = Color.argb(225, 116, 201, 255)
            canvas.drawRoundRect(128f, 49f, 232f, 135f, 15f, 15f, p)
            p.style = android.graphics.Paint.Style.FILL
            p.color = Color.rgb(220, 240, 255)
            p.textSize = 18f
            p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            canvas.drawText("ALL", 158f, 84f, p)
            p.textSize = 11f
            p.color = Color.rgb(119, 198, 255)
            canvas.drawText("IN ONE", 155f, 102f, p)
            p.color = Color.argb(180, 160, 215, 255)
            p.textSize = 8f
            p.typeface = Typeface.MONOSPACE
            canvas.drawText("0101 1010", 148f, 119f, p)
            p.typeface = Typeface.DEFAULT
            canvas.restore()
        }
    }

    private fun showMoreMenu(activeSection: String) {
        stopTimetableTicker()
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt(), (28 * density).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, pageGradient)
        }

        // Hero: bardziej przypomina centrum aplikacji niż zwykłą listę ustawień.
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(35, 83, 128), Color.rgb(61, 61, 116), Color.rgb(34, 48, 75))
            ).apply {
                cornerRadius = 25f * density
                setStroke((1 * density).toInt(), Color.rgb(92, 128, 184))
            }
            elevation = 3f * density
        }
        hero.addView(label("EDUFLOW  /  TWOJE CENTRUM", 10f, Color.rgb(190, 218, 255), true))
        hero.addView(label("Więcej możliwości.", 25f, Color.WHITE, true), params(0, 7, 0, 3))
        hero.addView(label("Szkoła, planowanie i codzienne sprawy — wszystko w jednym miejscu.", 12f, Color.rgb(222, 232, 248)), params(0, 0, 0, 14))
        val heroActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val profileButton = actionButton("👤  PROFILE") { showProfileSwitcher() }
        val notesButton = actionButton("✎  NOTATKI") { showPersonalNotes() }
        heroActions.addView(profileButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (6 * density).toInt() })
        heroActions.addView(notesButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = (6 * density).toInt() })
        hero.addView(heroActions)
        root.addView(hero, params(0, 0, 0, 20))

        root.addView(sectionHeader("SZKOŁA I NAUKA"), params(0, 0, 0, 7))
        addMoreModuleRow(root,
            "OCENY I POSTĘPY", "Oceny, średnie i wyniki", "📊", Color.rgb(67, 103, 177), { loadLibrusSection("OCENY") },
            "PLAN LEKCJI", "Dzień, tydzień i godziny", "⏰", Color.rgb(42, 119, 145), { loadLibrusSection("PLAN LEKCJI") })
        addMoreModuleRow(root,
            "ZADANIA DOMOWE", "Prace i terminy do wykonania", "📚", Color.rgb(103, 86, 158), { loadLibrusSection("ZADANIA DOMOWE") },
            "TERMINARZ", "Sprawdziany i wydarzenia", "🗓", Color.rgb(55, 112, 159), { loadLibrusSection("TERMINARZ") })
        addMoreModuleRow(root,
            "FREKWENCJA", "Miesięczny kalendarz obecności", "📅", Color.rgb(39, 126, 117), { loadLibrusSection("FREKWENCJA") },
            "SZCZĘŚLIWY NUMEREK", "Numer na dzisiejszy dzień", "🍀", Color.rgb(63, 130, 91), { loadLibrusSection("SZCZĘŚLIWY NUMEREK") })
        addMoreModuleRow(root,
            "WIADOMOŚCI", "Kontakt ze szkołą i nauczycielami", "✉️", Color.rgb(119, 78, 153), { loadLibrusSection("WIADOMOŚCI") },
            "KATALOG NAUCZYCIELI", "Osoby, przedmioty i kontakt", "👩‍🏫", Color.rgb(70, 104, 157), { loadLibrusSection("NAUCZYCIELE") })
        addMoreModuleRow(root,
            "DANE KONTA I SZKOŁY", "Uczeń, klasa i szkoła", "🏫", Color.rgb(47, 111, 157), { loadLibrusSection("PROFILE") },
            "UWAGI", "Informacje o zachowaniu", "📝", Color.rgb(153, 105, 57), { loadLibrusSection("UWAGI") })

        root.addView(sectionHeader("ORGANIZACJA NA CO DZIEŃ"), params(0, 17, 0, 7))
        addMoreModuleRow(root,
            "NOTATKI", "Własne zapiski i szablony", "✍️", Color.rgb(105, 83, 151), { showPersonalNotes() },
            "LISTA ZAKUPÓW", "Listy i odhaczanie produktów", "🛒", Color.rgb(45, 125, 104), { showShoppingList() })
        addMoreModuleRow(root,
            "KALENDARZ OSOBISTY", "Twoje wydarzenia i przypomnienia", "📆", Color.rgb(51, 112, 151), { loadLibrusSection("TERMINARZ") },
            "SZYBKI DOSTĘP", "Wróć do ekranu głównego", "⌂", Color.rgb(74, 113, 139), { showHome() })

        root.addView(sectionHeader("USTAWIENIA I APLIKACJA"), params(0, 17, 0, 7))
        addMoreModuleRow(root,
            "POWIADOMIENIA", "Alerty i harmonogram sprawdzania", "🔔", Color.rgb(153, 103, 62), { showNotificationSettings() },
            "DIAGNOSTYKA", "Sprawdź działanie powiadomień", "🩺", Color.rgb(56, 128, 135), { showNotificationDiagnostics() })
        addMoreModuleRow(root,
            "WYGLĄD APLIKACJI", "Motyw i kolory interfejsu", "🎨", Color.rgb(91, 92, 158), { showAppearanceSettings(activeSection) },
            "ZARZĄDZAJ PROFILAMI", "Dodaj lub przełącz konto", "👥", Color.rgb(74, 105, 151), { showProfileSwitcher() })
        addMoreModuleRow(root,
            "O AUTORZE", "Twórca i informacje o EduFlow", "✦", Color.rgb(76, 101, 130), { showAbout() },
            "PULPIT DNIA", "Najważniejsze informacje i szkolne skróty", "☀️", Color.rgb(43, 128, 111), { showHome() })

        root.addView(label("EduFlow  •  Twoja szkoła i codzienność w jednym miejscu", 10f, muted).apply {
            gravity = Gravity.CENTER
        }, params(0, 18, 0, 4))
        showPersonalToolPage(root, "WIĘCEJ")
    }

    private fun addMoreModuleRow(
        parent: LinearLayout,
        firstTitle: String, firstDescription: String, firstEmoji: String, firstAccent: Int, firstAction: () -> Unit,
        secondTitle: String, secondDescription: String, secondEmoji: String, secondAccent: Int, secondAction: () -> Unit
    ) {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        addMoreModuleTile(row, firstTitle, firstDescription, firstEmoji, firstAccent, firstAction,
            LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = (5 * density).toInt() })
        addMoreModuleTile(row, secondTitle, secondDescription, secondEmoji, secondAccent, secondAction,
            LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = (5 * density).toInt() })
        parent.addView(row, params(0, 0, 0, 10))
    }

    private fun addMoreModuleTile(
        parent: LinearLayout,
        title: String,
        description: String,
        emoji: String,
        accent: Int,
        action: () -> Unit,
        layoutParams: LinearLayout.LayoutParams
    ) {
        val density = resources.displayMetrics.density
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding((13 * density).toInt(), (14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                Color.rgb(Color.red(accent) * 2 / 5 + 18, Color.green(accent) * 2 / 5 + 18, Color.blue(accent) * 2 / 5 + 18),
                Color.rgb(25, 36, 55)
            )).apply {
                cornerRadius = 19f * density
                setStroke((1 * density).toInt(), Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent)))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            minimumHeight = (126 * density).toInt()
        }
        val icon = TextView(this).apply {
            text = emoji
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)))
                cornerRadius = 13f * density
            }
        }
        tile.addView(icon, LinearLayout.LayoutParams((42 * density).toInt(), (42 * density).toInt()))
        tile.addView(label(title, 11f, Color.WHITE, true), params(0, 10, 0, 3))
        tile.addView(label(description, 10f, Color.rgb(190, 205, 225)), params(0, 0, 0, 0))
        parent.addView(tile, layoutParams)
    }

    // ---------------------------------------------------------
    // PRYWATNE NARZĘDZIA — dane zapisywane lokalnie na urządzeniu
    // ---------------------------------------------------------

    private fun addPersonalToolCard(parent: LinearLayout, title: String, description: String, emoji: String, action: () -> Unit) {
        val density = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((18 * density).toInt(), (13 * density).toInt(), (16 * density).toInt(), (13 * density).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(44, 66, 88), Color.rgb(25, 37, 55))).apply {
                cornerRadius = 20f * density
                setStroke((1 * density).toInt(), Color.rgb(70, 96, 126))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text = emoji
            textSize = 29f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams((54 * density).toInt(), (54 * density).toInt()))
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), 0, 0, 0)
            addView(label(title, 14f, white, true))
            addView(label(description, 12f, Color.rgb(196, 211, 231)))
        }
        card.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(label("›", 28f, Color.rgb(170, 195, 224), true))
        parent.addView(card, params(0, 5, 0, 5))
    }

    private fun readLocalArray(key: String): JSONArray {
        return try { JSONArray(uiPrefs.getString(key, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
    }

    private fun saveLocalArray(key: String, array: JSONArray) {
        uiPrefs.edit().putString(key, array.toString()).apply()
    }

    private fun showPersonalNotes() {
        stopTimetableTicker()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 22)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, pageGradient)
        }
        root.addView(label("TWOJE NOTATKI", 11f, Color.rgb(180, 205, 239), true))
        root.addView(label("Zapisuj pomysły, informacje i sprawy do zapamiętania.", 13f, muted), params(0, 5, 0, 10))
        val search = EditText(this).apply {
            hint = "Szukaj w notatkach…"
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setPadding(15, 12, 15, 12)
            background = roundedBackground(panelLight, 13)
        }
        root.addView(search, params(0, 0, 0, 10))
        val noteActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        noteActions.addView(actionButton("+  DODAJ NOTATKĘ") { editPersonalNote(null) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = (6 * resources.displayMetrics.density).toInt() })
        noteActions.addView(actionButton("SZABLONY") { showNoteTemplates() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(noteActions, params(0, 0, 0, 12))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        fun redraw(query: String = search.text.toString()) {
            list.removeAllViews()
            val notes = readLocalArray("personal_notes")
            val needle = query.trim().lowercase(Locale.ROOT)
            val filtered = (0 until notes.length()).mapNotNull { notes.optJSONObject(it) }
                .filter {
                    needle.isBlank() ||
                        it.optString("title").lowercase(Locale.ROOT).contains(needle) ||
                        it.optString("body").lowercase(Locale.ROOT).contains(needle)
                }
                .sortedWith(compareByDescending<JSONObject> { it.optBoolean("pinned", false) }
                    .thenByDescending { it.optLong("updated", 0L) })
            if (filtered.isEmpty()) {
                list.addView(infoCard(if (needle.isBlank()) "Nie masz jeszcze notatek. Dodaj pierwszą, aby mieć ważne informacje pod ręką."
                    else "Nie znaleziono notatek pasujących do: $query"))
                return
            }
            filtered.forEach { note ->
                val noteId = note.optString("id")
                val title = note.optString("title", "Bez tytułu")
                val body = note.optString("body", "")
                val pinned = note.optBoolean("pinned", false)
                val noteCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 14, 16, 14)
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.rgb(37, 51, 72), Color.rgb(25, 35, 52))).apply {
                        cornerRadius = 17f * resources.displayMetrics.density
                        setStroke((1 * resources.displayMetrics.density).toInt(), Color.rgb(61, 82, 111))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { editPersonalNote(note) }
                    setOnLongClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle("Usuń notatkę?")
                            .setMessage(title).setNegativeButton("Anuluj", null)
                            .setPositiveButton("Usuń") { _, _ ->
                                val current = readLocalArray("personal_notes")
                                val updated = JSONArray()
                                for (index in 0 until current.length()) {
                                    val item = current.optJSONObject(index) ?: continue
                                    if (item.optString("id") != noteId) updated.put(item)
                                }
                                saveLocalArray("personal_notes", updated)
                                redraw()
                            }.show()
                        true
                    }
                }
                noteCard.addView(label((if (pinned) "📌  " else "") + title, 15f, white, true))
                if (body.isNotBlank()) noteCard.addView(label(body, 13f, Color.rgb(201, 214, 232)).apply { maxLines = 4 }, params(0, 6, 0, 0))
                noteCard.addView(actionButton(if (pinned) "ODEPNIJ NOTATKĘ" else "PRZYPNIJ NA GÓRZE") {
                    val current = readLocalArray("personal_notes")
                    val updated = JSONArray()
                    for (index in 0 until current.length()) {
                        val item = current.optJSONObject(index) ?: continue
                        if (item.optString("id") == noteId) item.put("pinned", !pinned)
                        updated.put(item)
                    }
                    saveLocalArray("personal_notes", updated)
                    redraw()
                }, params(0, 8, 0, 0))
                val updatedAt = note.optLong("updated", 0L)
                if (updatedAt > 0L) {
                    val date = java.text.SimpleDateFormat("dd.MM.yyyy  •  HH:mm", Locale("pl", "PL")).format(java.util.Date(updatedAt))
                    noteCard.addView(label("Ostatnia zmiana: $date", 10f, muted), params(0, 5, 0, 0))
                }
                noteCard.addView(label("Dotknij treści, aby edytować • przytrzymaj, aby usunąć", 10f, muted), params(0, 4, 0, 0))
                list.addView(noteCard, params(0, 5, 0, 5))
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { redraw(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        redraw("")
        showPersonalToolPage(root, "WIĘCEJ")
    }

    private fun showNoteTemplates() {
        val templates = arrayOf(
            "Do zrobienia" to "☐ Zadanie:\n☐ Termin:\n☐ Ważne informacje:",
            "Ważne informacje" to "Informacja:\n\nKontakt / numer:\nTermin:",
            "Pomysł" to "Pomysł:\n\nCo będzie potrzebne:\nPierwszy krok:",
            "Lista / plan" to "1. \n2. \n3. "
        )
        AlertDialog.Builder(this)
            .setTitle("Wybierz szablon notatki")
            .setItems(templates.map { it.first }.toTypedArray()) { _, which ->
                val template = templates[which]
                editPersonalNote(null, template.first, template.second)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun editPersonalNote(existing: JSONObject?, initialTitle: String = "", initialBody: String = "") {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 0)
        }
        val titleInput = EditText(this).apply {
            hint = "Tytuł notatki"
            setText(existing?.optString("title", "").orEmpty().ifBlank { initialTitle })
            setTextColor(white)
            setHintTextColor(muted)
            setSingleLine(true)
        }
        val bodyInput = EditText(this).apply {
            hint = "Treść notatki"
            setText(existing?.optString("body", "").orEmpty().ifBlank { initialBody })
            setTextColor(white)
            setHintTextColor(muted)
            minLines = 5
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        fields.addView(titleInput)
        fields.addView(bodyInput, params(0, 8, 0, 0))
        AlertDialog.Builder(this).setTitle(if (existing == null) "Nowa notatka" else "Edytuj notatkę")
            .setView(fields).setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz") { _, _ ->
                val title = titleInput.text.toString().trim()
                val body = bodyInput.text.toString().trim()
                if (title.isBlank() && body.isBlank()) {
                    Toast.makeText(this, "Wpisz tytuł lub treść notatki.", Toast.LENGTH_SHORT).show()
                } else {
                    val all = readLocalArray("personal_notes")
                    val id = existing?.optString("id")?.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
                    val updated = JSONArray()
                    for (i in 0 until all.length()) {
                        val item = all.optJSONObject(i) ?: continue
                        if (item.optString("id") != id) updated.put(item)
                    }
                    updated.put(JSONObject().put("id", id).put("title", title.ifBlank { "Bez tytułu" })
                        .put("body", body).put("pinned", existing?.optBoolean("pinned", false) ?: false)
                        .put("updated", System.currentTimeMillis()))
                    saveLocalArray("personal_notes", updated)
                    showPersonalNotes()
                }
            }.show()
    }

    private fun showShoppingList() {
        stopTimetableTicker()
        val lists = readLocalArray("shopping_lists")
        if (lists.length() == 0) {
            lists.put(JSONObject().put("id", "default").put("name", "Zakupy"))
            saveLocalArray("shopping_lists", lists)
        }
        val allItems = readLocalArray("shopping_items")
        val defaultListId = lists.optJSONObject(0)?.optString("id", "default") ?: "default"
        for (i in 0 until allItems.length()) {
            val item = allItems.optJSONObject(i) ?: continue
            if (item.optString("listId").isBlank()) item.put("listId", defaultListId)
            if (item.optString("category").isBlank()) item.put("category", "Pozostałe")
            if (item.optString("quantity").isBlank()) item.put("quantity", "1")
        }
        saveLocalArray("shopping_items", allItems)
        val activeListId = uiPrefs.getString("active_shopping_list", defaultListId) ?: defaultListId
        val activeList = (0 until lists.length()).mapNotNull { lists.optJSONObject(it) }
            .firstOrNull { it.optString("id") == activeListId } ?: lists.optJSONObject(0) ?: JSONObject().put("id", defaultListId).put("name", "Zakupy")
        val activeId = activeList.optString("id", defaultListId)
        val categories = listOf("Wszystkie", "Warzywa i owoce", "Nabiał", "Pieczywo", "Mięso i ryby", "Napoje", "Chemia", "Pozostałe")
        var categoryFilter = "Wszystkie"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 22)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, pageGradient)
        }
        root.addView(label("LISTA ZAKUPÓW", 11f, Color.rgb(180, 205, 239), true))
        val listTitleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        listTitleRow.addView(label(activeList.optString("name", "Zakupy"), 23f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        listTitleRow.addView(actionButton("Zmień listę") { showShoppingLists(activeId) })
        root.addView(listTitleRow, params(0, 4, 0, 2))
        val summary = label("", 13f, muted)
        root.addView(summary, params(0, 2, 0, 10))
        root.addView(actionButton("+  DODAJ PRODUKT") { addShoppingItem(activeId, null) }, params(0, 0, 0, 8))
        root.addView(label("SZYBKIE DODAWANIE", 10f, Color.rgb(180, 205, 239), true), params(0, 2, 0, 5))
        val frequentNames = listOf(
            "Chleb" to "Pieczywo", "Marchew" to "Warzywa i owoce", "Jajka" to "Nabiał",
            "Mleko" to "Nabiał", "Masło" to "Nabiał", "Ziemniaki" to "Warzywa i owoce",
            "Banany" to "Warzywa i owoce", "Woda" to "Napoje"
        )
        val savedFrequent = readFrequentShoppingProducts()
        val quickItems = (frequentNames + savedFrequent).distinctBy { it.first.lowercase(Locale.ROOT) }
        val quickScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quickItems.forEach { (productName, productCategory) ->
            quickRow.addView(actionButton("+ $productName") {
                addFrequentShoppingItem(activeId, productName, productCategory)
            }.apply {
                textSize = 11f
                minHeight = (38 * resources.displayMetrics.density).toInt()
                setPadding(10, 0, 10, 0)
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * resources.displayMetrics.density).toInt() })
        }
        quickScroll.addView(quickRow)
        root.addView(quickScroll, params(0, 0, 0, 10))
        val filterScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        categories.forEach { category ->
            filterRow.addView(actionButton(category) {
                categoryFilter = category
                redrawShoppingItems(categoryFilter, activeId, summary, list)
            }.apply {
                textSize = 10f
                minHeight = (38 * resources.displayMetrics.density).toInt()
                setPadding(8, 0, 8, 0)
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * resources.displayMetrics.density).toInt() })
        }
        filterScroll.addView(filterRow)
        root.addView(filterScroll, params(0, 0, 0, 10))
        root.addView(list)
        redrawShoppingItems(categoryFilter, activeId, summary, list)
        showPersonalToolPage(root, "WIĘCEJ")
    }

    private fun readFrequentShoppingProducts(): List<Pair<String, String>> {
        val raw = uiPrefs.getString("frequent_shopping_products_v1", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").trim()
                val category = item.optString("category", "Pozostałe")
                if (name.isBlank()) null else name to category
            }
        }.getOrDefault(emptyList())
    }

    private fun saveFrequentShoppingProduct(name: String, category: String) {
        val items = (readFrequentShoppingProducts() + (name.trim() to category))
            .distinctBy { it.first.lowercase(Locale.ROOT) }
            .take(30)
        val array = JSONArray()
        items.forEach { (productName, productCategory) ->
            array.put(JSONObject().put("name", productName).put("category", productCategory))
        }
        uiPrefs.edit().putString("frequent_shopping_products_v1", array.toString()).apply()
    }

    private fun addFrequentShoppingItem(listId: String, name: String, category: String) {
        val all = readLocalArray("shopping_items")
        val duplicate = (0 until all.length()).mapNotNull { all.optJSONObject(it) }
            .firstOrNull { it.optString("listId") == listId && !it.optBoolean("done", false) &&
                it.optString("name").trim().equals(name, ignoreCase = true) }
        if (duplicate != null) {
            Toast.makeText(this, "$name jest już na tej liście.", Toast.LENGTH_SHORT).show()
            return
        }
        all.put(JSONObject().put("id", java.util.UUID.randomUUID().toString())
            .put("listId", listId).put("name", name).put("quantity", "1")
            .put("category", category).put("done", false))
        saveLocalArray("shopping_items", all)
        uiPrefs.edit().putString("active_shopping_list", listId).apply()
        showShoppingList()
    }

    private fun redrawShoppingItems(categoryFilter: String, activeId: String, summary: TextView, list: LinearLayout) {
        list.removeAllViews()
        val current = readLocalArray("shopping_items")
        val scoped = (0 until current.length()).mapNotNull { current.optJSONObject(it) }.filter { it.optString("listId") == activeId }
        val remaining = scoped.count { !it.optBoolean("done", false) }
        summary.text = getString(R.string.shopping_summary, remaining, scoped.size - remaining, scoped.size)
        val visible = scoped.filter { categoryFilter == "Wszystkie" || it.optString("category", "Pozostałe") == categoryFilter }
        if (visible.isEmpty()) {
            list.addView(infoCard(if (scoped.isEmpty()) "Ta lista jest pusta. Dodaj pierwszy produkt." else "Brak produktów w kategorii: " + categoryFilter))
            return
        }
        visible.sortedWith(compareBy<JSONObject> { it.optBoolean("done", false) }.thenBy { it.optString("category") }
            .thenBy { it.optString("name").lowercase(Locale("pl", "PL")) }).forEach { item ->
            val id = item.optString("id")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(10, 7, 8, 7)
                background = roundedBackground(Color.rgb(29, 41, 59), 14)
            }
            val check = CheckBox(this).apply {
                text = item.optString("name", "")
                isChecked = item.optBoolean("done", false)
                setTextColor(if (isChecked) muted else white)
                textSize = 14f
                setOnCheckedChangeListener { _, checked ->
                    val fresh = readLocalArray("shopping_items")
                    for (j in 0 until fresh.length()) {
                        val candidate = fresh.optJSONObject(j) ?: continue
                        if (candidate.optString("id") == id) candidate.put("done", checked)
                    }
                    saveLocalArray("shopping_items", fresh)
                    redrawShoppingItems(categoryFilter, activeId, summary, list)
                }
            }
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(label(item.optString("quantity", "1"), 12f, accentColor, true))
            details.addView(label(item.optString("category", "Pozostałe"), 10f, muted))
            row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(details)
            row.setOnClickListener { addShoppingItem(activeId, item) }
            row.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Usunąć produkt?").setMessage(item.optString("name"))
                    .setNegativeButton("Anuluj", null).setPositiveButton("Usuń") { _, _ ->
                        val fresh = readLocalArray("shopping_items")
                        val kept = JSONArray()
                        for (j in 0 until fresh.length()) {
                            val candidate = fresh.optJSONObject(j) ?: continue
                            if (candidate.optString("id") != id) kept.put(candidate)
                        }
                        saveLocalArray("shopping_items", kept)
                        redrawShoppingItems(categoryFilter, activeId, summary, list)
                    }.show()
                true
            }
            list.addView(row, params(0, 3, 0, 3))
        }
        if (scoped.any { it.optBoolean("done", false) }) {
            list.addView(actionButton("USUŃ KUPIONE Z TEJ LISTY") {
                val fresh = readLocalArray("shopping_items")
                val kept = JSONArray()
                for (j in 0 until fresh.length()) {
                    val item = fresh.optJSONObject(j) ?: continue
                    if (item.optString("listId") != activeId || !item.optBoolean("done", false)) kept.put(item)
                }
                saveLocalArray("shopping_items", kept)
                redrawShoppingItems(categoryFilter, activeId, summary, list)
            }, params(0, 10, 0, 0))
        }
    }

    private fun showShoppingLists(currentId: String) {
        val lists = readLocalArray("shopping_lists")
        val names = (0 until lists.length()).mapNotNull { lists.optJSONObject(it) }
        AlertDialog.Builder(this).setTitle("Wybierz listę")
            .setItems(names.map { it.optString("name", "Lista") }.toTypedArray()) { _, which ->
                uiPrefs.edit().putString("active_shopping_list", names[which].optString("id")).apply()
                showShoppingList()
            }
            .setPositiveButton("Nowa lista") { _, _ ->
                val input = EditText(this).apply { hint = "Nazwa listy"; setTextColor(white); setHintTextColor(muted) }
                AlertDialog.Builder(this).setTitle("Nowa lista zakupów").setView(input).setNegativeButton("Anuluj", null)
                    .setPositiveButton("Utwórz") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotBlank()) {
                            val updated = readLocalArray("shopping_lists")
                            val id = System.currentTimeMillis().toString()
                            updated.put(JSONObject().put("id", id).put("name", name))
                            saveLocalArray("shopping_lists", updated)
                            uiPrefs.edit().putString("active_shopping_list", id).apply()
                            showShoppingList()
                        }
                    }.show()
            }
            .setNeutralButton("Zmień nazwę") { _, _ ->
                val selected = names.firstOrNull { it.optString("id") == currentId } ?: return@setNeutralButton
                val input = EditText(this).apply { setText(selected.optString("name")); setTextColor(white) }
                AlertDialog.Builder(this).setTitle("Zmień nazwę listy").setView(input).setNegativeButton("Anuluj", null)
                    .setPositiveButton("Zapisz") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotBlank()) {
                            selected.put("name", name)
                            saveLocalArray("shopping_lists", lists)
                            showShoppingList()
                        }
                    }.show()
            }.show()
    }

    private fun addShoppingItem(listId: String, existing: JSONObject?) {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 4, 8, 0) }
        val name = EditText(this).apply {
            hint = "Nazwa produktu"; setText(existing?.optString("name", "").orEmpty())
            setTextColor(white); setHintTextColor(muted); setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val quantity = EditText(this).apply {
            hint = "Ilość (np. 2, 1 kg, 500 g)"; setText(existing?.optString("quantity", "1") ?: "1")
            setTextColor(white); setHintTextColor(muted); setSingleLine(true)
        }
        val categoryNames = arrayOf("Warzywa i owoce", "Nabiał", "Pieczywo", "Mięso i ryby", "Napoje", "Chemia", "Pozostałe")
        var category = existing?.optString("category", "Pozostałe") ?: "Pozostałe"
        val rememberFrequent = CheckBox(this).apply {
            text = "Zapamiętaj jako często kupowany produkt"
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(accentColor)
            isChecked = readFrequentShoppingProducts().any { it.first.equals(existing?.optString("name", ""), true) }
            isEnabled = existing == null
        }
        lateinit var categoryButton: Button
        categoryButton = actionButton("Kategoria: " + category) {
            AlertDialog.Builder(this).setTitle("Kategoria")
                .setSingleChoiceItems(categoryNames, categoryNames.indexOf(category).coerceAtLeast(0)) { dialog, which ->
                    category = categoryNames[which]
                    categoryButton.text = getString(R.string.shopping_category, category)
                    dialog.dismiss()
                }.show()
        }
        fields.addView(name)
        fields.addView(quantity, params(0, 6, 0, 6))
        fields.addView(categoryButton, params(0, 6, 0, 0))
        if (existing == null) fields.addView(rememberFrequent, params(0, 6, 0, 0))
        AlertDialog.Builder(this).setTitle(if (existing == null) "Dodaj produkt" else "Edytuj produkt").setView(fields)
            .setNegativeButton("Anuluj", null).setPositiveButton("Zapisz") { _, _ ->
                val productName = name.text.toString().trim()
                if (productName.isBlank()) Toast.makeText(this, "Wpisz nazwę produktu.", Toast.LENGTH_SHORT).show()
                else {
                    val all = readLocalArray("shopping_items")
                    val id = existing?.optString("id")?.takeIf { it.isNotBlank() } ?: System.currentTimeMillis().toString()
                    val updated = JSONArray()
                    for (i in 0 until all.length()) {
                        val item = all.optJSONObject(i) ?: continue
                        if (item.optString("id") != id) updated.put(item)
                    }
                    updated.put(JSONObject().put("id", id).put("listId", listId).put("name", productName)
                        .put("quantity", quantity.text.toString().trim().ifBlank { "1" }).put("category", category)
                        .put("done", existing?.optBoolean("done", false) ?: false))
                    saveLocalArray("shopping_items", updated)
                    if (existing == null && rememberFrequent.isChecked) saveFrequentShoppingProduct(productName, category)
                    uiPrefs.edit().putString("active_shopping_list", listId).apply()
                    showShoppingList()
                }
            }.show()
    }

    private fun showPersonalToolPage(root: LinearLayout, active: String) {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(root)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(buildBottomNavigation(active))
        setContentView(page)
    }

    // ---------------------------------------------------------
    // KOMPONENTY UI
    // ---------------------------------------------------------

    private fun addTodayPanel(parent: LinearLayout) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Calendar.getInstance().time)
        val todayLabel = SimpleDateFormat("EEEE, d MMMM", Locale("pl", "PL"))
            .format(Calendar.getInstance().time).replaceFirstChar { it.uppercase(Locale("pl", "PL")) }
        val events = readLocalArray("personal_events")
        val upcoming = (0 until events.length()).mapNotNull { events.optJSONObject(it) }
            .filter { it.optString("date") >= today }
            .sortedBy { it.optString("date") }
            .take(3)
        val panel = card()
        panel.addView(label("DZISIAJ", 11f, accentColor, true))
        panel.addView(label(todayLabel, 20f, white, true))
        val todayEvents = upcoming.filter { it.optString("date") == today }
        if (todayEvents.isEmpty()) {
            panel.addView(label("Nie masz zapisanych prywatnych wydarzeń na dziś.", 13f, muted))
        } else {
            todayEvents.forEach { event ->
                panel.addView(label("• " + event.optString("title"), 14f, white, true))
                if (event.optString("description").isNotBlank()) {
                    panel.addView(label(event.optString("description"), 11f, muted))
                }
            }
        }
        val later = upcoming.filter { it.optString("date") > today }
        if (later.isNotEmpty()) {
            panel.addView(label("NAJBLIŻEJ", 10f, muted, true), params(0, 8, 0, 2))
            later.forEach { event ->
                panel.addView(label(dateLabel(event.optString("date")) + "  ·  " + event.optString("title"), 12f, Color.rgb(205, 218, 237)))
            }
        }
        panel.addView(actionButton("OTWÓRZ TERMINARZ") { loadLibrusSection("TERMINARZ") }, params(0, 8, 0, 0))
        parent.addView(panel, params(0, 0, 0, 14))
    }

    private fun addHomeSection(
        parent: LinearLayout,
        heading: String,
        description: String,
        emoji: String
    ) {
        val density = resources.displayMetrics.density
        val palette = when (heading.uppercase(Locale("pl", "PL"))) {
            "PLAN LEKCJI" -> intArrayOf(Color.rgb(29, 83, 112), Color.rgb(24, 43, 68))
            "OCENY" -> intArrayOf(Color.rgb(48, 76, 130), Color.rgb(29, 43, 72))
            "WIADOMOŚCI" -> intArrayOf(Color.rgb(83, 62, 112), Color.rgb(39, 39, 70))
            "FREKWENCJA" -> intArrayOf(Color.rgb(42, 91, 91), Color.rgb(25, 51, 65))
            "ZADANIA DOMOWE" -> intArrayOf(Color.rgb(105, 77, 48), Color.rgb(55, 44, 39))
            "TERMINARZ" -> intArrayOf(Color.rgb(91, 67, 105), Color.rgb(44, 39, 67))
            "UWAGI" -> intArrayOf(Color.rgb(105, 67, 68), Color.rgb(55, 39, 52))
            "SZCZĘŚLIWY NUMEREK" -> intArrayOf(Color.rgb(67, 91, 57), Color.rgb(34, 52, 43))
            else -> intArrayOf(Color.rgb(45, 70, 100), Color.rgb(25, 38, 59))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((18 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            minimumHeight = (104 * density).toInt()
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, palette).apply {
                cornerRadius = 22f * density
                setStroke((1 * density).toInt(), Color.argb(105, 145, 178, 216))
            }
            isClickable = true
            isFocusable = true
            foreground = android.util.TypedValue().let {
                val out = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                getDrawable(out.resourceId)
            }
            elevation = 3f * density
            clipToPadding = false
            clipChildren = false
        }

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
        }
        texts.addView(label(if (heading == "PROFILE") "Dane konta i szkoły" else heading, 15f, white, true))
        texts.addView(label(description, 12f, Color.rgb(205, 218, 237)).apply {
            setLineSpacing(1f, 1.04f)
            maxLines = 2
        })
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        // Własna ilustracja wektorowa — ostre kształty, bez emoji i bez pobierania grafik z sieci.
        val illustration = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(62, 255, 255, 255), Color.argb(20, 255, 255, 255))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke((1 * density).toInt(), Color.argb(70, 220, 235, 255))
            }
            clipChildren = false
            clipToPadding = false
        }
        illustration.addView(ThematicBannerArtView(this, heading),
            FrameLayout.LayoutParams((76 * density).toInt(), (76 * density).toInt(), Gravity.CENTER))
        if (uiPrefs.getString("accent", "blue") in setOf("red", "christmas", "halloween", "pink", "spring")) {
            illustration.addView(SeasonalBannerOverlayView(this),
                FrameLayout.LayoutParams((76 * density).toInt(), (76 * density).toInt(), Gravity.CENTER))
        }
        card.addView(illustration, LinearLayout.LayoutParams(
            (76 * density).toInt(), (76 * density).toInt()
        ).apply { marginStart = (8 * density).toInt() })

        card.setOnClickListener { loadLibrusSection(heading) }
        card.alpha = 0f
        card.translationY = 12f * density
        parent.addView(card, params(0, 7, 0, 7))
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(parent.childCount.coerceAtMost(12) * 35L)
            .setDuration(320)
            .start()
    }


    // Lekka, przezroczysta warstwa sezonowych ilustracji nakładana na banery modułów.
    // Rysowana lokalnie na Canvas — bez bitmap, emoji i zasobów pobieranych z internetu.
    private inner class SeasonalBannerOverlayView(context: android.content.Context) : View(context) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val shapePath = android.graphics.Path()

        private fun fill(canvas: android.graphics.Canvas, c: Int) {
            p.color = c
            p.style = android.graphics.Paint.Style.FILL
        }

        private fun stroke(canvas: android.graphics.Canvas, c: Int, w: Float = 3f) {
            p.color = c
            p.style = android.graphics.Paint.Style.STROKE
            p.strokeWidth = w
            p.strokeCap = android.graphics.Paint.Cap.ROUND
            p.strokeJoin = android.graphics.Paint.Join.ROUND
        }

        private fun snowflake(canvas: android.graphics.Canvas, x: Float, y: Float, r: Float, c: Int) {
            stroke(canvas, c, 2.4f)
            for (a in 0..2) {
                val angle = Math.PI * a / 3.0
                val dx = kotlin.math.cos(angle).toFloat() * r
                val dy = kotlin.math.sin(angle).toFloat() * r
                canvas.drawLine(x-dx, y-dy, x+dx, y+dy, p)
                for (side in listOf(-1f, 1f)) {
                    val ex = x + dx * side
                    val ey = y + dy * side
                    val bx = x + dx * side * 0.58f
                    val by = y + dy * side * 0.58f
                    val wing = r * 0.24f
                    canvas.drawLine(ex, ey, bx - dy/r*wing, by + dx/r*wing, p)
                    canvas.drawLine(ex, ey, bx + dy/r*wing, by - dx/r*wing, p)
                }
            }
        }

        private fun heart(canvas: android.graphics.Canvas, x: Float, y: Float, size: Float, c: Int) {
            fill(canvas, c)
            shapePath.reset()
            shapePath.moveTo(x, y + size * 0.42f)
            shapePath.cubicTo(x-size*0.95f, y-size*0.18f, x-size*0.62f, y-size*0.82f, x, y-size*0.28f)
            shapePath.cubicTo(x+size*0.62f, y-size*0.82f, x+size*0.95f, y-size*0.18f, x, y+size*0.42f)
            shapePath.close()
            canvas.drawPath(shapePath, p)
        }

        private fun flower(canvas: android.graphics.Canvas, x: Float, y: Float, r: Float, petal: Int, center: Int) {
            fill(canvas, petal)
            for (a in 0..4) {
                val angle = Math.PI * 2.0 * a / 5.0
                val px = x + kotlin.math.cos(angle).toFloat() * r * 0.58f
                val py = y + kotlin.math.sin(angle).toFloat() * r * 0.58f
                canvas.drawOval(px-r*0.34f, py-r*0.48f, px+r*0.34f, py+r*0.48f, p)
            }
            fill(canvas, center)
            canvas.drawCircle(x, y, r*0.25f, p)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val save = canvas.save()
            canvas.scale(width / 100f, height / 100f)
            when (uiPrefs.getString("accent", "blue")) {
                "red" -> {
                    // Mikołajki: czapka Mikołaja i śnieżynki.
                    fill(canvas, Color.rgb(225, 66, 78))
                    shapePath.reset()
                    shapePath.moveTo(69f, 27f); shapePath.quadTo(76f, 9f, 91f, 14f)
                    shapePath.lineTo(84f, 30f); shapePath.close()
                    canvas.drawPath(shapePath, p)
                    fill(canvas, Color.rgb(250, 248, 242))
                    canvas.drawRoundRect(67f, 25f, 88f, 31f, 3f, 3f, p)
                    canvas.drawCircle(92f, 13f, 4f, p)
                    snowflake(canvas, 13f, 19f, 6f, Color.argb(220, 225, 244, 255))
                    snowflake(canvas, 89f, 84f, 5f, Color.argb(210, 225, 244, 255))
                }
                "christmas" -> {
                    // Boże Narodzenie: bombki, gwiazdka i drobne światełka.
                    fill(canvas, Color.rgb(218, 73, 79)); canvas.drawCircle(87f, 25f, 7f, p)
                    stroke(canvas, Color.rgb(250, 218, 157), 2f); canvas.drawLine(87f, 15f, 87f, 18f, p)
                    fill(canvas, Color.rgb(244, 193, 92)); canvas.drawCircle(15f, 79f, 5f, p)
                    fill(canvas, Color.rgb(106, 198, 145)); canvas.drawCircle(87f, 82f, 4f, p)
                    fill(canvas, Color.rgb(255, 224, 150))
                    shapePath.reset()
                    for (i in 0..9) {
                        val a = -Math.PI/2 + i*Math.PI/5
                        val r = if (i % 2 == 0) 7.0 else 3.0
                        val px = 13 + kotlin.math.cos(a)*r
                        val py = 22 + kotlin.math.sin(a)*r
                        if (i == 0) shapePath.moveTo(px.toFloat(), py.toFloat())
                        else shapePath.lineTo(px.toFloat(), py.toFloat())
                    }
                    shapePath.close()
                    canvas.drawPath(shapePath, p)
                }
                "halloween" -> {
                    // Halloween: nietoperz i mała dynia.
                    fill(canvas, Color.rgb(111, 75, 132))
                    shapePath.reset()
                    shapePath.moveTo(10f, 24f); shapePath.lineTo(17f, 17f); shapePath.lineTo(23f, 24f); shapePath.lineTo(29f, 17f)
                    shapePath.lineTo(35f, 24f); shapePath.lineTo(29f, 29f); shapePath.lineTo(23f, 26f); shapePath.lineTo(17f, 29f); shapePath.close()
                    canvas.drawPath(shapePath, p)
                    fill(canvas, Color.rgb(239, 135, 49))
                    canvas.drawOval(70f, 70f, 94f, 91f, p)
                    stroke(canvas, Color.rgb(105, 73, 47), 2f)
                    canvas.drawLine(82f, 71f, 83f, 66f, p)
                    fill(canvas, Color.rgb(45, 35, 39))
                    canvas.drawCircle(77f, 79f, 2f, p); canvas.drawCircle(87f, 79f, 2f, p)
                    shapePath.reset()
                    shapePath.moveTo(77f, 85f); shapePath.quadTo(82f, 89f, 88f, 84f)
                    stroke(canvas, Color.rgb(45, 35, 39), 1.8f); canvas.drawPath(shapePath, p)
                }
                "pink" -> {
                    // Walentynki: serca w dwóch rozmiarach, bez przesadnego zagęszczenia.
                    heart(canvas, 15f, 20f, 8f, Color.rgb(248, 139, 190))
                    heart(canvas, 87f, 78f, 7f, Color.rgb(255, 188, 214))
                    heart(canvas, 88f, 18f, 4f, Color.rgb(225, 105, 165))
                }
                "spring" -> {
                    // Wiosna: kwiaty, listki i lekki akcent motyla.
                    flower(canvas, 14f, 19f, 7f, Color.rgb(255, 190, 207), Color.rgb(255, 220, 125))
                    flower(canvas, 87f, 82f, 6f, Color.rgb(255, 231, 145), Color.rgb(239, 153, 103))
                    fill(canvas, Color.rgb(139, 211, 143))
                    shapePath.reset()
                    shapePath.moveTo(83f, 17f); shapePath.quadTo(96f, 10f, 94f, 25f); shapePath.quadTo(85f, 27f, 83f, 17f); shapePath.close()
                    canvas.drawPath(shapePath, p)
                    stroke(canvas, Color.rgb(87, 159, 104), 1.5f); canvas.drawLine(84f, 22f, 92f, 16f, p)
                }
            }
            canvas.restoreToCount(save)
        }
    }

    private inner class ThematicBannerArtView(
        context: android.content.Context,
        private val section: String
    ) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val shapePath = android.graphics.Path()

        private fun color(value: Int) { paint.color = value; paint.style = android.graphics.Paint.Style.FILL }
        private fun line(value: Int, width: Float = 5f) {
            paint.color = value
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = width
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.strokeJoin = android.graphics.Paint.Join.ROUND
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val save = canvas.save()
            canvas.scale(width / 100f, height / 100f)
            val whiteSoft = Color.argb(235, 239, 247, 255)
            val accent = Color.rgb(145, 211, 255)
            val warm = Color.rgb(255, 211, 139)
            val green = Color.rgb(155, 226, 190)
            val navy = Color.argb(210, 15, 35, 57)

            when (section.uppercase(Locale("pl", "PL"))) {
                "PROFILE" -> {
                    // Budynek szkoły, wejście i okna.
                    color(whiteSoft); canvas.drawRoundRect(17f, 39f, 83f, 83f, 6f, 6f, paint)
                    color(accent); canvas.drawRect(12f, 34f, 88f, 42f, paint)
                    color(warm)
                    shapePath.reset()
                    shapePath.moveTo(10f, 36f); shapePath.lineTo(50f, 10f); shapePath.lineTo(90f, 36f); shapePath.close()
                    canvas.drawPath(shapePath, paint)
                    color(navy); canvas.drawRoundRect(42f, 58f, 58f, 83f, 3f, 3f, paint)
                    color(accent)
                    for (x in listOf(25f, 64f)) for (y in listOf(49f, 66f)) canvas.drawRoundRect(x, y, x+10f, y+9f, 2f, 2f, paint)
                    line(warm, 3f); canvas.drawLine(50f, 10f, 50f, 3f, paint)
                    color(warm); canvas.drawCircle(50f, 3f, 3f, paint)
                }
                "PLAN LEKCJI" -> {
                    color(whiteSoft); canvas.drawRoundRect(22f, 13f, 78f, 87f, 9f, 9f, paint)
                    color(accent); canvas.drawRoundRect(22f, 13f, 78f, 31f, 8f, 8f, paint)
                    color(warm); canvas.drawRoundRect(34f, 8f, 40f, 23f, 3f, 3f, paint); canvas.drawRoundRect(60f, 8f, 66f, 23f, 3f, 3f, paint)
                    line(Color.rgb(65, 106, 143), 3f)
                    for (x in listOf(36f, 50f, 64f)) for (y in listOf(43f, 57f, 71f)) canvas.drawRoundRect(x-4f, y-4f, x+4f, y+4f, 2f, 2f, paint)
                    color(green); canvas.drawCircle(68f, 70f, 12f, paint)
                    line(navy, 3f); canvas.drawLine(68f, 70f, 68f, 62f, paint); canvas.drawLine(68f, 70f, 74f, 73f, paint)
                }
                "OCENY" -> {
                    color(whiteSoft)
                    shapePath.reset()
                    shapePath.moveTo(12f, 29f); shapePath.quadTo(32f, 20f, 49f, 34f); shapePath.lineTo(49f, 83f); shapePath.quadTo(29f, 69f, 12f, 78f); shapePath.close()
                    canvas.drawPath(shapePath, paint)
                    shapePath.reset()
                    shapePath.moveTo(51f, 34f); shapePath.quadTo(70f, 20f, 88f, 29f); shapePath.lineTo(88f, 78f); shapePath.quadTo(68f, 69f, 51f, 83f); shapePath.close()
                    canvas.drawPath(shapePath, paint)
                    line(accent, 3f); canvas.drawLine(50f, 35f, 50f, 83f, paint)
                    color(warm); canvas.drawCircle(69f, 47f, 13f, paint)
                    shapePath.reset()
                    for(i in 0..9){ val a=(-Math.PI/2+i*Math.PI/5); val r=if(i%2==0)8.0 else 3.5; val x=69+Math.cos(a)*r; val y=47+Math.sin(a)*r; if(i==0)shapePath.moveTo(x.toFloat(),y.toFloat()) else shapePath.lineTo(x.toFloat(),y.toFloat()) }
                    shapePath.close()
                    color(navy); canvas.drawPath(shapePath, paint)
                    line(Color.rgb(74, 115, 151), 2.5f); canvas.drawLine(21f, 48f, 39f, 48f, paint); canvas.drawLine(21f, 58f, 39f, 58f, paint)
                }
                "WIADOMOŚCI" -> {
                    color(whiteSoft); canvas.drawRoundRect(11f, 26f, 89f, 76f, 9f, 9f, paint)
                    color(accent)
                    shapePath.reset()
                    shapePath.moveTo(13f,29f); shapePath.lineTo(50f,58f); shapePath.lineTo(87f,29f); shapePath.close()
                    canvas.drawPath(shapePath, paint)
                    line(Color.rgb(79, 115, 157), 4f); canvas.drawLine(14f,72f,39f,51f,paint); canvas.drawLine(86f,72f,61f,51f,paint)
                    color(warm); canvas.drawCircle(77f, 19f, 10f, paint)
                    color(navy); canvas.drawCircle(74f,17f,1.7f,paint); canvas.drawCircle(80f,17f,1.7f,paint)
                    line(navy,2f); canvas.drawArc(74f,17f,81f,24f,10f,150f,false,paint)
                }
                "FREKWENCJA" -> {
                    color(whiteSoft); canvas.drawRoundRect(18f, 18f, 82f, 84f, 8f, 8f, paint)
                    color(accent); canvas.drawRoundRect(18f, 18f, 82f, 36f, 7f, 7f, paint)
                    color(warm); canvas.drawRoundRect(29f, 11f, 36f, 26f, 3f, 3f, paint); canvas.drawRoundRect(64f, 11f, 71f, 26f, 3f, 3f, paint)
                    color(green); canvas.drawCircle(51f, 59f, 19f, paint)
                    line(navy, 5f); canvas.drawLine(41f,59f,48f,66f,paint); canvas.drawLine(48f,66f,63f,49f,paint)
                }
                "ZADANIA DOMOWE" -> {
                    color(whiteSoft); canvas.drawRoundRect(23f, 14f, 76f, 86f, 6f, 6f, paint)
                    color(accent); canvas.drawRoundRect(36f, 9f, 64f, 22f, 5f, 5f, paint)
                    line(Color.rgb(80, 119, 156), 3f)
                    for(y in listOf(39f,54f,69f)){ canvas.drawRoundRect(33f,y-4f,41f,y+4f,2f,2f,paint); canvas.drawLine(48f,y,67f,y,paint) }
                    color(warm); canvas.drawCircle(70f,70f,13f,paint)
                    line(navy,3f); canvas.drawLine(64f,76f,76f,64f,paint)
                }
                "TERMINARZ" -> {
                    color(whiteSoft); canvas.drawRoundRect(17f, 20f, 83f, 82f, 8f, 8f, paint)
                    color(accent); canvas.drawRoundRect(17f,20f,83f,38f,7f,7f,paint)
                    color(warm); canvas.drawCircle(68f,68f,17f,paint)
                    line(navy,3.5f); canvas.drawLine(68f,68f,68f,57f,paint); canvas.drawLine(68f,68f,77f,73f,paint)
                    line(Color.rgb(71,111,145),3f); canvas.drawLine(30f,49f,45f,49f,paint); canvas.drawLine(30f,60f,45f,60f,paint)
                }
                "UWAGI" -> {
                    color(whiteSoft); canvas.drawRoundRect(24f,13f,77f,86f,6f,6f,paint)
                    color(accent); canvas.drawRect(35f,25f,67f,31f,paint)
                    line(Color.rgb(75,111,148),3f); canvas.drawLine(35f,43f,66f,43f,paint); canvas.drawLine(35f,54f,66f,54f,paint); canvas.drawLine(35f,65f,55f,65f,paint)
                    color(warm); shapePath.reset()
                    shapePath.moveTo(66f,72f); shapePath.lineTo(81f,57f); shapePath.lineTo(88f,64f); shapePath.lineTo(73f,79f); shapePath.close()
                    canvas.drawPath(shapePath,paint)
                }
                "SZCZĘŚLIWY NUMEREK" -> {
                    color(warm); canvas.drawCircle(50f,50f,29f,paint)
                    color(green)
                    for(a in listOf(0.0, Math.PI/2, Math.PI, Math.PI*1.5)) {
                        val x=50+Math.cos(a)*13; val y=50+Math.sin(a)*13
                        canvas.drawOval((x-9).toFloat(),(y-13).toFloat(),(x+9).toFloat(),(y+13).toFloat(),paint)
                    }
                    color(navy); canvas.drawCircle(50f,50f,5f,paint)
                    line(whiteSoft,3f); canvas.drawLine(50f,55f,50f,75f,paint)
                }
                else -> {
                    color(whiteSoft); canvas.drawRoundRect(18f,18f,82f,82f,10f,10f,paint)
                    color(accent); canvas.drawCircle(50f,42f,14f,paint)
                    color(warm); canvas.drawRoundRect(31f,62f,69f,72f,5f,5f,paint)
                }
            }
            canvas.restoreToCount(save)
        }
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
        if (GradeAverageParser.parse(normalized) == null) return white
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

    private fun cleanGradeDetail(value: String, vararg prefixes: String): String {
        var result = value.trim()
        var changed: Boolean
        do {
            changed = false
            for (prefix in prefixes) {
                val pattern = Regex("^\\s*${Regex.escape(prefix)}\\s*:\\s*", RegexOption.IGNORE_CASE)
                val cleaned = result.replaceFirst(pattern, "")
                if (cleaned != result) {
                    result = cleaned.trim()
                    changed = true
                }
            }
        } while (changed)
        return result
    }

    private fun sameGradeDetail(first: String, second: String): Boolean {
        if (first.isBlank() || second.isBlank()) return false
        fun normalize(value: String) = cleanGradeDetail(value, "Za co", "Komentarz", "Opis")
            .lowercase(Locale("pl", "PL")).replace(Regex("\\s+"), " ").trim()
        return normalize(first) == normalize(second)
    }

    private fun appendGradeExtraDetails(
        parent: LinearLayout,
        grade: JSONObject,
        sourceKey: String
    ) {
        val seen = mutableSetOf<String>()
        val skillHeading = if (sourceKey == "descriptive_grades" || sourceKey == "partial_grades") {
            "Kategoria"
        } else {
            "Umiejętność"
        }
        listOf(
            "requirements" to "Wymaganie",
            "criteria" to "Kryterium",
            "skills" to skillHeading,
            "skill" to skillHeading
        ).forEach { (field, heading) ->
            val raw = grade.opt(field) ?: return@forEach
            if (raw == JSONObject.NULL) return@forEach
            val values = when (raw) {
                is JSONArray -> (0 until raw.length()).map { displayJsonValue(raw.opt(it)) }
                else -> listOf(displayJsonValue(raw))
            }
            values.forEach { rawValue ->
                val value = cleanGradeDetail(rawValue, heading)
                val identity = heading.lowercase(Locale("pl", "PL")) + "|" +
                    value.lowercase(Locale("pl", "PL")).replace(Regex("\\s+"), " ").trim()
                if (value.isNotBlank() && seen.add(identity)) {
                    parent.addView(label("$heading: $value", 12f, muted))
                }
            }
        }
    }

    private fun stopTimetableTicker() {
        timetableTicker?.let { android.os.Handler(mainLooper).removeCallbacks(it) }
        timetableTicker = null
    }

    override fun onDestroy() {
        stopTimetableTicker()
        activityScope.cancel()
        super.onDestroy()
    }

}

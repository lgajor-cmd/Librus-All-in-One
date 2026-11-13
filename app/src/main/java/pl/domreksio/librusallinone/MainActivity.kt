package pl.domreksio.librusallinone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

    private lateinit var librusVault: LibrusCredentialVault

    private var librusLoginMemory: String? = null
    private var librusPasswordMemory: String? = null
    private var librusSessionMemory: JSONObject? = null
    private var librusDataMemory: JSONObject? = null

    private var syncInProgress = false
    private var autoRestoreStarted = false

    private val white = Color.rgb(242, 245, 250)
    private val muted = Color.rgb(155, 169, 190)
    private val panel = Color.rgb(25, 34, 51)
    private val panelLight = Color.rgb(34, 45, 65)
    private val bgColor = Color.rgb(13, 18, 30)
    private val successColor = Color.rgb(100, 210, 140)
    private val errorColor = Color.rgb(255, 125, 125)
    private val accentColor = Color.rgb(105, 165, 255)
    private val warningColor = Color.rgb(255, 190, 95)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        librusVault = LibrusCredentialVault(applicationContext)
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
        if (isFinishing) return

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 28)
            setBackgroundColor(bgColor)
        }

        content.addView(label("LIBRUS / ALL-IN-ONE", 13f, muted, true))
        content.addView(label("Witaj!", 32f, white, true))
        content.addView(label("Zalogowano jako: $email", 14f, muted))
        content.addView(label("Konto aplikacji działa.", 15f, successColor))

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

        content.addView(sectionHeader("POŁĄCZENIE Z BACKENDEM"), params(0, 20, 0, 5))

        backendStatusText = label("Nie sprawdzono jeszcze połączenia.", 14f, muted)
        content.addView(backendStatusText)

        content.addView(actionButton("SPRAWDŹ POŁĄCZENIE Z SERWEREM") {
            checkBackendConnection()
        }, params(0, 8, 0, 12))

        content.addView(sectionHeader("KONTO LIBRUS"), params(0, 16, 0, 5))
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

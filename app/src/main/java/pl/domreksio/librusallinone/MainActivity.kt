
package pl.domreksio.librusallinone

import android.app.Activity
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
import kotlin.concurrent.thread

private const val SUPABASE_URL =
    "https://alegkhpqxdzuqjuuoawp.supabase.co"

private const val SUPABASE_PUBLISHABLE_KEY =
    "sb_publishable_gchAnvMxlFgvzdenD0QqWg_YoOtsTPe"

private const val AUTH_REDIRECT =
    "pl.domreksio.librusallinone://login-callback"

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

    private val white = Color.rgb(242, 245, 250)
    private val muted = Color.rgb(165, 178, 198)
    private val panel = Color.rgb(25, 34, 51)
    private val bgColor = Color.rgb(13, 18, 30)

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

        content.addView(
            label("LIBRUS / ALL-IN-ONE", 13f, muted, true)
        )
        content.addView(
            label("Twoje konto\nw jednym miejscu", 30f, white, true)
        )
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

        content.addView(
            label("LIBRUS / ALL-IN-ONE", 13f, muted, true)
        )
        content.addView(label("Witaj!", 32f, white, true))
        content.addView(
            label("Zalogowano jako: $email", 14f, muted)
        )
        content.addView(
            label(
                "Konto aplikacji działa. Przygotowujemy połączenie z Librusem.",
                15f, white
            )
        )

        addHomeSection(content, "PROFILE", "Profile rodzica i ucznia")
        addHomeSection(content, "OCENY", "Oceny i średnie – po podłączeniu Librusa")
        addHomeSection(content, "FREKWENCJA", "Obecności i nieobecności – po podłączeniu Librusa")
        addHomeSection(content, "PLAN LEKCJI", "Plan i zastępstwa – po podłączeniu Librusa")
        addHomeSection(content, "WIADOMOŚCI", "Ogłoszenia i wiadomości – po podłączeniu Librusa")

        content.addView(Button(this).apply {
            text = "WYLOGUJ"
            setOnClickListener { signOut() }
        }, params(0, 24, 0, 8))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(content)
        })
    }

    private fun addHomeSection(
        parent: LinearLayout,
        heading: String,
        description: String
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(panel)
        }

        card.addView(label(heading, 14f, white, true))
        card.addView(label(description, 13f, muted))
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
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
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

        statusText.text =
            if (register) "Tworzę konto…" else "Loguję…"

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
                        val session =
                            supabase.auth.currentSessionOrNull()

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

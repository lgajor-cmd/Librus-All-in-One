
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
import io.github.jan.supabase.createSupabaseClient
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

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var status: TextView

    private val white = Color.rgb(242, 245, 250)
    private val muted = Color.rgb(165, 178, 198)
    private val panel = Color.rgb(25, 34, 51)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            supabase.handleDeeplinks(intent)
        } catch (_: Exception) {
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 36, 24, 28)
            setBackgroundColor(Color.rgb(13, 18, 30))
        }

        content.addView(label("LIBRUS / ALL-IN-ONE", 13f, muted, true))
        content.addView(label("Twoje konto\nw jednym miejscu", 30f, white, true))

        content.addView(
            label(
                "Załóż konto lub zaloguj się. Konto aplikacji jest oddzielne od danych Librusa.",
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
            hint = "Hasło do konta aplikacji (min. 10 znaków)"
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
            text = "Zarejestruj konto"
            setOnClickListener { authenticate(true) }
        }, params(0, 4, 0, 6))

        content.addView(Button(this).apply {
            text = "Zaloguj się"
            setOnClickListener { authenticate(false) }
        }, params(0, 4, 0, 6))

        content.addView(Button(this).apply {
            text = "Wyloguj"
            setOnClickListener { signOut() }
        }, params(0, 4, 0, 6))

        status = label("Status: gotowy do logowania", 14f, muted)
        content.addView(status, params(0, 14, 0, 4))

        content.addView(
            label(
                "Po rejestracji sprawdź skrzynkę e-mail i kliknij link potwierdzający. Link powinien otworzyć tę aplikację.",
                13f, muted
            )
        )

        setContentView(ScrollView(this).apply {
            addView(content)
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        try {
            supabase.handleDeeplinks(intent)
            status.text = "Odebrano link logowania. Sprawdzam sesję…"
        } catch (e: Exception) {
            status.text = "Nie udało się obsłużyć linku: ${e.message}"
        }
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
            status.text = "Podaj prawidłowy adres e-mail."
            return
        }

        if (password.length < 10) {
            status.text = "Hasło musi mieć co najmniej 10 znaków."
            return
        }

        status.text = if (register) "Tworzę konto…" else "Loguję…"

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

                        "Konto utworzone. Sprawdź e-mail i kliknij link potwierdzający."
                    } else {
                        supabase.auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }

                        "Logowanie zakończone powodzeniem."
                    }
                }
            } catch (e: Exception) {
                "Błąd: ${e.message ?: e.javaClass.simpleName}"
            }

            runOnUiThread {
                status.text = result
            }
        }
    }

    private fun signOut() {
        status.text = "Wylogowuję…"

        thread {
            val result = try {
                runBlocking {
                    supabase.auth.signOut()
                }

                "Wylogowano z konta aplikacji."
            } catch (e: Exception) {
                "Błąd wylogowania: ${e.message}"
            }

            runOnUiThread {
                status.text = result
            }
        }
    }
}

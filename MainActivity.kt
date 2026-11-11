package pl.domreksio.librusallinone

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.rgb(13, 18, 30)
        val panel = Color.rgb(25, 34, 51)
        val white = Color.rgb(242, 245, 250)
        val muted = Color.rgb(165, 178, 198)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 36, 24, 24)
            setBackgroundColor(bg)
        }
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 8, 0, 8)
            }
        page.addView(label("LIBRUS  /  ALL-IN-ONE", 13f, muted, true))
        page.addView(label("Dziennik szkolny\nw jednym miejscu", 30f, white, true))
        page.addView(label("Wersja testowa. Następnym etapem będzie bezpieczne logowanie i pobieranie danych z Librusa.", 15f, muted))
        val profile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(panel)
        }
        profile.addView(label("PROFILE", 12f, muted, true))
        profile.addView(label("Rodzic     •     Uczeń", 20f, white, true))
        profile.addView(label("Oddzielne profile i dane zostaną podłączone po wdrożeniu bezpiecznego logowania.", 13f, muted))
        page.addView(profile, params(0, 20, 0, 16))
        page.addView(label("MODUŁY DO PODŁĄCZENIA", 12f, muted, true))
        listOf("Oceny i średnie", "Frekwencja", "Plan lekcji i zastępstwa", "Zadania domowe", "Wiadomości i ogłoszenia", "Uwagi i pochwały").forEach {
            page.addView(TextView(this).apply {
                text = "  •  $it"
                textSize = 16f
                setTextColor(white)
                setPadding(16, 15, 16, 15)
                setBackgroundColor(panel)
            }, params(0, 5, 0, 5))
        }
        status = label("Status serwera: jeszcze nie sprawdzono", 14f, muted)
        page.addView(status, params(0, 18, 0, 4))
        page.addView(Button(this).apply {
            text = "Sprawdź połączenie z serwerem"
            setOnClickListener { checkServer() }
        }, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun params(l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(l, t, r, b) }

    private fun checkServer() {
        status.text = "Status serwera: sprawdzam…"
        thread {
            val result = try {
                val c = URL("https://librus-all-in-one-api.onrender.com/health").openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 10000
                val code = c.responseCode
                c.disconnect()
                if (code == 200) "Serwer działa (HTTP 200)." else "Serwer odpowiedział: HTTP $code."
            } catch (e: Exception) {
                "Nie udało się połączyć: ${e.javaClass.simpleName}"
            }
            runOnUiThread { status.text = "Status serwera: $result" }
        }
    }
}

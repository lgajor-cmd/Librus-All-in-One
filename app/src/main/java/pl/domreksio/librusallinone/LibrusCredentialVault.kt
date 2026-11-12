
package pl.domreksio.librusallinone

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Bezpieczny, lokalny magazyn danych logowania Librus.
 * AES-256-GCM, klucz przechowywany w Android Keystore.
 */
class LibrusCredentialVault(
    context: Context,
    private val profile: String = "parent"
) {
    data class Credentials(
        val login: String,
        val password: String,
        val sessionJson: String?
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        "librus_secure_vault",
        Context.MODE_PRIVATE
    )

    private val keyAlias = "pl.domreksio.librusallinone.LibrusVault.$profile"
    private val encryptedValueKey = "credentials_$profile"

    @Synchronized
    fun save(login: String, password: String, sessionJson: String?) {
        require(login.isNotBlank()) { "Login Librusa nie może być pusty." }
        require(password.isNotBlank()) { "Hasło Librusa nie może być puste." }

        val payload = JSONObject().apply {
            put("login", login)
            put("password", password)
            if (sessionJson.isNullOrBlank()) {
                put("session", JSONObject.NULL)
            } else {
                put("session", JSONObject(sessionJson))
            }
        }.toString()

        val encrypted = encrypt(payload.toByteArray(StandardCharsets.UTF_8))
        val stored = JSONObject().apply {
            put("iv", Base64.encodeToString(encrypted.first, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(encrypted.second, Base64.NO_WRAP))
        }.toString()

        check(prefs.edit().putString(encryptedValueKey, stored).commit()) {
            "Nie udało się zapisać danych Librusa."
        }
    }

    @Synchronized
    fun updateSession(sessionJson: String?) {
        val current = load() ?: return
        save(current.login, current.password, sessionJson)
    }

    @Synchronized
    fun load(): Credentials? {
        val stored = prefs.getString(encryptedValueKey, null) ?: return null

        return try {
            val envelope = JSONObject(stored)
            val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
            val ciphertext = Base64.decode(
                envelope.getString("ciphertext"),
                Base64.NO_WRAP
            )
            val plaintext = decrypt(iv, ciphertext)
            val json = JSONObject(String(plaintext, StandardCharsets.UTF_8))

            val login = json.optString("login")
            val password = json.optString("password")
            val sessionValue = json.opt("session")

            if (login.isBlank() || password.isBlank()) {
                null
            } else {
                Credentials(
                    login = login,
                    password = password,
                    sessionJson = if (
                        sessionValue == null ||
                        sessionValue == JSONObject.NULL
                    ) null else sessionValue.toString()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clear() {
        check(prefs.edit().remove(encryptedValueKey).commit()) {
            "Nie udało się usunąć danych Librusa."
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun encrypt(plainText: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.iv to cipher.doFinal(plainText)
    }

    private fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val TAG_LENGTH_BITS = 128
    }
}

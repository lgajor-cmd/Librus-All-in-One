package pl.domreksio.librusallinone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrusCredentialVaultTest {
    private lateinit var context: Context
    private val studentId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private val parentVault: LibrusCredentialVault
        get() = LibrusCredentialVault(context, LibrusProfileStore.LEGACY_PROFILE_ID)
    private val studentVault: LibrusCredentialVault
        get() = LibrusCredentialVault(context, studentId)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runCatching { parentVault.clear() }
        runCatching { studentVault.clear() }
    }

    @After
    fun tearDown() {
        runCatching { parentVault.clear() }
        runCatching { studentVault.clear() }
    }

    @Test
    fun credentialsAndKeystoreKeysAreIsolatedBetweenProfiles() {
        parentVault.save("parent-login", "parent-password", null)
        studentVault.save("student-login", "student-password", null)

        assertEquals("parent-login", parentVault.load()?.login)
        assertEquals("parent-password", parentVault.load()?.password)
        assertEquals("student-login", studentVault.load()?.login)
        assertEquals("student-password", studentVault.load()?.password)

        studentVault.clear()

        assertNull(studentVault.load())
        assertEquals("parent-login", parentVault.load()?.login)
        assertEquals("parent-password", parentVault.load()?.password)
    }

    @Test
    fun invalidProfileIdentifiersAreRejectedBeforeStorageAccess() {
        assertTrue(runCatching {
            LibrusCredentialVault(context, "../other-profile")
        }.isFailure)
        assertTrue(runCatching {
            LibrusCredentialVault(context, "not-a-profile-id")
        }.isFailure)
    }

    @Test
    fun malformedEnvelopeIsReportedWithoutDeletingStoredValue() {
        val storedValue = "not-json"
        context.getSharedPreferences("librus_secure_vault", Context.MODE_PRIVATE)
            .edit().putString("credentials_$studentId", storedValue).commit()

        assertEquals(
            LibrusCredentialVault.LoadStatus.CORRUPTED,
            studentVault.loadDetailed().status
        )
        assertEquals(
            storedValue,
            context.getSharedPreferences("librus_secure_vault", Context.MODE_PRIVATE)
                .getString("credentials_$studentId", null)
        )
    }
}

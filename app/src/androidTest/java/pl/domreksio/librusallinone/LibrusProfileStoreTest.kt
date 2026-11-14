package pl.domreksio.librusallinone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrusProfileStoreTest {
    private lateinit var context: Context
    private lateinit var store: LibrusProfileStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("librus_profiles", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = LibrusProfileStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("librus_profiles", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun firstLaunchMigratesToLegacyParentProfile() {
        val profile = store.ensureDefaultProfile()

        assertEquals(LibrusProfileStore.LEGACY_PROFILE_ID, profile.id)
        assertEquals(LibrusProfileStore.TYPE_PARENT, profile.accountType)
        assertEquals(profile.id, store.getActiveProfileId())
        assertEquals(1, store.getProfiles().size)
    }

    @Test
    fun addingProfileActivatesItWithoutChangingExistingProfile() {
        val parent = store.ensureDefaultProfile()
        val student = store.addProfile("Uczeń", LibrusProfileStore.TYPE_STUDENT)

        assertEquals(student.id, store.getActiveProfileId())
        assertEquals(parent, store.getProfile(parent.id))
        assertEquals("Uczeń", store.getProfile(student.id)?.displayName)
        assertEquals(2, store.getProfiles().size)
    }

    @Test
    fun removingActiveProfileSelectsAnotherAndCannotRemoveLastProfile() {
        val parent = store.ensureDefaultProfile()
        val student = store.addProfile("Uczeń", LibrusProfileStore.TYPE_STUDENT)

        assertTrue(store.removeProfile(student.id))
        assertEquals(parent.id, store.getActiveProfileId())
        assertNull(store.getProfile(student.id))
        assertFalse(store.removeProfile(parent.id))
        assertEquals(listOf(parent), store.getProfiles())
    }

    @Test
    fun invalidProfileInputIsRejected() {
        store.ensureDefaultProfile()

        assertTrue(runCatching {
            store.addProfile("   ", LibrusProfileStore.TYPE_STUDENT)
        }.isFailure)
        assertTrue(runCatching {
            store.addProfile("Nieznany", "admin")
        }.isFailure)
        assertTrue(runCatching {
            store.setActiveProfileId("nie-istnieje")
        }.isFailure)
    }

    @Test
    fun malformedStoredProfilesDoNotCrashAndDefaultCanBeRestored() {
        context.getSharedPreferences("librus_profiles", Context.MODE_PRIVATE)
            .edit().putString("profiles_v1", "not-json").commit()

        assertTrue(store.getProfiles().isEmpty())
        assertNotNull(store.ensureDefaultProfile())
        assertEquals(LibrusProfileStore.LEGACY_PROFILE_ID, store.getActiveProfileId())
    }
}

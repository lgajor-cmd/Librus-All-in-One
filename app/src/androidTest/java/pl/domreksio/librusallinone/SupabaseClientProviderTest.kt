package pl.domreksio.librusallinone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupabaseClientProviderTest {

    @Test
    fun clientUsesExplicitAndroidContextAndIsSharedAcrossCallers() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val first = SupabaseClientProvider.get(context)
        val second = SupabaseClientProvider.get(context)

        assertNotNull(first)
        assertSame("MainActivity and WorkManager must share one client", first, second)
    }
}

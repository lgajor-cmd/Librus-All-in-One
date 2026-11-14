package pl.domreksio.librusallinone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @Test
    fun mainActivityLaunchesAndCreatesContentView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("MainActivity should remain active after launch", activity.isFinishing)
                assertNotNull("Activity should create its content root", activity.findViewById(android.R.id.content))
                assertNotNull("Activity should have a window decor view", activity.window?.decorView)
            }
        }
    }
}

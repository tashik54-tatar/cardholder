package com.tashik54.cardholder

import android.os.Looper
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Проверка, что приложение запускается без падения: onCreate + первая
 * композиция (тема, шрифты, меню, экраны) выполняются целиком.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun activityLaunchesAndComposes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var decor: View? = null
            scenario.onActivity { activity ->
                decor = activity.window.decorView
                decor!!.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
                )
                decor!!.layout(0, 0, 1080, 2400)
            }
            shadowOf(Looper.getMainLooper()).idle()
            // Композиция прошла: контент окна создан, Activity живо
            assertNotNull(decor)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}

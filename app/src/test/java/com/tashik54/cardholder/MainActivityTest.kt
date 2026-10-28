package com.tashik54.cardholder

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.view.View

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
            scenario.onActivity { activity ->
                val view: View = activity.window.decorView
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, 1080, 2400)
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}

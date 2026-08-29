package ru.namaztime.tv.presentation

import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class DisplayKeepAwakeEffectTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun disposalRestoresAHostFlagThatWasAlreadySet() {
        val hostView = View(ApplicationProvider.getApplicationContext()).apply {
            keepScreenOn = true
        }
        val displayActive = mutableStateOf(true)

        compose.setContent {
            if (displayActive.value) DisplayKeepAwakeEffect(hostView)
        }
        compose.runOnIdle { assertTrue(hostView.keepScreenOn) }

        compose.runOnIdle { displayActive.value = false }
        compose.runOnIdle { assertTrue(hostView.keepScreenOn) }
    }
}

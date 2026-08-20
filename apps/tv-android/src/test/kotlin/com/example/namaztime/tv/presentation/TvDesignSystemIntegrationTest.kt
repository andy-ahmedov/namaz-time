package com.example.namaztime.tv.presentation

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvDesignSystemIntegrationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tvMaterialComponentsReceiveTheSemanticDarkPalette() {
        var actual: ColorScheme? = null

        compose.setContent {
            NamazTvTheme {
                actual = MaterialTheme.colorScheme
            }
        }

        compose.runOnIdle {
            val scheme = requireNotNull(actual)
            assertEquals(DarkTvColors.accent, scheme.primary)
            assertEquals(DarkTvColors.backgroundBottom, scheme.background)
            assertEquals(DarkTvColors.textPrimary, scheme.onBackground)
            assertEquals(DarkTvColors.textPrimary, scheme.onSurface)
            assertEquals(DarkTvColors.textSecondary, scheme.onSurfaceVariant)
            assertTrue("TV Material surface must stay dark", scheme.surface.luminance() < 0.1f)
        }
    }

}

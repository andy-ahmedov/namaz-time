package ru.namaztime.tv.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SourceLinkLauncherTest {
    @Test
    fun explicitLaunchCarriesOnlyCanonicalHttpsReferenceAndNoPrivatePayload() {
        var launched: Intent? = null
        val url = "https://dumso.ru/raspisanie"
        assertTrue(launchSourceLink(url) { launched = it })
        assertEquals(Intent.ACTION_VIEW, launched!!.action)
        assertEquals(url, launched!!.dataString)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), launched!!.categories)
        assertNull(launched!!.extras)
        assertNull(launched!!.clipData)
        assertEquals(0, launched!!.flags)
    }

    @Test
    fun unsafeSchemesCredentialsAndMissingBrowserFailLocallyWithoutAnotherRoute() {
        var calls = 0
        listOf("http://example.com", "intent://example.com", "file:///tmp/a", "content://private/a",
            "javascript:alert(1)", "https://user:secret@example.com", "https://example.com/#fragment",
            "https://example.com/\nsecret").forEach { url ->
            assertFalse(launchSourceLink(url) { calls++ })
        }
        assertEquals(0, calls)
        assertFalse(launchSourceLink("https://dumso.ru/raspisanie") { calls++; throw ActivityNotFoundException() })
        assertFalse(launchSourceLink("https://dumso.ru/raspisanie") { calls++; throw SecurityException() })
        assertEquals(2, calls)
    }
}

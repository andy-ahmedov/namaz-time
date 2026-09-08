package ru.namaztime.tv.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import java.net.URI

internal fun sourceLinkIntent(url: String): Intent? {
    val parsed = try { URI(url) } catch (_: Exception) { return null }
    if (url.length !in 1..2048 || url.any { it <= ' ' } || parsed.scheme != "https" ||
        !parsed.isAbsolute || parsed.host.isNullOrEmpty() || parsed.rawUserInfo != null || parsed.rawFragment != null
    ) return null
    return Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
}

/** The system resolver handles browser choice; absence/races are local failures. */
internal fun launchSourceLink(url: String, launch: (Intent) -> Unit): Boolean {
    val intent = sourceLinkIntent(url) ?: return false
    return try {
        launch(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

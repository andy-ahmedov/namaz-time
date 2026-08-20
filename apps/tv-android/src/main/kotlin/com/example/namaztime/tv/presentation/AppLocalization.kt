package com.example.namaztime.tv.presentation

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.namaztime.tv.R
import com.example.namaztime.tv.repository.DEFAULT_LANGUAGE_TAG
import java.util.Locale

enum class AppLanguage(
    val tag: String,
    @StringRes val displayNameRes: Int,
) {
    RUSSIAN("ru", R.string.language_russian),
    ENGLISH("en", R.string.language_english),
    ;

    val locale: Locale
        get() = Locale.forLanguageTag(tag)

    val next: AppLanguage
        get() = if (this == RUSSIAN) ENGLISH else RUSSIAN

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: RUSSIAN
    }
}

val LocalAppLanguage = staticCompositionLocalOf {
    AppLanguage.fromTag(DEFAULT_LANGUAGE_TAG)
}

class AppStrings internal constructor(
    private val resources: Resources,
    val language: AppLanguage,
) {
    val locale: Locale
        get() = language.locale

    fun get(@StringRes id: Int, vararg formatArgs: Any): String =
        resources.getString(id, *formatArgs)
}

fun appStringsFor(context: Context, language: AppLanguage): AppStrings {
    val configuration = Configuration(context.resources.configuration).apply {
        setLocale(language.locale)
    }
    return AppStrings(context.createConfigurationContext(configuration).resources, language)
}

@Composable
fun AppLanguageProvider(languageTag: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppLanguage provides AppLanguage.fromTag(languageTag),
        content = content,
    )
}

@Composable
fun appString(@StringRes id: Int, vararg formatArgs: Any): String {
    return appStrings().get(id, *formatArgs)
}

@Composable
fun appStrings(): AppStrings {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    return remember(context, language) { appStringsFor(context, language) }
}

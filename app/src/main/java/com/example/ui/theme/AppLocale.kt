package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Returns a [Context] whose resources resolve in [language] (values-fa/ for Persian,
 * values/ otherwise), regardless of the device locale.
 *
 * This is what keeps in-app language switching instant: instead of recreating the
 * Activity (the stock per-app-language flow), screens just re-read their strings from
 * a fresh [AppStrings], which wraps the application context with this function.
 *
 * Only resource resolution is affected — [Locale.setDefault] is deliberately not
 * touched, so number/date formatting keeps following the device locale exactly as
 * before the XML migration. Layout direction is likewise untouched (the app forces
 * LTR in MainActivity and lets each string shape its own direction).
 */
fun Context.localizedFor(language: AppLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(language.code))
    return createConfigurationContext(config)
}

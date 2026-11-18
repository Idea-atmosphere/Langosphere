package com.example.zonos

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * v0.2.14: unwrap a Context to its Activity, if it has one.
 *
 * Compose's LocalContext / the AndroidView factory context may be
 * wrapped (ContextThemeWrapper etc.); the application context is not
 * an Activity at all. A WebView built with a non-Activity context
 * cannot open system popup windows: Chromium's <select> dropdown
 * needs an Activity window token, so the tap registers and no option
 * list ever appears — exactly what the user reported for every system
 * <select> in the Studio (Direct line-voice, Default voice, Project
 * settings, Pron, Edit). Returns null when no Activity is in the
 * chain, so callers fail fast instead of silently building a broken
 * WebView.
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

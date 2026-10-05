package com.example.logic

import android.content.pm.ActivityInfo

/**
 * «چرخش صفحه» — the manual portrait ⇄ landscape switch of the online player.
 *
 * The next orientation is decided from what the activity currently *asks
 * for* (`requestedOrientation`); only when it asks for nothing specific
 * (UNSPECIFIED, SENSOR, USER, …) does the real screen orientation decide.
 * The activity declares `configChanges="orientation|screenSize|…"`, so the
 * switch never recreates it and playback (ExoPlayer or the WebView embed)
 * keeps running through the rotation.
 *
 * Pure function of two inputs, so it is unit tested without a device.
 */
object OrientationToggle {

    private val LANDSCAPE_REQUESTS = setOf(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    )

    private val PORTRAIT_REQUESTS = setOf(
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    )

    /** True when the activity is (or is about to be) shown in landscape. */
    fun isLandscape(requestedOrientation: Int, configIsLandscape: Boolean): Boolean = when (requestedOrientation) {
        in LANDSCAPE_REQUESTS -> true
        in PORTRAIT_REQUESTS -> false
        else -> configIsLandscape
    }

    /** The orientation to request next: portrait from landscape, landscape otherwise. */
    fun next(requestedOrientation: Int, configIsLandscape: Boolean): Int =
        if (isLandscape(requestedOrientation, configIsLandscape)) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}

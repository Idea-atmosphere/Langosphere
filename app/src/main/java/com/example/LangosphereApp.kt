package com.example

import android.app.Application
import android.os.StrictMode
import com.example.logic.CrashReporter

/**
 * Exists so the crash handler is installed before anything else in the
 * process runs. Application.onCreate happens before any activity, service or
 * background coroutine, so a failure during startup is now recorded instead
 * of vanishing.
 */
class LangosphereApp : Application() {

    override fun onCreate() {
        super.onCreate()

        CrashReporter.install(this)

        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
    }

    /**
     * Debug builds only, and reporting only — StrictMode never kills the app
     * here. It surfaces disk/network work on the main thread and Cursor or
     * other Closeable objects that were never closed, both of which this
     * codebase does in places (see the SQLite helpers).
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}

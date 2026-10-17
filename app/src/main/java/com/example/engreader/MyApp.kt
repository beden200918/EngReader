package com.example.engreader

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val process = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else packageName
        if (process.endsWith(":crash")) return

        val prefs = getSharedPreferences("crash", MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            prefs.edit().putString("last_crash", Log.getStackTraceString(error)).commit()
            previous?.uncaughtException(thread, error)
        }

        val saved = prefs.getString("last_crash", null)
        if (saved != null) {
            prefs.edit().remove("last_crash").apply()
            startActivity(
                Intent(this, ErrorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("trace", saved)
            )
        }
    }
}

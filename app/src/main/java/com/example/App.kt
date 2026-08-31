package com.example

import android.app.Application
import android.util.Log
import com.example.model.LogLevel
import com.example.util.Logger

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Persistent Logger
        Logger.init(this)

        // Install Global Process-Wide Uncaught Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = Log.getStackTraceString(throwable)
                val crashMsg = "FATAL CRASH on Thread [${thread.name}]: ${throwable.javaClass.simpleName}: ${throwable.message}\n$stackTrace"
                Log.e("CRASH_HANDLER", crashMsg)
                Logger.log("CRASH", crashMsg, LogLevel.ERROR)
            } catch (e: Exception) {
                Log.e("CRASH_HANDLER", "Error saving crash log", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

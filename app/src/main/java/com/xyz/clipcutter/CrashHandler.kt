package com.xyz.clipcutter

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches any uncaught exception app-wide and writes the full stack trace to a local
 * file before letting the app die normally. Since the phone-only workflow has no
 * adb/logcat access, this file is the only way to retrieve a crash reason — it can be
 * shared from the "Info & Links" screen after a restart.
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeCrashLog(throwable)
        } catch (_: Exception) {
            // Never let logging itself throw and mask the original crash.
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun writeCrashLog(throwable: Throwable) {
        val dir = File(context.filesDir, "crash_logs")
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash_$timestamp.txt")

        val builder = StringBuilder()
        builder.append("ClipCutter crash log\n")
        builder.append("Time: $timestamp\n")
        builder.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        builder.append("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        builder.append("\n--- Stack trace ---\n")
        builder.append(throwable.stackTraceToString())

        file.writeText(builder.toString())
    }

    companion object {
        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext))
        }

        fun crashLogsDir(context: Context): File {
            return File(context.filesDir, "crash_logs")
        }

        fun latestCrashLog(context: Context): File? {
            val dir = crashLogsDir(context)
            return dir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
        }

        fun hasCrashLogs(context: Context): Boolean {
            val dir = crashLogsDir(context)
            return dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
        }

        fun clearCrashLogs(context: Context) {
            crashLogsDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}

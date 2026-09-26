package com.ameer.autoefootballgamepad.util

import android.content.Context
import android.util.Log
import com.ameer.autoefootballgamepad.core.AppState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ErrorLogger(context: Context) {
    private val file = File(context.filesDir, "auto_efootball_gamepad.log")
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(message: String, error: Throwable? = null) {
        val line = buildString {
            append(formatter.format(Date()))
            append(" | ")
            append(message)
            if (error != null) append(" | ${error::class.java.simpleName}: ${error.message}")
        }
        Log.w("AutoEFootballGamepad", line, error)
        runCatching {
            file.appendText(line + "\n")
            trimIfNeeded()
        }
        AppState.setError(message)
    }

    fun lastLines(max: Int = 20): List<String> = runCatching {
        if (!file.exists()) emptyList() else file.readLines().takeLast(max)
    }.getOrDefault(emptyList())

    private fun trimIfNeeded() {
        if (!file.exists() || file.length() < 256_000) return
        val keep = file.readLines().takeLast(300)
        file.writeText(keep.joinToString("\n", postfix = "\n"))
    }
}

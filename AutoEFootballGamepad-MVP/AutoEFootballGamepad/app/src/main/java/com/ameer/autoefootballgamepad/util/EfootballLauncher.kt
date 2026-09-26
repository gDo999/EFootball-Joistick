package com.ameer.autoefootballgamepad.util

import android.content.Context
import android.content.Intent

object EfootballLauncher {
    const val PACKAGE_NAME = "jp.konami.pesam"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        true
    }.getOrDefault(false)

    fun launch(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        return true
    }
}

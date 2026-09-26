package com.ameer.autoefootballgamepad.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.ameer.autoefootballgamepad.service.EfootballAccessibilityService

object PermissionUtils {
    fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val si = info.resolveInfo.serviceInfo
                si.packageName == context.packageName &&
                    si.name == EfootballAccessibilityService::class.java.name
            }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
}

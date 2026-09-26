package com.ameer.autoefootballgamepad.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ameer.autoefootballgamepad.core.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var statusView: TextView? = null
    private var collectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.ameer.autoefootballgamepad.R.drawable.ic_gamepad)
                .setContentTitle("Auto eFootball Gamepad")
                .setContentText("Controller mapping service ready")
                .setOngoing(true)
                .setSilent(true)
                .build()
        )

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WindowManager::class.java)
        val view = TextView(this).apply {
            text = "🎮 Ready"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(20, 10, 20, 10)
            background = GradientDrawable().apply {
                setColor(0xCC111827.toInt())
                cornerRadius = 28f
            }
        }
        statusView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 70
        }
        windowManager.addView(view, params)

        collectJob = scope.launch {
            AppState.state.collectLatest { state ->
                val controller = state.controller?.name?.take(18) ?: "No controller"
                view.text = when {
                    state.mappingActive && state.inputMode.startsWith("Native") -> "🎮 Native · $controller"
                    state.mappingActive -> "🎮 Mapper ON · $controller"
                    state.efootballDetected -> "🎮 eFootball detected"
                    else -> "🎮 Ready · $controller"
                }
            }
        }
    }

    override fun onDestroy() {
        collectJob?.cancel()
        statusView?.let { runCatching { windowManager.removeView(it) } }
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Gamepad mapping", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "gamepad_mapping"
        private const val NOTIFICATION_ID = 1042

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}

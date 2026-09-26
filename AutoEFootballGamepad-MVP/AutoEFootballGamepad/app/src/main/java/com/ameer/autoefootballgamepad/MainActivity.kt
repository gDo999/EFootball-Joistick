package com.ameer.autoefootballgamepad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ameer.autoefootballgamepad.controller.AutoControllerDetector
import com.ameer.autoefootballgamepad.core.AppState
import com.ameer.autoefootballgamepad.service.OverlayService
import com.ameer.autoefootballgamepad.ui.AutoGamepadTheme
import com.ameer.autoefootballgamepad.util.EfootballLauncher
import com.ameer.autoefootballgamepad.util.ErrorLogger
import com.ameer.autoefootballgamepad.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private lateinit var controllerDetector: AutoControllerDetector
    private lateinit var logger: ErrorLogger
    private var pendingStart = false
    private var awaitingSettingsReturn: SettingsStep? = null
    private var showDisclaimer by mutableStateOf(false)
    private var userMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = ErrorLogger(this)
        controllerDetector = AutoControllerDetector(this)
        controllerDetector.start()

        val accepted = getSharedPreferences("onboarding", 0)
            .getBoolean("risk_notice_accepted", false)
        showDisclaimer = !accepted

        setContent {
            AutoGamepadTheme {
                val state by AppState.state.collectAsState()
                HomeScreen(
                    state = state,
                    message = userMessage,
                    onStart = { beginStartFlow() },
                    onDismissMessage = { userMessage = null }
                )
                if (showDisclaimer) RiskDialog(
                    onAccept = {
                        getSharedPreferences("onboarding", 0).edit()
                            .putBoolean("risk_notice_accepted", true)
                            .apply()
                        showDisclaimer = false
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val returnedFrom = awaitingSettingsReturn ?: return
        awaitingSettingsReturn = null
        if (!pendingStart) return

        Handler(Looper.getMainLooper()).postDelayed({
            val granted = when (returnedFrom) {
                SettingsStep.ACCESSIBILITY -> PermissionUtils.isAccessibilityEnabled(this)
                SettingsStep.OVERLAY -> PermissionUtils.canDrawOverlays(this)
            }
            if (granted) {
                advanceSetupAndStart()
            } else {
                pendingStart = false
                userMessage = when (returnedFrom) {
                    SettingsStep.ACCESSIBILITY -> "لم يتم تفعيل خدمة Accessibility. اضغط Start وحاول مرة أخرى عندما تكون جاهزاً."
                    SettingsStep.OVERLAY -> "لم يتم منح صلاحية Overlay. اضغط Start وحاول مرة أخرى عندما تكون جاهزاً."
                }
            }
        }, 250L)
    }

    override fun onDestroy() {
        controllerDetector.stop()
        super.onDestroy()
    }

    private fun beginStartFlow() {
        if (showDisclaimer) return
        pendingStart = true
        advanceSetupAndStart()
    }

    private fun advanceSetupAndStart() {
        if (!pendingStart) return

        if (!PermissionUtils.isAccessibilityEnabled(this)) {
            userMessage = "فعّل خدمة Auto eFootball Gamepad Input ثم ارجع للتطبيق. هذه الخطوة يفرضها Android ولا يمكن تفعيلها تلقائياً."
            awaitingSettingsReturn = SettingsStep.ACCESSIBILITY
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        if (!PermissionUtils.canDrawOverlays(this)) {
            userMessage = "اسمح للتطبيق بالظهور فوق التطبيقات مرة واحدة، ثم ارجع."
            awaitingSettingsReturn = SettingsStep.OVERLAY
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (!EfootballLauncher.isInstalled(this)) {
            pendingStart = false
            userMessage = "لم أجد eFootball على هذا الجهاز (jp.konami.pesam)."
            return
        }

        runCatching { OverlayService.start(this) }
            .onFailure { logger.log("Could not start overlay service", it) }

        if (!EfootballLauncher.launch(this)) {
            logger.log("Could not launch eFootball")
            userMessage = "تعذر تشغيل eFootball."
            pendingStart = false
            return
        }

        userMessage = null
        pendingStart = false
    }

    private enum class SettingsStep { ACCESSIBILITY, OVERLAY }

    @Composable
    private fun RiskDialog(onAccept: () -> Unit) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("قبل الاستخدام") },
            text = {
                Text(
                    "هذا تطبيق غير رسمي. على Android 14 أو أحدث يحوّل إدخال يد التحكم إلى لمس 1:1 فقط، سواء كانت DualSense أو DUALSHOCK 4 أو Xbox أو Generic، وبدون Bots أو Macros. " +
                        "على Android الأقدم يعتمد على دعم eFootball الأصلي للأيدي المعروفة فقط. قد تتغير واجهة اللعبة بعد التحديثات، " +
                        "وقد تعتبر KONAMI بعض أدوات الطرف الثالث غير مصرح بها خصوصاً في المباريات التنافسية. " +
                        "الاستخدام على مسؤوليتك."
                )
            },
            confirmButton = {
                Button(onClick = onAccept) { Text("فهمت") }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    state: com.ameer.autoefootballgamepad.core.AppUiState,
    message: String?,
    onStart: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎮", style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            "Auto eFootball Gamepad",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Connect · Start · Play",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                StatusCard(
                    label = "Controller Connected",
                    active = state.controller != null,
                    detail = state.controller?.let { "${it.name} · ${it.family.name.replace('_', ' ')}" }
                        ?: "Waiting for Bluetooth or USB controller"
                )
                Spacer(Modifier.height(12.dp))
                StatusCard(
                    label = "eFootball Detected",
                    active = state.efootballDetected,
                    detail = if (state.efootballDetected) "Game window active" else "Starts automatically with the button below"
                )
                Spacer(Modifier.height(12.dp))
                StatusCard(
                    label = "Auto Mapping Active",
                    active = state.mappingActive,
                    detail = when {
                        state.mappingActive && state.inputMode.startsWith("Native") -> state.inputMode
                        !state.fullAnalogSupport -> "Android 14+ required for full controller-to-touch capture"
                        state.mappingActive && state.calibrationConfidence != null ->
                            "${state.inputMode} · ${state.controlLayout} · calibration ${(state.calibrationConfidence * 100).toInt()}%"
                        state.mappingActive -> state.inputMode
                        state.inputMode.startsWith("Auto-calibrating") -> state.inputMode
                        else -> "Activates automatically in eFootball"
                    }
                )

                if (message != null) {
                    Spacer(Modifier.height(18.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = onDismissMessage,
                                modifier = Modifier.align(Alignment.End)
                            ) { Text("OK") }
                        }
                    }
                }

                state.lastError?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Last log: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column {
                if (!state.fullAnalogSupport) {
                    Text(
                        "لأفضل تجربة استخدم Android 14 أو أحدث.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Start eFootball", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "No profiles · No manual mapping · 1:1 input only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatusCard(label: String, active: Boolean, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (active) Color(0xFF22C55E) else Color(0xFF9CA3AF))
            )
            Column(Modifier.padding(start = 14.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

package com.kukurigu.sunalarm.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kukurigu.sunalarm.solar.DawnPhase
import com.kukurigu.sunalarm.ui.theme.CrowingRooster
import com.kukurigu.sunalarm.ui.theme.DawnBackdrop
import com.kukurigu.sunalarm.ui.theme.KukuriguTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Full-screen alarm activity shown over the lock screen when a dawn-phase alarm
 * fires. A crowing rooster greets the sunrise over a dawn-sky backdrop, with the
 * phase, the actual ring time, and Dismiss / Snooze actions that drive
 * [AlarmService].
 */
class AlarmActivity : ComponentActivity() {

    private var phase: DawnPhase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenBehaviour()

        val phaseName = intent?.getStringExtra(AlarmConstants.EXTRA_PHASE)
        phase = phaseName?.let { runCatching { DawnPhase.valueOf(it) }.getOrNull() }
        val label = intent?.getStringExtra(AlarmConstants.EXTRA_LABEL)
            ?: phase?.title
            ?: "Alarm"
        val timeMillis = intent?.getLongExtra(
            AlarmConstants.EXTRA_TIME_MILLIS,
            System.currentTimeMillis(),
        ) ?: System.currentTimeMillis()

        setContent {
            KukuriguTheme {
                AlarmScreen(
                    title = label,
                    description = phase?.description ?: "",
                    timeText = formatTime(timeMillis),
                    onDismiss = ::onDismiss,
                    onSnooze = ::onSnooze,
                )
            }
        }
    }

    private fun configureLockScreenBehaviour() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun onDismiss() {
        phase?.let { startService(AlarmService.dismissIntent(this, it)) }
        finish()
    }

    private fun onSnooze() {
        phase?.let { startService(AlarmService.snoozeIntent(this, it)) }
        finish()
    }

    private fun formatTime(timeMillis: Long): String {
        val time = Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val formatter = DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
        return time.format(formatter)
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    description: String,
    timeText: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    DawnBackdrop(modifier = Modifier.fillMaxSize(), dark = dark) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (description.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            CrowingRooster(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                dark = dark,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "KUKURIGÚ!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD27A),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF24180F),
                    ),
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text("Snooze", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

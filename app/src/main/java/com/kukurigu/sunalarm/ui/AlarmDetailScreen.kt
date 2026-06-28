package com.kukurigu.sunalarm.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kukurigu.sunalarm.data.AlarmConfig
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val OFFSET_MIN = -120
private const val OFFSET_MAX = 120
private const val SNOOZE_MIN = 1
private const val SNOOZE_MAX = 60

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmDetailScreen(
    config: AlarmConfig,
    onSave: (AlarmConfig) -> Unit,
    onBack: () -> Unit,
) {
    var label by remember(config.phase) { mutableStateOf(config.label) }
    var offsetMinutes by remember(config.phase) { mutableIntStateOf(config.offsetMinutes) }
    var snoozeMinutes by remember(config.phase) { mutableIntStateOf(config.snoozeMinutes) }
    var vibrate by remember(config.phase) { mutableStateOf(config.vibrate) }
    var days by remember(config.phase) { mutableStateOf(config.daysOfWeek) }
    var soundUri by remember(config.phase) { mutableStateOf(config.soundUri) }
    var soundName by remember(config.phase) { mutableStateOf(config.soundName) }

    val context = LocalContext.current
    var previewing by remember { mutableStateOf(false) }
    val previewRingtone = remember { mutableStateOf<Ringtone?>(null) }
    val stopPreview = {
        previewRingtone.value?.stop()
        previewRingtone.value = null
        previewing = false
    }
    DisposableEffect(Unit) {
        onDispose {
            previewRingtone.value?.stop()
            previewRingtone.value = null
        }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            stopPreview()
            val picked = ringtonePickedUri(result.data)
            soundUri = picked?.toString()
            soundName = picked?.let { ringtoneTitle(context, it) }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // Persist read access so the file still plays days later / after reboot.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            stopPreview()
            soundUri = uri.toString()
            soundName = documentName(context, uri) ?: "Selected audio"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(config.phase.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard {
                Text(
                    text = config.phase.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard {
                Text("Offset", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = offsetLabel(offsetMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = offsetMinutes.toFloat(),
                    onValueChange = { offsetMinutes = it.roundToInt() },
                    valueRange = OFFSET_MIN.toFloat()..OFFSET_MAX.toFloat(),
                    steps = (OFFSET_MAX - OFFSET_MIN) / 5 - 1,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("-2h", style = MaterialTheme.typography.labelMedium)
                    Text("On time", style = MaterialTheme.typography.labelMedium)
                    Text("+2h", style = MaterialTheme.typography.labelMedium)
                }
            }

            SectionCard {
                Text("Repeat", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { dow ->
                        val value = dow.value // 1=Mon..7=Sun, matches AlarmConfig contract
                        FilterChip(
                            selected = days.contains(value),
                            onClick = {
                                days = if (days.contains(value)) days - value else days + value
                            },
                            label = {
                                Text(dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                            },
                        )
                    }
                }
            }

            SectionCard {
                Text("Sound", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = soundName ?: if (soundUri == null) "Default alarm sound" else "Custom sound",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_ALARM,
                                )
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                                )
                                soundUri?.let {
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        Uri.parse(it),
                                    )
                                }
                            }
                            ringtoneLauncher.launch(intent)
                        },
                    ) { Text("Ringtones") }
                    OutlinedButton(
                        onClick = { fileLauncher.launch(arrayOf("audio/*")) },
                    ) { Text("Audio file") }
                    if (soundUri != null) {
                        TextButton(
                            onClick = {
                                stopPreview()
                                soundUri = null
                                soundName = null
                            },
                        ) { Text("Default") }
                    }
                }
                TextButton(
                    onClick = {
                        if (previewing) {
                            stopPreview()
                        } else {
                            val uri = soundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            val ringtone = runCatching {
                                RingtoneManager.getRingtone(context, uri)
                            }.getOrNull()
                            if (ringtone != null) {
                                runCatching { ringtone.play() }
                                previewRingtone.value = ringtone
                                previewing = true
                            }
                        }
                    },
                ) {
                    Text(if (previewing) "Stop preview" else "Preview")
                }
            }

            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vibrate", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Vibrate while the alarm rings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
            }

            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Snooze", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$snoozeMinutes min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Stepper(
                        onDecrement = {
                            snoozeMinutes = (snoozeMinutes - 1).coerceAtLeast(SNOOZE_MIN)
                        },
                        onIncrement = {
                            snoozeMinutes = (snoozeMinutes + 1).coerceAtMost(SNOOZE_MAX)
                        },
                    )
                }
            }

            Button(
                onClick = {
                    onSave(
                        config.copy(
                            label = label.ifBlank { config.phase.title },
                            offsetMinutes = offsetMinutes,
                            snoozeMinutes = snoozeMinutes,
                            vibrate = vibrate,
                            daysOfWeek = days,
                            soundUri = soundUri,
                            soundName = soundName,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = days.isNotEmpty(),
            ) {
                Text("Save")
            }
            if (days.isEmpty()) {
                Text(
                    text = "Select at least one day.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun Stepper(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onDecrement) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Decrease")
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onIncrement) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Increase")
        }
    }
}

private fun offsetLabel(minutes: Int): String = when {
    minutes == 0 -> "Ring exactly at the phase"
    minutes < 0 -> "Ring ${abs(minutes)} min before"
    else -> "Ring $minutes min after"
}

/** Extracts the picked ringtone URI from the system picker's result intent. */
private fun ringtonePickedUri(data: Intent?): Uri? = when {
    data == null -> null
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    else ->
        @Suppress("DEPRECATION")
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
}

/** Human-readable title for a ringtone URI, or null if it can't be resolved. */
private fun ringtoneTitle(context: Context, uri: Uri): String? =
    runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()

/** Display name of a Storage-Access-Framework document URI, or null. */
private fun documentName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

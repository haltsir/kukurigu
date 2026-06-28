package com.kukurigu.sunalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import com.kukurigu.sunalarm.solar.DawnPhase
import com.kukurigu.sunalarm.ui.theme.KukuriguHero
import com.kukurigu.sunalarm.ui.theme.PhaseAstronomical
import com.kukurigu.sunalarm.ui.theme.PhaseCivil
import com.kukurigu.sunalarm.ui.theme.PhaseNautical
import com.kukurigu.sunalarm.ui.theme.PhaseSunrise

@Composable
fun AlarmListScreen(
    state: AlarmListUiState,
    onToggle: (DawnPhase, Boolean) -> Unit,
    onOpenAlarm: (DawnPhase) -> Unit,
    onOpenLocation: () -> Unit,
    onTestAlarm: () -> Unit,
) {
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                KukuriguHero(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
            }

            item {
                // One location card: a prompt when nothing is set yet, otherwise a
                // compact summary you can tap to change.
                if (state.location == null) {
                    NoLocationHint(onOpenLocation = onOpenLocation)
                } else {
                    LocationHeaderCard(
                        state = state,
                        onOpenLocation = onOpenLocation,
                    )
                }
            }

            items(state.alarms, key = { it.config.phase }) { alarm ->
                PhaseCard(
                    alarm = alarm,
                    onToggle = { enabled -> onToggle(alarm.config.phase, enabled) },
                    onClick = { onOpenAlarm(alarm.config.phase) },
                )
            }

            item {
                OutlinedButton(
                    onClick = onTestAlarm,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Test the alarm (crows in ~8 s)")
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LocationHeaderCard(
    state: AlarmListUiState,
    onOpenLocation: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLocation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = state.location?.let { loc ->
                        loc.label.ifBlank {
                            "%.4f, %.4f".format(loc.latitude, loc.longitude)
                        }
                    } ?: "Not set",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpenLocation) {
                Text(if (state.location == null) "Set" else "Change")
            }
        }
    }
}

@Composable
private fun NoLocationHint(onOpenLocation: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "No location yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Set your location so Kukurigu can follow the sunrise where you are.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenLocation) {
                Text("Set location")
            }
        }
    }
}

@Composable
private fun PhaseCard(
    alarm: AlarmUiState,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val config = alarm.config
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(phaseColor(config.phase)),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.label.ifBlank { config.phase.title },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = config.phase.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = nextLabel(alarm),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (config.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

private fun nextLabel(alarm: AlarmUiState): String {
    val cfg = alarm.config
    return if (cfg.enabled) "Next: ${alarm.nextTriggerText}" else alarm.nextTriggerText
}

private fun phaseColor(phase: DawnPhase): Color = when (phase) {
    DawnPhase.ASTRONOMICAL -> PhaseAstronomical
    DawnPhase.NAUTICAL -> PhaseNautical
    DawnPhase.CIVIL -> PhaseCivil
    DawnPhase.SUNRISE -> PhaseSunrise
}

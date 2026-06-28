package com.kukurigu.sunalarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kukurigu.sunalarm.solar.DawnPhase
import com.kukurigu.sunalarm.ui.AlarmDetailScreen
import com.kukurigu.sunalarm.ui.AlarmListScreen
import com.kukurigu.sunalarm.ui.LocationScreen
import com.kukurigu.sunalarm.ui.MainViewModel
import com.kukurigu.sunalarm.ui.theme.KukuriguTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Defensive: receivers may run before Application in edge cases.
        ServiceLocator.ensureInit(applicationContext)
        enableEdgeToEdge()
        setContent {
            KukuriguTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val viewModel: MainViewModel = viewModel()
                    App(viewModel)
                }
            }
        }
    }
}

/** Sealed in-Compose navigation state. No external nav dependency. */
private sealed interface Screen {
    data object List : Screen
    data class Detail(val phase: DawnPhase) : Screen
    data object Location : Screen
}

@Composable
private fun App(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val detecting by viewModel.detecting.collectAsState()
    val message by viewModel.message.collectAsState()
    val cityQuery by viewModel.cityQuery.collectAsState()
    val cityResults by viewModel.cityResults.collectAsState()
    val searching by viewModel.searching.collectAsState()

    var screen by rememberSaveable(
        stateSaver = ScreenSaver,
    ) { mutableStateOf<Screen>(Screen.List) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Surface transient view-model messages as a snackbar.
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // --- Runtime permission launchers ---
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result intentionally ignored; alarms still fire, only the notification UI degrades */ }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.detectLocation() }

    // Request POST_NOTIFICATIONS once on first composition (Android 13+).
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Re-evaluate exact-alarm + full-screen-intent grants whenever we resume.
    var canScheduleExact by remember { mutableStateOf(checkExactAlarm(context)) }
    var canUseFullScreen by remember { mutableStateOf(checkFullScreenIntent(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExact = checkExactAlarm(context)
                canUseFullScreen = checkFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!canScheduleExact) {
                ExactAlarmBanner(onGrant = { openExactAlarmSettings(context) })
            }
            if (!canUseFullScreen) {
                FullScreenIntentBanner(onGrant = { openFullScreenIntentSettings(context) })
            }
            when (val s = screen) {
                Screen.List -> AlarmListScreen(
                    state = state,
                    onToggle = viewModel::toggleAlarm,
                    onOpenAlarm = { phase -> screen = Screen.Detail(phase) },
                    onOpenLocation = { screen = Screen.Location },
                    onTestAlarm = viewModel::fireTestAlarm,
                )

                is Screen.Detail -> {
                    val config = viewModel.configFor(s.phase)
                    if (config == null) {
                        // Config not loaded yet (or removed); fall back to the list.
                        screen = Screen.List
                    } else {
                        AlarmDetailScreen(
                            config = config,
                            onSave = { updated ->
                                viewModel.updateAlarm(updated)
                                screen = Screen.List
                            },
                            onBack = { screen = Screen.List },
                        )
                    }
                }

                Screen.Location -> LocationScreen(
                    location = state.location,
                    detecting = detecting,
                    searching = searching,
                    query = cityQuery,
                    results = cityResults,
                    onQueryChange = viewModel::searchCities,
                    onSelectCity = { city ->
                        viewModel.selectCity(city)
                        screen = Screen.List
                    },
                    onDetect = {
                        if (hasLocationPermission(context)) {
                            viewModel.detectLocation()
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    onSaveManual = { lat, lng, label -> viewModel.setManualLocation(lat, lng, label) },
                    onBack = { screen = Screen.List },
                )
            }
        }
    }
}

@Composable
private fun ExactAlarmBanner(onGrant: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Exact alarms are off",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Kukurigu needs permission to ring at the exact sunrise time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onGrant) { Text("Allow") }
        }
    }
}

@Composable
private fun FullScreenIntentBanner(onGrant: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Full-screen alarms are off",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Allow full-screen notifications so the alarm shows over your lock screen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onGrant) { Text("Allow") }
        }
    }
}

private val ScreenSaver = androidx.compose.runtime.saveable.Saver<Screen, Any>(
    save = { screen ->
        when (screen) {
            Screen.List -> "list"
            Screen.Location -> "location"
            is Screen.Detail -> "detail:${screen.phase.name}"
        }
    },
    restore = { value ->
        val s = value as String
        when {
            s == "list" -> Screen.List
            s == "location" -> Screen.Location
            s.startsWith("detail:") -> Screen.Detail(DawnPhase.valueOf(s.removePrefix("detail:")))
            else -> Screen.List
        }
    },
)

private fun checkExactAlarm(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return am.canScheduleExactAlarms()
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

private fun checkFullScreenIntent(context: Context): Boolean {
    // canUseFullScreenIntent() exists from Android 14 (API 34); below that the
    // USE_FULL_SCREEN_INTENT permission is granted at install time.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.canUseFullScreenIntent()
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

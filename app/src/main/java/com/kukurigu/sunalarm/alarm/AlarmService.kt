package com.kukurigu.sunalarm.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kukurigu.sunalarm.R
import com.kukurigu.sunalarm.ServiceLocator
import com.kukurigu.sunalarm.data.AlarmConfig
import com.kukurigu.sunalarm.solar.DawnPhase

/**
 * Foreground service that actually wakes the user: it plays the looping alarm
 * ringtone on the alarm audio stream, vibrates, holds a wake lock, and posts a
 * high-importance full-screen notification that launches [AlarmActivity].
 *
 * Robustness contract:
 *  - [onStartCommand] promotes the service to the foreground on EVERY entry
 *    (fire, dismiss, snooze, or a redelivered null intent) before doing anything
 *    else, so a service created via `startForegroundService()` can never miss its
 *    mandatory [startForeground] call and trigger
 *    `ForegroundServiceDidNotStartInTimeException`.
 *  - It performs NO DataStore / disk reads on the fire path. The vibrate, sound,
 *    and snooze settings are snapshotted into the fire intent by
 *    [AlarmManagerScheduler], so ringing starts without blocking I/O.
 *  - It returns [START_NOT_STICKY]: the alarm is one-shot and is re-armed by
 *    [AlarmReceiver]/[AlarmManager], so there is no useful null-intent restart.
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false

    private var activePhase: DawnPhase? = null
    private var activeLabel: String = ""
    private var activeTimeMillis: Long = 0L
    private var activeVibrate: Boolean = true
    private var activeSoundUri: String? = null
    private var activeSnoozeMinutes: Int = AlarmConstants.DEFAULT_SNOOZE_MINUTES

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always become foreground first — even for dismiss/snooze or a null
        // restart intent — so the startForegroundService() contract is satisfied.
        promoteToForeground(intent)

        when (intent?.action) {
            AlarmConstants.ACTION_DISMISS -> stopAlarm()
            AlarmConstants.ACTION_SNOOZE -> handleSnooze(intent)
            AlarmConstants.ACTION_FIRE -> startAlarm(intent)
            else -> stopAlarm() // null/unknown (e.g. process-restart redelivery): bail safely
        }

        // One-shot alarm; never redeliver a null intent. Re-arming is AlarmManager's job.
        return START_NOT_STICKY
    }

    /**
     * Calls [startForeground] exactly once per service lifecycle, building the
     * best notification it can from whatever the [intent] carries (falling back
     * to a minimal placeholder).
     */
    private fun promoteToForeground(intent: Intent?) {
        if (isForeground) return
        val phase = intent?.getStringExtra(AlarmConstants.EXTRA_PHASE)
            ?.let { runCatching { DawnPhase.valueOf(it) }.getOrNull() }
        val notification = if (phase != null) {
            val label = intent.getStringExtra(AlarmConstants.EXTRA_LABEL) ?: phase.title
            val timeMillis =
                intent.getLongExtra(AlarmConstants.EXTRA_TIME_MILLIS, System.currentTimeMillis())
            val vibrate = intent.getBooleanExtra(AlarmConstants.EXTRA_VIBRATE, true)
            val soundUri = intent.getStringExtra(AlarmConstants.EXTRA_SOUND_URI)
            val snooze = intent.getIntExtra(
                AlarmConstants.EXTRA_SNOOZE_MINUTES,
                AlarmConstants.DEFAULT_SNOOZE_MINUTES,
            )
            buildNotification(phase, label, timeMillis, vibrate, soundUri, snooze)
        } else {
            buildPlaceholderNotification()
        }
        startInForeground(notification)
        isForeground = true
    }

    private fun startAlarm(intent: Intent?) {
        val phaseName = intent?.getStringExtra(AlarmConstants.EXTRA_PHASE)
        val phase = phaseName?.let { runCatching { DawnPhase.valueOf(it) }.getOrNull() }
        if (phase == null) {
            Log.w(TAG, "startAlarm with unknown phase: $phaseName")
            stopAlarm()
            return
        }
        activePhase = phase
        activeLabel = intent.getStringExtra(AlarmConstants.EXTRA_LABEL) ?: phase.title
        activeTimeMillis =
            intent.getLongExtra(AlarmConstants.EXTRA_TIME_MILLIS, System.currentTimeMillis())
        activeVibrate = intent.getBooleanExtra(AlarmConstants.EXTRA_VIBRATE, true)
        activeSoundUri = intent.getStringExtra(AlarmConstants.EXTRA_SOUND_URI)
        activeSnoozeMinutes = intent.getIntExtra(
            AlarmConstants.EXTRA_SNOOZE_MINUTES,
            AlarmConstants.DEFAULT_SNOOZE_MINUTES,
        )

        acquireWakeLock()
        startSound(activeSoundUri)
        if (activeVibrate) {
            startVibration()
        }
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlarmConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(AlarmConstants.NOTIFICATION_ID, notification)
        }
    }

    // ---------------------------------------------------------------------
    // Sound
    // ---------------------------------------------------------------------

    private fun startSound(soundUriString: String?) {
        val custom = soundUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        playSound(custom, allowFallback = true)
    }

    /**
     * Plays [uri] looping on the alarm stream. If a CUSTOM sound fails to load
     * (deleted file, revoked permission, unsupported format) and [allowFallback]
     * is true, it retries with the system default alarm sound — an alarm must
     * never end up silent.
     */
    private fun playSound(uri: Uri?, allowFallback: Boolean) {
        val target = uri ?: defaultAlarmUri() ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(applicationContext, target)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    if (allowFallback && uri != null) {
                        releaseMediaPlayer()
                        playSound(null, allowFallback = false)
                    }
                    true
                }
                prepareAsync()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start alarm sound", t)
            releaseMediaPlayer()
            if (allowFallback && uri != null) playSound(null, allowFallback = false)
        }
    }

    private fun defaultAlarmUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun releaseMediaPlayer() {
        mediaPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
    }

    // ---------------------------------------------------------------------
    // Vibration
    // ---------------------------------------------------------------------

    private fun startVibration() {
        val vib = obtainVibrator() ?: return
        vibrator = vib
        // Wait 0ms, buzz 600ms, pause 600ms, repeat from index 0.
        val timings = longArrayOf(0L, 600L, 600L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(timings, /* repeat = */ 0)
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(timings, /* repeat = */ 0)
        }
    }

    private fun obtainVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    // ---------------------------------------------------------------------
    // Wake lock
    // ---------------------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        lock.setReferenceCounted(false)
        // Safety timeout so a stuck alarm never drains the battery indefinitely.
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    // ---------------------------------------------------------------------
    // Dismiss / snooze
    // ---------------------------------------------------------------------

    private fun handleSnooze(intent: Intent) {
        val live = activePhase != null
        val phase = activePhase
            ?: intent.getStringExtra(AlarmConstants.EXTRA_PHASE)
                ?.let { runCatching { DawnPhase.valueOf(it) }.getOrNull() }

        if (phase != null) {
            // Prefer the live in-memory snapshot; fall back to the intent extras
            // for the rare case the service was recreated for this snooze tap.
            val label = if (live && activeLabel.isNotEmpty()) activeLabel
                else intent.getStringExtra(AlarmConstants.EXTRA_LABEL) ?: phase.title
            val vibrate = if (live) activeVibrate
                else intent.getBooleanExtra(AlarmConstants.EXTRA_VIBRATE, true)
            val soundUri = if (live) activeSoundUri
                else intent.getStringExtra(AlarmConstants.EXTRA_SOUND_URI)
            val snoozeMinutes = if (live) activeSnoozeMinutes
                else intent.getIntExtra(
                    AlarmConstants.EXTRA_SNOOZE_MINUTES,
                    AlarmConstants.DEFAULT_SNOOZE_MINUTES,
                )

            val snoozeMillis = System.currentTimeMillis() + snoozeMinutes.toLong() * 60_000L
            try {
                ServiceLocator.ensureInit(applicationContext)
                val scheduler = ServiceLocator.scheduler
                if (scheduler is AlarmManagerScheduler) {
                    // Synthetic one-shot config; scheduleAt only uses phase/label
                    // plus the ring-setting extras (not offset/daysOfWeek).
                    val config = AlarmConfig(
                        phase = phase,
                        enabled = true,
                        label = label,
                        vibrate = vibrate,
                        soundUri = soundUri,
                        snoozeMinutes = snoozeMinutes,
                    )
                    scheduler.scheduleAt(config, snoozeMillis, snooze = true)
                }
                Log.d(TAG, "Snoozed $phase for $snoozeMinutes min")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to snooze $phase", t)
            }
        }
        stopAlarm()
    }

    private fun stopAlarm() {
        releaseMediaPlayer()
        stopVibration()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isForeground = false
        stopSelf()
    }

    override fun onDestroy() {
        releaseMediaPlayer()
        stopVibration()
        releaseWakeLock()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------

    private fun buildNotification(
        phase: DawnPhase,
        label: String,
        timeMillis: Long,
        vibrate: Boolean,
        soundUri: String?,
        snoozeMinutes: Int,
    ): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            action = AlarmConstants.ACTION_FIRE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmConstants.EXTRA_PHASE, phase.name)
            putExtra(AlarmConstants.EXTRA_LABEL, label)
            putExtra(AlarmConstants.EXTRA_TIME_MILLIS, timeMillis)
        }
        val fullScreenPi = PendingIntent.getActivity(
            this,
            AlarmConstants.showRequestCode(phase),
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val dismissPi = PendingIntent.getService(
            this,
            AlarmConstants.notificationDismissRequestCode(phase),
            dismissIntent(this, phase),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Snooze action carries the ring settings so it works even if the full
        // screen activity never launched (common on Android 14) and even if the
        // service is later recreated to handle the tap.
        val snoozeServiceIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmConstants.ACTION_SNOOZE
            putExtra(AlarmConstants.EXTRA_PHASE, phase.name)
            putExtra(AlarmConstants.EXTRA_LABEL, label)
            putExtra(AlarmConstants.EXTRA_VIBRATE, vibrate)
            putExtra(AlarmConstants.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            soundUri?.let { putExtra(AlarmConstants.EXTRA_SOUND_URI, it) }
        }
        val snoozePi = PendingIntent.getService(
            this,
            AlarmConstants.notificationSnoozeRequestCode(phase),
            snoozeServiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, AlarmConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(label)
            .setContentText(phase.description)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(R.drawable.ic_alarm, "Snooze", snoozePi)
            .addAction(R.drawable.ic_alarm, "Dismiss", dismissPi)
            .build()
    }

    /** Minimal valid foreground notification for the degenerate (null-intent) path. */
    private fun buildPlaceholderNotification(): Notification =
        NotificationCompat.Builder(this, AlarmConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(getString(R.string.app_name))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "AlarmService"
        private const val WAKE_LOCK_TAG = "kukurigu:alarm"
        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 1000L

        /** Build the intent that starts (and fires) the alarm for [phase]. */
        fun startIntent(
            context: Context,
            phase: DawnPhase,
            label: String,
            timeMillis: Long,
            vibrate: Boolean,
            soundUri: String?,
            snoozeMinutes: Int,
        ): Intent = Intent(context, AlarmService::class.java).apply {
            action = AlarmConstants.ACTION_FIRE
            putExtra(AlarmConstants.EXTRA_PHASE, phase.name)
            putExtra(AlarmConstants.EXTRA_LABEL, label)
            putExtra(AlarmConstants.EXTRA_TIME_MILLIS, timeMillis)
            putExtra(AlarmConstants.EXTRA_VIBRATE, vibrate)
            putExtra(AlarmConstants.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            soundUri?.let { putExtra(AlarmConstants.EXTRA_SOUND_URI, it) }
        }

        /** Build the intent that dismisses the alarm for [phase]. */
        fun dismissIntent(context: Context, phase: DawnPhase): Intent =
            Intent(context, AlarmService::class.java).apply {
                action = AlarmConstants.ACTION_DISMISS
                putExtra(AlarmConstants.EXTRA_PHASE, phase.name)
            }

        /** Build the intent that snoozes the alarm for [phase] (used by [AlarmActivity]). */
        fun snoozeIntent(context: Context, phase: DawnPhase): Intent =
            Intent(context, AlarmService::class.java).apply {
                action = AlarmConstants.ACTION_SNOOZE
                putExtra(AlarmConstants.EXTRA_PHASE, phase.name)
            }
    }
}

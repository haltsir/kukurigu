package com.kukurigu.sunalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import com.kukurigu.sunalarm.alarm.AlarmConstants

/**
 * Application entry point. Wires up the [ServiceLocator] and creates the
 * high-importance notification channel that the firing [com.kukurigu.sunalarm.alarm.AlarmService] uses.
 */
class SunAlarmApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(AlarmConstants.CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            AlarmConstants.CHANNEL_ID,
            AlarmConstants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = AlarmConstants.CHANNEL_DESCRIPTION
            enableVibration(true)
            enableLights(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            // The service plays the alarm audio itself on the alarm stream, so we
            // suppress the channel's own sound to avoid double playback.
            setSound(null, audioAttributesForAlarm())
        }
        manager.createNotificationChannel(channel)
    }

    private fun audioAttributesForAlarm(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
}

package com.kukurigu.sunalarm

import android.content.Context
import com.kukurigu.sunalarm.alarm.AlarmManagerScheduler
import com.kukurigu.sunalarm.alarm.AlarmScheduler
import com.kukurigu.sunalarm.data.AlarmRepository
import com.kukurigu.sunalarm.data.DataStoreAlarmRepository

/**
 * Minimal hand-rolled dependency wiring (no Hilt/Dagger).
 *
 * [SunAlarmApp.onCreate] calls [init]; broadcast receivers and services that may
 * run before the [android.app.Application] is fully constructed call [ensureInit]
 * defensively. Both are safe to call repeatedly.
 */
object ServiceLocator {

    lateinit var repository: AlarmRepository
        private set

    lateinit var scheduler: AlarmScheduler
        private set

    @Volatile
    private var initialized: Boolean = false

    private val lock = Any()

    /** Build the singletons. Idempotent: a second call is a no-op. */
    fun init(context: Context) {
        ensureInit(context)
    }

    /**
     * Idempotent initialization guard. Receivers can call this before the
     * Application's [init] has run; subsequent calls return immediately.
     */
    fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val appContext = context.applicationContext
            repository = DataStoreAlarmRepository(appContext)
            scheduler = AlarmManagerScheduler(appContext)
            initialized = true
        }
    }
}

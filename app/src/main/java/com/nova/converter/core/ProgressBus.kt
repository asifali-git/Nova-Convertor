package com.nova.converter.core

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global multi-job progress bus — foreground service isi se notification % update karta hai.
 * Har job ka apna key hota hai → jitni chaho jobs ek saath chal sakti hain.
 * App minimize ho ya na ho, process service ke saath zinda rehta hai.
 */
object ProgressBus {

    data class Job(val label: String, val percent: Float)

    /** Sab jobs ka aggregate (notification ke liye) */
    data class Snapshot(val label: String, val percent: Float, val count: Int)

    private val _jobs = MutableStateFlow<Map<String, Job>>(emptyMap())
    val jobs: StateFlow<Map<String, Job>> = _jobs.asStateFlow()

    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state.asStateFlow()

    private val lastSent = HashMap<String, Float>()
    private var appContext: Context? = null
    private var serviceStarted = false

    fun bind(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    @Synchronized
    fun update(key: String, label: String, percent: Float) {
        val p = percent.coerceIn(0f, 100f)
        // throttle: 1% se kam change ignore (notification spam bachao)
        val prev = lastSent[key]
        if (prev != null && p < 100f && kotlin.math.abs(prev - p) < 1f) return
        lastSent[key] = p
        _jobs.value = _jobs.value + (key to Job(label, p))
        recompute()
        startService()
    }

    @Synchronized
    fun remove(key: String) {
        if (_jobs.value.containsKey(key)) {
            _jobs.value = _jobs.value - key
            lastSent.remove(key)
            recompute()
        }
    }

    @Synchronized
    fun clear() {
        _jobs.value = emptyMap()
        lastSent.clear()
        _state.value = null
        serviceStarted = false
    }

    private fun recompute() {
        val map = _jobs.value
        if (map.isEmpty()) {
            _state.value = null
            return
        }
        val avg = map.values.map { it.percent }.average().toFloat()
        val label = if (map.size == 1) map.values.first().label else "${map.size} jobs"
        _state.value = Snapshot(label, avg, map.size)
    }

    /** Sirf ek baar service start (background FGS-start restriction + spam dono bachao) */
    @Synchronized
    fun startService() {
        if (serviceStarted) return
        val ctx = appContext ?: return
        try {
            ctx.startForegroundService(Intent(ctx, NovaService::class.java))
            serviceStarted = true
        } catch (t: Throwable) {
            // background FGS restriction / permission issue — job chalti rahegi, notification nahi
            serviceStarted = false
        }
    }

    fun stopService() {
        val ctx = appContext ?: return
        serviceStarted = false
        ctx.stopService(Intent(ctx, NovaService::class.java))
    }

    /** NovaService band ho chuki ho to flag wapas false (warna agli job notification ke bina chalegi) */
    internal fun markServiceStopped() {
        serviceStarted = false
    }
}

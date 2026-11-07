package com.fastnode.app

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import java.util.Locale

/** Local session timer and per-app UID byte counters (estimate; not exact payload or tether-client totals). */
object SessionStats {
    private const val PREFS = "fn_session"
    private const val STARTED = "started_elapsed"
    private const val RX_BASE = "rx_base"
    private const val TX_BASE = "tx_base"

    data class Snapshot(val durationSeconds: Long, val receivedBytes: Long?, val sentBytes: Long?)

    @Synchronized
    fun begin(ctx: Context) {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(STARTED, SystemClock.elapsedRealtime())
            .putLong(RX_BASE, TrafficStats.getUidRxBytes(Process.myUid()))
            .putLong(TX_BASE, TrafficStats.getUidTxBytes(Process.myUid()))
            .apply()
    }

    @Synchronized
    fun end(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(STARTED).remove(RX_BASE).remove(TX_BASE).apply()
    }

    fun snapshot(ctx: Context): Snapshot? {
        if (!AppState.vpn) return null
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val started = p.getLong(STARTED, 0L)
        if (started <= 0L) return null
        val elapsed = ((SystemClock.elapsedRealtime() - started) / 1000L).coerceAtLeast(0L)
        val rx = delta(TrafficStats.getUidRxBytes(Process.myUid()), p.getLong(RX_BASE, -1L))
        val tx = delta(TrafficStats.getUidTxBytes(Process.myUid()), p.getLong(TX_BASE, -1L))
        return Snapshot(elapsed, rx, tx)
    }

    fun display(ctx: Context): String {
        val s = snapshot(ctx) ?: return ""
        return "مدة الاتصال: ${duration(s.durationSeconds)}\nالبيانات التقريبية للتطبيق: مستلم ${bytes(s.receivedBytes)} • مرسل ${bytes(s.sentBytes)}"
    }

    fun notificationLine(ctx: Context): String {
        val s = snapshot(ctx) ?: return "جارٍ تشغيل VPN…"
        return "${duration(s.durationSeconds)} • ↓ ${bytes(s.receivedBytes)} • ↑ ${bytes(s.sentBytes)}"
    }

    private fun delta(now: Long, base: Long): Long? =
        if (now < 0L || base < 0L || now < base) null else now - base

    private fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun bytes(value: Long?): String {
        if (value == null) return "—"
        if (value < 1024L) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var unit = -1
        do { amount /= 1024.0; unit++ } while (amount >= 1024.0 && unit < units.lastIndex)
        return String.format(Locale.US, "%.1f %s", amount, units[unit])
    }
}

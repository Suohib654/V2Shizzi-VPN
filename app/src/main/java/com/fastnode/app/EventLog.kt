package com.fastnode.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Private on-device diagnostics. Raw server links and credentials are never intentionally logged. */
object EventLog {
    private const val PREFS = "fn_diagnostics"
    private const val KEY = "events"
    private const val MAX_EVENTS = 200
    private val uriPattern = Regex("(?i)(?:vless|vmess|trojan|ss)://[^\\s]+")
    private val secretPattern = Regex("(?i)(uuid|password|passwd|token|secret|psk|private[_-]?key)(\\s*[:=]\\s*)[^,\\s}]+")

    @Synchronized
    fun add(ctx: Context, category: String, message: String) {
        try {
            val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = JSONArray(prefs.getString(KEY, "[]"))
            val items = ArrayList<JSONObject>(old.length() + 1)
            for (i in 0 until old.length()) items += old.optJSONObject(i) ?: continue
            val safeMessage = redact(message).take(600)
            items += JSONObject()
                .put("time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                .put("category", redact(category).take(32))
                .put("message", safeMessage)
            val out = JSONArray()
            items.takeLast(MAX_EVENTS).forEach(out::put)
            prefs.edit().putString(KEY, out.toString()).apply()
        } catch (_: Exception) { }
    }

    @Synchronized
    fun recent(ctx: Context, limit: Int = 100): List<String> {
        return try {
            val a = JSONArray(ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]"))
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { "${it.optString("time")}  [${it.optString("category")}] ${it.optString("message")}" }
            }.takeLast(limit.coerceIn(1, MAX_EVENTS)).reversed()
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun redact(input: String): String = input
        .replace(uriPattern, "[رابط اتصال مخفي]")
        .replace(secretPattern) { m -> "${m.groupValues[1]}${m.groupValues[2]}[مخفي]" }
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .trim()
}

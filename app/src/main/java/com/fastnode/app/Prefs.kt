package com.fastnode.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("fn", Context.MODE_PRIVATE)

    var active: String
        get() = sp.getString("active", "") ?: ""
        set(v) { sp.edit().putString("active", v).apply() }

    /** auto | root | shizuku */
    var mode: String
        get() = sp.getString("mode", "auto") ?: "auto"
        set(v) { sp.edit().putString("mode", v).apply() }

    var iface: String
        get() = sp.getString("iface", "") ?: ""
        set(v) { sp.edit().putString("iface", v).apply() }

    fun servers(): MutableList<String> {
        val a = JSONArray(sp.getString("servers", "[]"))
        return MutableList(a.length()) { a.getString(it) }
    }

    fun saveServers(l: List<String>) {
        sp.edit().putString("servers", JSONArray(l).toString()).apply()
    }

    fun addServer(link: String) {
        val l = servers()
        l.remove(link)
        l.add(0, link)
        saveServers(l)
        active = link
    }

    fun removeServer(link: String) {
        val l = servers(); l.remove(link); saveServers(l)
        if (active == link) active = l.firstOrNull() ?: ""
    }
}

object AppState {
    @Volatile var vpn = false
    @Volatile var sharing = false
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    fun listen(f: () -> Unit) { listeners.add(f) }
    fun unlisten(f: () -> Unit) { listeners.remove(f) }
    fun notifyChange() {
        Handler(Looper.getMainLooper()).post { listeners.forEach { it() } }
    }
}

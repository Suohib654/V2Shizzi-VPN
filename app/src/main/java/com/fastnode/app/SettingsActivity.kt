package com.fastnode.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var input: EditText
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private lateinit var shInfo: TextView
    private lateinit var logsView: TextView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val sv = ScrollView(this).apply { setBackgroundColor(BG) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(28))
        }
        sv.addView(col)
        col.addView(label("إعدادات التطبيق والاتصال", 20f, Color.WHITE, true))
        col.addView(btn("حول التطبيق — V2Shizzi VPN", Color.parseColor("#2A2733"), Color.WHITE) {
            startActivity(Intent(this, AboutActivity::class.java))
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        col.addView(btn("فحص تحديثات التطبيق", Color.parseColor("#2A2733"), Color.WHITE) {
            startActivity(Intent(this, UpdateActivity::class.java))
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        // ---- Card 1: V2Ray link ----
        val c1 = card()
        c1.addView(label("كود اتصال V2Ray (VLESS / VMess / Trojan / SS)", 15f, Color.WHITE, true))
        input = EditText(this).apply {
            hint = "vless://..."
            setHintTextColor(MUTED); setTextColor(Color.WHITE)
            isSingleLine = true; textSize = 13f
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            background = roundBg(Color.BLACK, dp(14), dp(1), Color.parseColor("#2A2733"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        c1.addView(input, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        val row = LinearLayout(this)
        row.addView(btn("حفظ الكود", Color.parseColor("#C455E8"), Color.BLACK) { saveLink() },
            LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(8) })
        row.addView(btn("لصق", Color.parseColor("#2A2733"), Color.WHITE) { paste() },
            LinearLayout.LayoutParams(0, dp(50), 1f))
        c1.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        info = label("", 13f, MUTED)
        c1.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("سيرفرات محفوظة:", 14f, MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(label("قياس السرعة", 14f, Color.parseColor("#C455E8"), true).apply {
            setOnClickListener { renderServers() }
        })
        c1.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c1.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(c1)

        // ---- Card 2: sharing ----
        val c2 = card()
        c2.addView(label("إعدادات مشاركة الإنترنت (روت / Shizuku)", 15f, Color.WHITE, true))
        c2.addView(label("طريقة التنفيذ", 13f, MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
        val modes = listOf("auto" to "تلقائي (روت ثم Shizuku)", "root" to "روت", "shizuku" to "Shizuku")
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes.map { it.second })
        sp.setSelection(modes.indexOfFirst { it.first == prefs.mode }.coerceAtLeast(0))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { prefs.mode = modes[pos].first }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        c2.addView(sp)
        c2.addView(label("واجهة الهوتسبوت (اتركها فارغة للكشف التلقائي)", 13f, MUTED), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        val ifaceEt = EditText(this).apply {
            setText(prefs.iface); setTextColor(Color.WHITE); isSingleLine = true
            textDirection = View.TEXT_DIRECTION_LTR; gravity = Gravity.START
            background = roundBg(Color.BLACK, dp(14), dp(1), Color.parseColor("#2A2733"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) { prefs.iface = e.toString().trim() }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        c2.addView(ifaceEt, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(4) })
        c2.addView(btn("تلقائي — مسح اسم الواجهة", Color.parseColor("#2A2733"), Color.WHITE) {
            ifaceEt.setText("")
            prefs.iface = ""
            toast("سيتم اكتشاف واجهة الهوتسبوت تلقائياً")
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) })
        shInfo = label("اضغط فحص للتحقق من الروت و Shizuku", 14f, MUTED)
        c2.addView(shInfo, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        c2.addView(btn("فحص", Color.parseColor("#2A2733"), Color.WHITE) { check() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        c2.addView(btn("فحص واجهات الهوتسبوت تلقائياً", Color.parseColor("#2A2733"), Color.WHITE) { checkInterfaces() },
            LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        col.addView(c2)

        val logCard = card()
        logCard.addView(label("سجل الاتصال ومشاركة الإنترنت", 15f, Color.WHITE, true))
        logCard.addView(label("السجل محفوظ محلياً على هذا الجهاز، بحد أقصى 200 حدث. روابط الأكواد وكلمات المرور تُحجب.", 12f, MUTED),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        logsView = label("", 11f, MUTED).apply {
            setTextIsSelectable(true)
            maxLines = 45
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundBg(Color.BLACK, dp(12), dp(1), Color.parseColor("#2A2733"))
        }
        logCard.addView(logsView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val logActions = LinearLayout(this)
        logActions.addView(btn("تحديث السجل", Color.parseColor("#2A2733"), Color.WHITE) { renderLogs() },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        logActions.addView(btn("مسح السجل", Color.parseColor("#4A2733"), Color.WHITE) {
            AlertDialog.Builder(this)
                .setTitle("مسح سجل التشخيص؟")
                .setMessage("سيتم حذف سجل الأحداث والأخطاء المحفوظ على هذا الجهاز فقط.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("مسح") { _, _ -> EventLog.clear(this); renderLogs() }
                .show()
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        logCard.addView(logActions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(logCard)

        setContentView(sv)
        renderServers()
        renderLogs()
    }

    private fun paste() {
        val cm = getSystemService(ClipboardManager::class.java)
        val t = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (t.isEmpty()) toast("الحافظة فاضية") else input.setText(t)
    }

    private fun saveLink() {
        val t = input.text.toString().trim()
        val srv = LinkParser.parse(t)
        if (srv == null) { info.text = "الكود غير صالح أو غير مدعوم"; info.setTextColor(Color.parseColor("#FF6B6B")); return }
        val security = srv.outbound.optJSONObject("streamSettings")?.optString("security")
        if (srv.outbound.optString("protocol") == "vless" && security == "none") {
            AlertDialog.Builder(this)
                .setTitle("تحذير: اتصال غير مشفّر")
                .setMessage("هذا الرابط يستخدم VLESS بدون TLS أو REALITY. بيانات الاتصال وحركة المرور قد تكون مكشوفة على الشبكة. احفظه فقط إذا كنت تثق بمصدره وتقبل هذا الخطر.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("أفهم، احفظ الرابط") { _, _ -> saveParsedLink(t, srv) }
                .show()
            return
        }
        saveParsedLink(t, srv)
    }

    private fun saveParsedLink(t: String, srv: Server) {
        EventLog.add(this, "اتصال الكود", "حُفظ الكود ${srv.name}؛ البروتوكول=${srv.outbound.optString("protocol")}")
        prefs.addServer(t)
        input.setText("")
        info.text = "تم الحفظ: ${srv.name}"; info.setTextColor(Color.parseColor("#6BE3A0"))
        if (AppState.vpn) toast("أعد تشغيل VPN لتطبيق الكود الجديد")
        renderServers()
    }

    private fun renderServers() {
        list.removeAllViews()
        for (link in prefs.servers()) {
            val srv = LinkParser.parse(link) ?: continue
            val isActive = link == prefs.active
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = roundBg(if (isActive) Color.parseColor("#1A1026") else CARD, dp(16), dp(1),
                    if (isActive) Color.parseColor("#6B3FA0") else Color.parseColor("#2A2733"))
                setOnClickListener {
                    EventLog.add(this@SettingsActivity, "اتصال الكود", "اختير الكود ${srv.name}؛ البروتوكول=${srv.outbound.optString("protocol")}")
                    prefs.active = link; renderServers()
                    if (AppState.vpn) toast("أعد تشغيل VPN لتطبيق السيرفر")
                }
                setOnLongClickListener { prefs.removeServer(link); renderServers(); true }
            }
            val ms = label("…", 14f, MUTED).apply { typeface = android.graphics.Typeface.MONOSPACE }
            row.addView(label(srv.name, 14f, Color.WHITE, isActive), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(ms)
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            Thread {
                val v = ping(srv.host, srv.port)
                runOnUiThread {
                    ms.text = if (v >= 0) "$v ms" else "—"
                    ms.setTextColor(if (v in 0..150) Color.parseColor("#6BE3A0") else if (v >= 0) Color.parseColor("#FFC857") else Color.parseColor("#FF6B6B"))
                }
            }.start()
        }
        if (list.childCount == 0) list.addView(label("لا توجد أكواد محفوظة", 13f, MUTED))
    }

    private fun checkInterfaces() {
        shInfo.text = "جارٍ فحص الواجهات التي يعلنها Android…"; shInfo.setTextColor(MUTED)
        Thread {
            val (mode, found) = HotspotSharing.detectForSharing(applicationContext)
            val candidates = if (mode == null) HotspotSharing.apIfaces(prefs.iface) else found
            EventLog.add(this, "فحص الواجهات", "الطريقة=${mode ?: "بدون صلاحية نظام"}؛ النتيجة=${candidates.ifEmpty { listOf("لا توجد") }.joinToString()}")
            runOnUiThread {
                val headline = if (candidates.isEmpty()) "لم تُكتشف واجهة. شغّل نقطة الوصول، ثم أعد الفحص." else "الواجهات المكتشفة تلقائياً: ${candidates.joinToString()}"
                shInfo.text = "${shInfo.text.toString().substringBefore("\nواجهات الهوتسبوت:")}\nواجهات الهوتسبوت: $headline"
                shInfo.setTextColor(if (candidates.isEmpty()) Color.parseColor("#FFC857") else Color.parseColor("#6BE3A0"))
                renderLogs()
            }
        }.start()
    }

    private fun renderLogs() {
        if (!::logsView.isInitialized) return
        val entries = EventLog.recent(this, 100)
        logsView.text = if (entries.isEmpty()) "لا توجد أحداث بعد" else entries.joinToString("\n")
    }

    override fun onResume() {
        super.onResume()
        if (::logsView.isInitialized) renderLogs()
    }

    private fun ping(host: String, port: Int): Int = try {
        val t = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(host, port), 3000) }
        ((System.nanoTime() - t) / 1_000_000).toInt()
    } catch (e: Exception) { -1 }

    private fun check() {
        shInfo.text = "جارٍ الفحص…"; shInfo.setTextColor(MUTED)
        Thread {
            val root = Shell.hasRoot()
            val sh = Shell.shizukuStatus()
            if (sh == 1) Shell.requestShizuku()
            EventLog.add(this, "فحص الصلاحيات", "Root=${if (root) "متاح" else "غير متاح"}; Shizuku=$sh")
            val lines = StringBuilder()
            lines.append(if (root) "روت: متاح ✓\n" else "روت: غير متاح\n")
            lines.append(when (sh) {
                2 -> "Shizuku: متصل ومفعّل (UID ${Shell.shizukuUid()}) ✓"
                1 -> "Shizuku: شغّال، اقبل طلب الصلاحية ثم اضغط فحص"
                else -> "Shizuku: غير شغّال (شغّله من تطبيق Shizuku)"
            })
            runOnUiThread {
                shInfo.text = lines
                shInfo.setTextColor(if (root || sh == 2) Color.parseColor("#6BE3A0") else Color.parseColor("#FFC857"))
            }
        }.start()
    }

    private fun toast(m: String) = runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }
}

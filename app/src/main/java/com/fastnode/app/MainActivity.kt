package com.fastnode.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var logo: LogoView
    private lateinit var chip: TextView
    private lateinit var b1: android.widget.Button
    private lateinit var b2: android.widget.Button
    private lateinit var status: TextView
    private lateinit var sessionInfo: TextView
    private val listener: () -> Unit = { refresh() }
    private val statsHandler = Handler(Looper.getMainLooper())
    private val statsTicker = object : Runnable {
        override fun run() {
            updateSessionInfo()
            statsHandler.postDelayed(this, 1_000L)
        }
    }
    @Volatile private var isResumed = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(20), dp(36), dp(20), dp(16))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        chip = label("", 14f, MUTED).apply {
            background = roundBg(Color.parseColor("#12101A"), dp(18), dp(1), Color.parseColor("#2A2733"))
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val spacer = View(this)
        val gear = label("⚙", 30f, Color.parseColor("#B7A4D4")).apply {
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        top.addView(chip)
        top.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(gear)
        root.addView(top, LinearLayout.LayoutParams(-1, -2))

        logo = LogoView(this)
        root.addView(logo, LinearLayout.LayoutParams(-1, 0, 1f))

        b1 = btn("", MAGENTA, Color.BLACK) { onVpnClick() }
        b2 = btn("", VIOLET, Color.WHITE) { onShareClick() }
        root.addView(b1, LinearLayout.LayoutParams(-1, dp(64)))
        root.addView(b2, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(14) })

        sessionInfo = label("", 11f, MUTED).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }
        root.addView(sessionInfo, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        status = label("", 12f, Color.parseColor("#4A4458"), true).apply {
            gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR
            letterSpacing = 0.12f
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        AppState.listen(listener)
        refresh()
        statsHandler.removeCallbacks(statsTicker)
        statsHandler.post(statsTicker)
        UpdateChecker.maybeCheck(this) { update ->
            runOnUiThread {
                if (isResumed && !isFinishing && !isDestroyed) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("يتوفر تحديث ${update.versionName}")
                        .setMessage(update.notes)
                        .setNegativeButton("لاحقاً", null)
                        .setPositiveButton("عرض التحديث") { _, _ ->
                            startActivity(Intent(this, UpdateActivity::class.java))
                        }
                        .show()
                }
            }
        }
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
        AppState.unlisten(listener)
        statsHandler.removeCallbacks(statsTicker)
    }

    private fun refresh() {
        val srv = LinkParser.parse(prefs.active)
        chip.text = srv?.name ?: "لا يوجد كود"
        b1.text = if (AppState.vpn) "إيقاف VPN" else "تشغيل VPN"
        b2.text = if (AppState.sharing) "إيقاف مشاركة الشبكة" else "مشاركة الشبكة"
        logo.active = AppState.vpn
        val st = when {
            AppState.sharing -> "SHARING ACTIVE"
            AppState.vpn -> "VPN ACTIVE"
            else -> "DISCONNECTED"
        }
        status.text = "V1.2.1  |  STATUS $st"
        updateSessionInfo()
    }

    private fun updateSessionInfo() {
        if (!::sessionInfo.isInitialized) return
        val summary = SessionStats.display(this)
        sessionInfo.text = summary
        sessionInfo.visibility = if (summary.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun toast(m: String) = runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }

    private fun onVpnClick() {
        if (AppState.vpn) {
            EventLog.add(this, "VPN", "طلب المستخدم إيقاف VPN")
            startService(Intent(this, FastVpnService::class.java).setAction("stop"))
            return
        }
        if (LinkParser.parse(prefs.active) == null) {
            EventLog.add(this, "اتصال الكود", "تعذر البدء: لا يوجد كود صالح مختار")
            toast("أضف كود V2Ray من الإعدادات أولاً")
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val i = VpnService.prepare(this)
        if (i != null) startActivityForResult(i, 1) else startVpn()
    }

    private fun startVpn() {
        EventLog.add(this, "VPN", "إرسال طلب تشغيل خدمة VPN")
        startForegroundService(Intent(this, FastVpnService::class.java).setAction("start"))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK) startVpn()
        else if (req == 1) EventLog.add(this, "VPN", "رفض المستخدم إذن VPN")
        if (req == 2 && AppState.vpn && !AppState.sharing) {
            checkHotspotAndShare(openSettingsIfOff = false)
        }
    }

    private fun onShareClick() {
        if (AppState.sharing) {
            EventLog.add(this, "مشاركة الإنترنت", "طلب المستخدم إيقاف المشاركة")
            Thread {
                try {
                    HotspotSharing.disable()
                    EventLog.add(this@MainActivity, "مشاركة الإنترنت", "تم طلب تنظيف قواعد المشاركة وإيقافها")
                } catch (e: Throwable) {
                    EventLog.add(this@MainActivity, "خطأ إيقاف المشاركة", e.message ?: e.javaClass.simpleName)
                } finally {
                    AppState.sharing = false
                    AppState.notifyChange()
                }
            }.start()
            return
        }
        if (!AppState.vpn) {
            EventLog.add(this, "مشاركة الإنترنت", "فشل البدء: VPN غير نشط")
            toast("شغّل VPN أولاً"); return
        }
        EventLog.add(this, "مشاركة الإنترنت", "بدأ فحص واجهة الهوتسبوت والصلاحيات")
        checkHotspotAndShare(openSettingsIfOff = true)
    }

    private fun checkHotspotAndShare(openSettingsIfOff: Boolean) {
        Thread {
            try {
                val (mode, ifaces) = HotspotSharing.detectForSharing(applicationContext)
                if (mode == null) {
                    EventLog.add(this@MainActivity, "خطأ مشاركة", "لا يوجد منفذ أو صلاحية Root/Shizuku")
                    toast("فعّل الروت أو شغّل Shizuku وامنح V2Shizzi VPN الصلاحية أولاً")
                    return@Thread
                }
                if (ifaces.isEmpty()) {
                    EventLog.add(this@MainActivity, "خطأ مشاركة", "لم تُكتشف واجهة هوتسبوت نشطة عبر $mode")
                    runOnUiThread {
                        if (openSettingsIfOff) openHotspotSettings()
                        else toast("لم تُفعّل نقطة الوصول. شغّلها من الإعدادات ثم ارجع إلى V2Shizzi VPN.")
                    }
                    return@Thread
                }
                val err = HotspotSharing.enable(applicationContext)
                if (err == null) {
                    EventLog.add(this@MainActivity, "مشاركة الإنترنت", "نجاح تهيئة المشاركة عبر $mode؛ الواجهات=${ifaces.joinToString()}")
                    AppState.sharing = true
                    toast("تم تفعيل مشاركة نقطة الاتصال عبر " +
                        (if (HotspotSharing.mode == "root") "الروت" else "Shizuku"))
                } else {
                    EventLog.add(this@MainActivity, "خطأ مشاركة", err)
                    toast(err)
                }
                AppState.notifyChange()
            } catch (e: Throwable) {
                EventLog.add(this@MainActivity, "خطأ مشاركة غير متوقع", e.message ?: e.javaClass.simpleName)
                toast("تعذّر تشغيل مشاركة الشبكة بأمان: ${e.message ?: "خطأ غير معروف"}")
                AppState.sharing = false
                AppState.notifyChange()
            }
        }.start()
    }

    private fun openHotspotSettings() {
        toast("افتح نقطة اتصال Wi‑Fi من شاشة النظام؛ سيعود التطبيق ويفحصها تلقائياً")
        val actions = listOf(
            "android.settings.TETHER_SETTINGS",
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_WIFI_SETTINGS
        )
        for (action in actions) {
            try {
                startActivityForResult(Intent(action), 2)
                return
            } catch (_: Exception) { }
        }
        toast("تعذر فتح إعدادات الهوتسبوت تلقائياً")
    }
}

package com.fastnode.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.widget.Toast
import hev.htproxy.TProxyService
import java.io.File

class FastVpnService : VpnService() {
    private var pfd: ParcelFileDescriptor? = null
    private val notifyHandler = Handler(Looper.getMainLooper())
    private val refreshNotification = object : Runnable {
        override fun run() {
            if (!AppState.vpn) return
            try { getSystemService(NotificationManager::class.java).notify(1, notification()) } catch (_: Throwable) { }
            notifyHandler.postDelayed(this, 5_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notification())
        if (intent?.action == "stop") {
            Thread { stopAll() }.start()
        } else {
            Thread { startAll() }.start()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("vpn", "V2Shizzi VPN", NotificationManager.IMPORTANCE_LOW))
        val openIntent = PendingIntent.getActivity(
            this, 10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 11,
            Intent(this, FastVpnService::class.java).setAction("stop"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "vpn")
            .setContentTitle("V2Shizzi VPN")
            .setContentText(SessionStats.notificationLine(this))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_view, "فتح التطبيق", openIntent)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف VPN", stopIntent)
            .build()
    }

    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    private fun startAll() {
        var serverLabel = "غير معروف"
        try {
            if (!AppIntegrity.releaseIsIntact(this)) {
                throw SecurityException("فشل فحص سلامة التطبيق أو شهادة التوقيع")
            }
            val srv = LinkParser.parse(Prefs(this).active)
                ?: throw Exception("الكود غير صالح أو غير مدعوم")
            serverLabel = srv.name
            EventLog.add(this, "اتصال الكود", "بدء اتصال ${srv.name}؛ البروتوكول=${srv.outbound.optString("protocol")}")
            XrayCore.start(this, ConfigBuilder.build(srv))

            val p = Builder()
                .setSession("V2Shizzi VPN")
                .setMtu(1500)
                .addAddress("26.26.26.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addDisallowedApplication(packageName)
                .establish() ?: throw Exception("تعذّر إنشاء النفق، تأكد من صلاحية VPN")
            pfd = p

            val cfg = File(filesDir, "hev.yml")
            cfg.writeText(
                """
                tunnel:
                  mtu: 1500
                socks5:
                  port: ${ConfigBuilder.SOCKS_PORT}
                  address: 127.0.0.1
                  udp: 'udp'
                misc:
                  task-stack-size: 81920
                  tcp-buffer-size: 65536
                  connect-timeout: 5000
                  read-write-timeout: 60000
                  log-level: warn
                """.trimIndent()
            )
            if (!TProxyService.TProxyStartService(cfg.absolutePath, p.fd)) {
                throw Exception("تعذّر تشغيل محرك النفق (hev)")
            }
            SessionStats.begin(this)
            AppState.vpn = true
            EventLog.add(this, "اتصال الكود", "بدأ نفق VPN بنجاح للكود $serverLabel")
            AppState.notifyChange()
            notifyHandler.removeCallbacks(refreshNotification)
            notifyHandler.post(refreshNotification)
        } catch (e: Throwable) {
            EventLog.add(this, "فشل اتصال الكود", "فشل اتصال $serverLabel: ${e.message ?: e.javaClass.simpleName}")
            toast("خطأ: ${e.message}")
            stopAll()
        }
    }

    private fun stopAll() {
        if (AppState.vpn || AppState.sharing) EventLog.add(this, "VPN", "إيقاف خدمة VPN ومشاركة الشبكة")
        notifyHandler.removeCallbacks(refreshNotification)
        SessionStats.end(this)
        try { HotspotSharing.disable() } catch (e: Throwable) {
            EventLog.add(this, "خطأ تنظيف المشاركة", e.message ?: e.javaClass.simpleName)
        }
        try { TProxyService.TProxyStopService() } catch (_: Throwable) { }
        XrayCore.stop()
        try { pfd?.close() } catch (_: Throwable) { }
        pfd = null
        AppState.vpn = false
        AppState.sharing = false
        AppState.notifyChange()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        Thread { stopAll() }.start()
    }
}

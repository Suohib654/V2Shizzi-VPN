package com.fastnode.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File

class UpdateActivity : Activity() {
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
    private lateinit var action: android.widget.Button
    private var release: UpdateChecker.ReleaseInfo? = null
    private var pendingApk: File? = null
    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(BG)
        }
        column.addView(label("تحديث V2Shizzi VPN", 22f, android.graphics.Color.WHITE, true))
        message = label("جارٍ فحص الإصدارات المنشورة…", 15f, MUTED).apply {
            gravity = Gravity.CENTER
        }
        column.addView(message, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        column.addView(progress, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(16) })
        action = btn("فحص التحديثات", VIOLET, android.graphics.Color.WHITE) { onAction() }
        column.addView(action, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(18) })
        column.addView(btn("إغلاق", android.graphics.Color.parseColor("#2A2733"), android.graphics.Color.WHITE) { finish() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        setContentView(column)
        check()
    }

    private fun onAction() {
        if (downloading) return
        val info = release
        if (info == null) check() else download(info)
    }

    private fun check() {
        release = null
        action.setOnClickListener { onAction() }
        message.text = "جارٍ فحص تحديثات Cloudflare R2…"
        action.text = "جارٍ الفحص"
        action.isEnabled = false
        Thread {
            try {
                val update = UpdateChecker.findUpdate(this)
                runOnUiThread {
                    action.isEnabled = true
                    if (update == null) {
                        message.text = "التطبيق محدّث إلى آخر إصدار منشور."
                        action.text = "إعادة الفحص"
                    } else {
                        release = update
                        message.text = "يتوفر الإصدار ${update.versionName}\n\n${update.notes}\n\nسيتم التحقق من SHA-256 قبل فتح مثبت Android."
                        action.text = "تنزيل التحديث"
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    action.isEnabled = true
                    message.text = e.message ?: "تعذّر فحص التحديثات. تحقق من الإنترنت وأن المستودع عام."
                    action.text = "إعادة المحاولة"
                }
            }
        }.start()
    }

    private fun download(info: UpdateChecker.ReleaseInfo) {
        downloading = true
        action.isEnabled = false
        progress.visibility = View.VISIBLE
        progress.progress = 0
        message.text = "جارٍ تنزيل الإصدار ${info.versionName}…"
        Thread {
            try {
                val apk = UpdateChecker.download(this, info) { value ->
                    runOnUiThread { progress.progress = value }
                }
                runOnUiThread {
                    downloading = false
                    action.isEnabled = true
                    message.text = "اكتمل التنزيل والتحقق من سلامة الملف. يلزم تأكيد التثبيت من Android."
                    action.text = "تثبيت التحديث"
                    action.setOnClickListener { installApk(apk) }
                    installApk(apk)
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    downloading = false
                    action.isEnabled = true
                    message.text = e.message ?: "فشل تنزيل التحديث"
                    action.text = "إعادة المحاولة"
                }
            }
        }.start()
    }

    private fun installApk(apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingApk = apk
            AlertDialog.Builder(this)
                .setTitle("السماح بتثبيت التحديث")
                .setMessage("فعّل السماح من هذا المصدر، ثم ارجع إلى التطبيق. سيطلب Android تأكيد تثبيت التحديث.")
                .setNegativeButton("لاحقاً", null)
                .setPositiveButton("فتح الإعدادات") { _, _ ->
                    try {
                        @Suppress("DEPRECATION")
                        startActivityForResult(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")), REQUEST_INSTALL_PERMISSION)
                    } catch (_: Throwable) {
                        message.text = "افتح إعدادات Android واسمح لهذا التطبيق بتثبيت تطبيقات غير معروفة."
                    }
                }.show()
            return
        }
        try {
            val content = FileProvider.getUriForFile(this, "$packageName.updateprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(content, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Throwable) {
            message.text = "تعذّر فتح مثبت Android: ${e.message ?: "خطأ غير معروف"}"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            val apk = pendingApk
            pendingApk = null
            if (apk != null && Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) installApk(apk)
            else message.text = "لم يُمنح الإذن. يمكنك الرجوع والمحاولة بعد تفعيله من إعدادات Android."
        }
    }

    companion object { private const val REQUEST_INSTALL_PERMISSION = 709 }
}

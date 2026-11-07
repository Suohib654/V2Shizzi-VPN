package com.fastnode.app

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object UpdateChecker {
    // Public Cloudflare R2 custom domain. Never embed R2 credentials in the Android app.
    private const val UPDATE_BASE_URL = "https://updates.mrsuohib.online"
    private const val MANIFEST_URL = "$UPDATE_BASE_URL/update.json"
    private val TRUSTED_UPDATE_HOSTS = setOf("updates.mrsuohib.online")
    private const val PREFS = "v2shizzi_updates"
    private const val CHECKED_AT = "checked_at"
    private const val PROMPTED_CODE = "prompted_code"
    private const val CHECK_INTERVAL = 12L * 60L * 60L * 1000L

    data class ReleaseInfo(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val sha256: String
    )

    fun findUpdate(context: Context): ReleaseInfo? {
        // Read the public manifest from R2 and only accept APKs hosted on our HTTPS domain.
        val manifest = getJson(MANIFEST_URL)
        val remoteCode = manifest.optInt("versionCode", -1)
        val remoteName = manifest.optString("versionName", "")
        if (remoteCode < 1 || remoteName.isBlank()) throw IllegalStateException("بيانات الإصدار غير مكتملة")
        val current = currentVersionCode(context)
        if (remoteCode <= current) return null

        val variants = manifest.optJSONObject("assets") ?: throw IllegalStateException("لا توجد ملفات APK للإصدار")
        var chosen: JSONObject? = null
        for (abi in Build.SUPPORTED_ABIS) {
            val candidate = variants.optJSONObject(abi)
            if (candidate != null) { chosen = candidate; break }
        }
        if (chosen == null) chosen = variants.optJSONObject("universal")
            ?: throw IllegalStateException("لا يوجد APK متوافق مع هذا الجهاز")
        val apkUrl = requireR2Url(chosen.optString("url"))
        val hash = chosen.optString("sha256").lowercase()
        if (!hash.matches(Regex("[a-f0-9]{64}"))) throw IllegalStateException("بصمة SHA-256 غير صالحة")
        return ReleaseInfo(remoteCode, remoteName, manifest.optString("notes", "إصدار جديد من V2Shizzi VPN"), apkUrl, hash)
    }

    fun maybeCheck(context: Context, callback: (ReleaseInfo) -> Unit) {
        val app = context.applicationContext
        val preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = preferences.getLong(CHECKED_AT, 0L)
        if (last > 0L && now - last < CHECK_INTERVAL) return
        preferences.edit().putLong(CHECKED_AT, now).apply()
        Thread {
            try {
                val update = findUpdate(app) ?: return@Thread
                val alreadyPrompted = preferences.getInt(PROMPTED_CODE, 0)
                if (alreadyPrompted >= update.versionCode) return@Thread
                preferences.edit().putInt(PROMPTED_CODE, update.versionCode).apply()
                callback(update)
            } catch (_: Throwable) {
                // Automatic checks are quiet when offline, private, or not yet published.
            }
        }.start()
    }

    fun download(context: Context, release: ReleaseInfo, progress: (Int) -> Unit): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val temp = File(directory, "update.apk.part")
        val finalFile = File(directory, "V2Shizzi-${release.versionName}.apk")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "V2Shizzi-VPN-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("فشل تنزيل التحديث (HTTP $code)")
            val digest = MessageDigest.getInstance("SHA-256")
            val total = connection.contentLengthLong
            var received = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > 250L * 1024L * 1024L) throw IllegalStateException("حجم ملف التحديث أكبر من الحد المسموح")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        if (total > 0L) progress(((received * 100L) / total).toInt().coerceIn(0, 100))
                    }
                    output.fd.sync()
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(release.sha256, ignoreCase = true)) {
                temp.delete()
                throw SecurityException("فشل التحقق من سلامة ملف التحديث (SHA-256)")
            }
            if (finalFile.exists()) finalFile.delete()
            if (!temp.renameTo(finalFile)) throw IllegalStateException("تعذّر حفظ ملف التحديث")
            return finalFile
        } finally {
            connection.disconnect()
        }
    }

    private fun currentVersionCode(context: Context): Int = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (_: Throwable) { 0 }

    private fun requireR2Url(value: String): String {
        val uri = android.net.Uri.parse(value)
        if (uri.scheme != "https" || uri.host !in TRUSTED_UPDATE_HOSTS || uri.userInfo != null)
            throw SecurityException("رابط التحديث ليس من نطاق Cloudflare R2 الموثوق")
        return value
    }

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "V2Shizzi-VPN-Android")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("تعذّر الوصول إلى خادم التحديثات (HTTP $code). تحقق من إعداد نطاق Cloudflare R2 ونشر update.json.")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (text.length > 1_000_000) throw IllegalStateException("بيانات التحديث أكبر من المتوقع")
            return JSONObject(text)
        } finally { connection.disconnect() }
    }

    fun markPrompted(context: Context, code: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(PROMPTED_CODE, code).apply()
    }
}

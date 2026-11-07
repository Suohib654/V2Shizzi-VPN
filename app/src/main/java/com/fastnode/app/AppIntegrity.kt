package com.fastnode.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.security.MessageDigest

/** A deterrent against casually repackaged APKs; not a substitute for server-side attestation. */
object AppIntegrity {
    // SHA-256 certificate fingerprint of the V2Shizzi private release key.
    private const val RELEASE_CERT_SHA256 = "9a0acfca7ad2c9275a17282243c93f4085d75a24b0fa3436a97b230f158fc692"

    fun releaseIsIntact(context: Context): Boolean {
        return try {
            val appInfo = context.applicationInfo
            // Allow developer builds made from the editable source tree.
            if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) return true
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return false

            val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            val signatures = packageInfo.signingInfo?.apkContentsSigners ?: return false
            if (signatures.size != 1) return false
            val actual = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())
                .joinToString("") { "%02x".format(it) }
            actual.equals(RELEASE_CERT_SHA256, ignoreCase = true)
        } catch (_: Throwable) {
            false
        }
    }
}

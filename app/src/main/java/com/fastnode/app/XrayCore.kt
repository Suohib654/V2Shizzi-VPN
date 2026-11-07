package com.fastnode.app

import android.content.Context
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Wraps libv2ray (Xray-core) through reflection so small API differences between releases don't break the build. */
object XrayCore {
    private var ctrl: Any? = null

    fun start(ctx: Context, config: String) {
        stop()
        try {
            val lib = Class.forName("libv2ray.Libv2ray")
            try {
                lib.getMethod("initCoreEnv", String::class.java, String::class.java)
                    .invoke(null, ctx.filesDir.absolutePath, "")
            } catch (_: NoSuchMethodException) { }
            val hcls = Class.forName("libv2ray.CoreCallbackHandler")
            val handler = Proxy.newProxyInstance(hcls.classLoader, arrayOf(hcls)) { _, m, _ ->
                when (m.returnType) {
                    java.lang.Long.TYPE -> 0L
                    java.lang.Integer.TYPE -> 0
                    java.lang.Boolean.TYPE -> false
                    else -> null
                }
            }
            val c = lib.getMethod("newCoreController", hcls).invoke(null, handler)
                ?: throw IllegalStateException("newCoreController returned null")
            val m = c.javaClass.methods.firstOrNull { it.name == "startLoop" }
                ?: throw IllegalStateException("startLoop not found in libv2ray")
            if (m.parameterTypes.size == 2) m.invoke(c, config, 0) else m.invoke(c, config)
            ctrl = c
        } catch (e: InvocationTargetException) {
            throw Exception(e.targetException?.message ?: "Xray failed")
        }
    }

    fun stop() {
        val c = ctrl ?: return
        try { c.javaClass.getMethod("stopLoop").invoke(c) } catch (_: Throwable) { }
        ctrl = null
    }
}

package com.fastnode.app

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

object Shell {

    fun run(mode: String, cmd: String): Pair<Int, String> {
        var p: Process? = null
        val out = StringBuilder()
        return try {
            p = if (mode == "root") Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)) else shizukuProc(cmd)
            val proc = p ?: return -1 to "exec failed"
            val readers = listOf(proc.inputStream, proc.errorStream).map { st ->
                Thread {
                    try {
                        st.bufferedReader().useLines { lines ->
                            lines.forEach { line -> synchronized(out) { out.append(line).append('\n') } }
                        }
                    } catch (_: Exception) { }
                }.apply { isDaemon = true; start() }
            }

            // ShizukuRemoteProcess.waitFor(timeout, unit) can call exitValue() before
            // the remote process exits and throw "process hasn't exited". Poll safely.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var exit: Int? = null
            while (System.nanoTime() < deadline && exit == null) {
                try {
                    exit = proc.exitValue()
                } catch (_: IllegalThreadStateException) {
                    Thread.sleep(50)
                } catch (e: IllegalArgumentException) {
                    if (e.message?.contains("hasn't exited", ignoreCase = true) == true ||
                        e.message?.contains("has not exited", ignoreCase = true) == true) {
                        Thread.sleep(50)
                    } else throw e
                }
            }
            if (exit == null) {
                try { proc.destroy() } catch (_: Throwable) { }
                readers.forEach { try { it.join(300) } catch (_: InterruptedException) { Thread.currentThread().interrupt() } }
                -1 to "timeout"
            } else {
                readers.forEach { try { it.join(500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() } }
                exit to synchronized(out) { out.toString().trim() }
            }
        } catch (e: Throwable) {
            try { p?.destroy() } catch (_: Throwable) { }
            -1 to (e.message ?: "exec failed")
        }
    }

    fun hasRoot(): Boolean {
        val (c, o) = run("root", "id")
        return c == 0 && o.contains("uid=0")
    }

    /** 0 = not running, 1 = running but no permission, 2 = ready */
    fun shizukuStatus(): Int = try {
        if (!Shizuku.pingBinder()) 0
        else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) 1
        else 2
    } catch (e: Throwable) { 0 }

    fun shizukuUid(): Int = try { Shizuku.getUid() } catch (e: Throwable) { -1 }

    fun requestShizuku() { try { Shizuku.requestPermission(100) } catch (_: Throwable) { } }

    private fun shizukuProc(cmd: String): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        return m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
    }
}

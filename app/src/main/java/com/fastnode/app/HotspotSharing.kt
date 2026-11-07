package com.fastnode.app

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface

/** Routes hotspot clients into the VPN TUN. Kernel/network permissions vary by Android build. */
object HotspotSharing {
    private const val TABLE = 200
    private const val PRIO = 5000
    private const val FORWARD_REQUESTER = "fastnode_vpn_share"
    private val IFACE_NAME = Regex("^[A-Za-z0-9_.-]{1,15}$")
    private val ADDR_LINE = Regex("^\\d+:\\s+([^@\\s]+)(?:@\\S+)?\\s+inet\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})/(\\d+)")
    private val TETHER_LIST = Regex("(?i)(?:mTetheredIfaces|mTetheredInterfaces|tetheredIfaces|tethered interfaces|tethered_interfaces)\\s*[:=]\\s*[\\{\\[(]([^}\\])]*?)[}\\])]")
    private val TETHER_IFACE = Regex("(?i)\\b(?:iface|ifname|interface)\\s*[=:]\\s*([A-Za-z0-9_.-]{1,15})")
    private val SOFTAP_IFACE = Regex("(?i)(?:mSoftApInterfaceName|softApInterfaceName|softApIface|softApInterface)\\s*[=:]\\s*([A-Za-z0-9_.-]{1,15})")
    private var aps: List<String> = emptyList()
    private var tun = ""
    private var ownsShizukuForwarding = false
    private data class DetectionCache(val mode: String, val preference: String, val names: List<String>, val at: Long)
    @Volatile private var detectionCache: DetectionCache? = null
    var mode = ""
        private set

    fun resolveMode(pref: String): String? = when (pref) {
        "root" -> if (Shell.hasRoot()) "root" else null
        "shizuku" -> if (Shell.shizukuStatus() == 2) "shizuku" else null
        else -> if (Shell.hasRoot()) "root" else if (Shell.shizukuStatus() == 2) "shizuku" else null
    }

    fun tunIface(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .firstOrNull { it.isUp && it.name.startsWith("tun") }?.name
    } catch (_: Exception) { null }

    private fun isPrivateGateway(ip: String): Boolean {
        val octets = ip.split('.').map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 } || octets[3] != 1) return false
        return octets[0] == 10 ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 172 && octets[1] in 16..31)
    }

    private fun parseTetherDump(text: String): Set<String> {
        val found = linkedSetOf<String>()
        TETHER_LIST.findAll(text).forEach { match ->
            match.groupValues[1].split(Regex("[,\\s]+")).forEach { raw ->
                val iface = raw.substringBefore('=').trim().trim(',', '{', '}', '[', ']')
                if (IFACE_NAME.matches(iface) && iface !in setOf("none", "null", "empty")) found.add(iface)
            }
        }
        text.lineSequence().forEach { line ->
            if (line.contains("TETHERED", ignoreCase = true)) {
                TETHER_IFACE.findAll(line).forEach { match ->
                    val iface = match.groupValues[1]
                    if (IFACE_NAME.matches(iface)) found.add(iface)
                }
            }
        }
        if (text.contains("softap", ignoreCase = true) || text.contains("soft ap", ignoreCase = true)) {
            SOFTAP_IFACE.findAll(text).forEach { match ->
                val iface = match.groupValues[1]
                if (IFACE_NAME.matches(iface)) found.add(iface)
            }
        }
        return found
    }

    fun apIfaces(pref: String, mode: String = ""): List<String> {
        if (pref.isNotBlank()) return pref.split(',', ' ').map(String::trim)
            .filter { it.isNotEmpty() && IFACE_NAME.matches(it) }.distinct()
        val found = linkedSetOf<String>()
        val activeTun = tunIface()
        try {
            NetworkInterface.getNetworkInterfaces().toList().filter { ni ->
                ni.isUp && !ni.isLoopback && ni.name != activeTun &&
                    ni.interfaceAddresses.any { a ->
                        val ad = a.address
                        ad is Inet4Address && ad.isSiteLocalAddress && (ad.address[3].toInt() and 0xff) == 1
                    }
            }.mapTo(found) { it.name }
        } catch (_: Exception) { }

        // Use Android's live tethering list first: interface names vary by OEM and Android release.
        if (mode.isNotEmpty()) {
            val (_, tetherDump) = Shell.run(mode, "dumpsys tethering 2>/dev/null")
            found.addAll(parseTetherDump(tetherDump).filter { it != activeTun })
            // Android 10/OEM fallback: older connectivity dumps may expose tethered interfaces instead.
            if (parseTetherDump(tetherDump).isEmpty()) {
                val (_, connectivityDump) = Shell.run(mode, "dumpsys connectivity 2>/dev/null")
                val connectivityIfaces = parseTetherDump(connectivityDump)
                found.addAll(connectivityIfaces.filter { it != activeTun })
                if (connectivityIfaces.isEmpty()) {
                    val (_, wifiDump) = Shell.run(mode, "dumpsys wifi 2>/dev/null")
                    found.addAll(parseTetherDump(wifiDump).filter { it != activeTun })
                }
            }
            // Last fallback: infer any dynamically named interface with a private .1 gateway address.
            val (code, output) = Shell.run(mode, "ip -o -4 addr show 2>/dev/null")
            if (code == 0) {
                output.lineSequence().forEach { line ->
                    val match = ADDR_LINE.find(line) ?: return@forEach
                    val iface = match.groupValues[1]
                    val ip = match.groupValues[2]
                    if (iface != activeTun && IFACE_NAME.matches(iface) && isPrivateGateway(ip)) found.add(iface)
                }
            }
        }
        return found.toList()
    }

    fun detectForSharing(ctx: Context): Pair<String?, List<String>> {
        val prefs = Prefs(ctx)
        val selectedMode = resolveMode(prefs.mode) ?: return null to emptyList()
        val found = apIfaces(prefs.iface, selectedMode)
        detectionCache = DetectionCache(selectedMode, prefs.iface, found, System.currentTimeMillis())
        EventLog.add(ctx, "كشف الهوتسبوت", "طريقة التنفيذ=$selectedMode؛ الواجهات=${found.ifEmpty { listOf("لا توجد") }.joinToString()}; API=${android.os.Build.VERSION.SDK_INT}")
        return selectedMode to found
    }

    private fun forwardingValue(m: String): String? {
        val (code, output) = Shell.run(m, "cat /proc/sys/net/ipv4/ip_forward 2>/dev/null")
        if (code != 0) return null
        return output.trim().lineSequence().lastOrNull()?.takeIf { it == "0" || it == "1" }
    }

    @Synchronized
    fun enable(ctx: Context): String? {
        val p = Prefs(ctx)
        val m = resolveMode(p.mode)
            ?: return "لا يوجد روت ولا Shizuku مفعّل. افتح الإعدادات واضغط فحص."
        val t = tunIface() ?: return "نفق VPN غير موجود، شغّل VPN أولاً."
        val snapshot = detectionCache
        val a = if (snapshot != null && snapshot.mode == m && snapshot.preference == p.iface &&
            System.currentTimeMillis() - snapshot.at in 0..8_000) snapshot.names else apIfaces(p.iface, m)
        if (a.isEmpty()) return "ما قدرت أتعرف على واجهة الهوتسبوت. شغّل نقطة الاتصال واترك خانة الواجهة فارغة ثم أعد المحاولة."

        // Remove this app's previous rules/request first; never disable Android's own forwarding request.
        cleanup(m, a, t)
        if (m == "shizuku") {
            // A previous process may have exited before releasing its uniquely named netd request.
            Shell.run(m, "ndc ipfwd disable $FORWARD_REQUESTER 2>/dev/null")
        }

        val steps = mutableListOf<Pair<String, Boolean>>() // command, critical
        steps += "echo 0 > /proc/sys/net/ipv4/conf/$t/rp_filter" to false
        steps += "ip route replace default dev $t table $TABLE" to true
        steps += "iptables -w -t nat -I POSTROUTING -o $t -j MASQUERADE" to true
        for (ap in a) {
            steps += "ip rule add iif $ap lookup $TABLE priority $PRIO" to true
            steps += "iptables -w -I FORWARD 1 -i $ap -o $t -j ACCEPT" to true
            steps += "iptables -w -I FORWARD 1 -i $t -o $ap -j ACCEPT" to true
            steps += "iptables -w -t nat -I PREROUTING -i $ap -p udp --dport 53 -j DNAT --to-destination 1.1.1.1:53" to false
            steps += "iptables -w -t nat -I PREROUTING -i $ap -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53" to false
        }
        for ((cmd, critical) in steps) {
            val (code, out) = Shell.run(m, cmd)
            if (code != 0 && critical) {
                cleanup(m, a, t)
                val hint = if (m == "shizuku") "\nرفض Android هذا الأمر عبر Shizuku؛ يلزم الروت أو دعم نظام الجهاز." else ""
                return "فشل إعداد مشاركة الشبكة: $cmd\n${out.take(240)}$hint"
            }
        }

        val beforeForwarding = forwardingValue(m)
        if (beforeForwarding != "1") {
            if (m == "root") {
                val (code, out) = Shell.run(m,
                    "sysctl -w net.ipv4.ip_forward=1 >/dev/null 2>&1 || echo 1 > /proc/sys/net/ipv4/ip_forward")
                if (code != 0 || forwardingValue(m) != "1") {
                    cleanup(m, a, t)
                    return "الروت لم يستطع تفعيل IP forwarding على هذا النظام. ${out.take(180)}"
                }
            } else {
                // Ask Android netd through its supported requester interface instead of writing a protected sysctl.
                val (ndcCode, ndcOutput) = Shell.run(m, "ndc ipfwd enable $FORWARD_REQUESTER")
                if (forwardingValue(m) == "1") {
                    ownsShizukuForwarding = true
                } else {
                    cleanup(m, a, t)
                    val detail = ndcOutput.ifBlank { "رمز خروج الأمر: $ndcCode" }.take(180)
                    return "Android لم يفعّل IP forwarding. جُرّبت واجهة netd عبر Shizuku لكنها رُفضت أو غير مدعومة ($detail).\nلا توجد طريقة آمنة لتجاوز قيد النظام من Shizuku؛ اختر الروت أو استخدم نظاماً يسمح بتمرير VPN إلى الهوتسبوت."
                }
            }
        }

        aps = a
        tun = t
        mode = m
        return null
    }

    @Synchronized
    fun disable() {
        if (mode.isEmpty()) return
        cleanup(mode, aps, tun)
        mode = ""; aps = emptyList(); tun = ""
    }

    private fun cleanup(m: String, a: List<String>, t: String) {
        val cmds = mutableListOf<String>()
        for (ap in a) cmds += "ip rule del iif $ap lookup $TABLE priority $PRIO 2>/dev/null"
        cmds += "ip route del default dev $t table $TABLE 2>/dev/null"
        cmds += "iptables -w -t nat -D POSTROUTING -o $t -j MASQUERADE 2>/dev/null"
        for (ap in a) {
            cmds += "iptables -w -D FORWARD -i $ap -o $t -j ACCEPT 2>/dev/null"
            cmds += "iptables -w -D FORWARD -i $t -o $ap -j ACCEPT 2>/dev/null"
            cmds += "iptables -w -t nat -D PREROUTING -i $ap -p udp --dport 53 -j DNAT --to-destination 1.1.1.1:53 2>/dev/null"
            cmds += "iptables -w -t nat -D PREROUTING -i $ap -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53 2>/dev/null"
        }
        if (m == "shizuku" && ownsShizukuForwarding) {
            cmds += "ndc ipfwd disable $FORWARD_REQUESTER 2>/dev/null"
            ownsShizukuForwarding = false
        }
        Shell.run(m, cmds.joinToString("; "))
    }
}

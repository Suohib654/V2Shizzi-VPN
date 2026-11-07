package com.fastnode.app

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

data class Server(val name: String, val host: String, val port: Int, val outbound: JSONObject)

object LinkParser {

    fun parse(raw: String): Server? = try {
        val s = raw.trim()
        when {
            s.startsWith("vless://") -> vless(s)
            s.startsWith("vmess://") -> vmess(s)
            s.startsWith("trojan://") -> trojan(s)
            s.startsWith("ss://") -> shadowsocks(s)
            else -> null
        }
    } catch (e: Exception) { null }

    private fun b64(s: String): String {
        var t = s.replace('-', '+').replace('_', '/').trim()
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT))
    }

    private fun stream(q: (String) -> String?, host: String): JSONObject {
        var net = q("type") ?: "tcp"
        val sec = q("security") ?: "none"
        val hostHdr = q("host")
        val path = q("path")
        val st = JSONObject()
        when (net) {
            "ws" -> {
                val ws = JSONObject().put("path", path ?: "/")
                if (hostHdr != null) ws.put("headers", JSONObject().put("Host", hostHdr))
                st.put("wsSettings", ws)
            }
            "grpc" -> st.put("grpcSettings", JSONObject()
                .put("serviceName", q("serviceName") ?: path ?: "")
                .put("multiMode", q("mode") == "multi"))
            "httpupgrade" -> st.put("httpupgradeSettings", JSONObject()
                .put("path", path ?: "/").put("host", hostHdr ?: host))
            "xhttp", "splithttp" -> {
                net = "xhttp"
                st.put("xhttpSettings", JSONObject()
                    .put("path", path ?: "/").put("host", hostHdr ?: host).put("mode", q("mode") ?: "auto"))
            }
            "h2", "http" -> {
                net = "http"
                st.put("httpSettings", JSONObject()
                    .put("path", path ?: "/").put("host", JSONArray().put(hostHdr ?: host)))
            }
            "tcp" -> if (q("headerType") == "http") {
                st.put("tcpSettings", JSONObject().put("header", JSONObject()
                    .put("type", "http")
                    .put("request", JSONObject()
                        .put("path", JSONArray().put(path ?: "/"))
                        .put("headers", JSONObject().put("Host", JSONArray().put(hostHdr ?: host))))))
            }
        }
        st.put("network", net).put("security", sec)
        when (sec) {
            "tls" -> {
                val t = JSONObject()
                    .put("serverName", q("sni") ?: hostHdr ?: host)
                    .put("allowInsecure", q("allowInsecure") == "1" || q("insecure") == "1")
                    .put("fingerprint", q("fp") ?: "chrome")
                q("alpn")?.let { t.put("alpn", JSONArray(it.split(','))) }
                st.put("tlsSettings", t)
            }
            "reality" -> st.put("realitySettings", JSONObject()
                .put("serverName", q("sni") ?: host)
                .put("fingerprint", q("fp") ?: "chrome")
                .put("publicKey", q("pbk") ?: "")
                .put("shortId", q("sid") ?: "")
                .put("spiderX", q("spx") ?: ""))
        }
        return st
    }

    private fun outbound(protocol: String, settings: JSONObject, st: JSONObject) =
        JSONObject().put("tag", "proxy").put("protocol", protocol)
            .put("settings", settings).put("streamSettings", st)

    private fun vless(s: String): Server {
        val u = Uri.parse(s)
        val q = { k: String -> u.getQueryParameter(k)?.takeIf { it.isNotEmpty() } }
        val host = u.host!!
        val port = if (u.port > 0) u.port else 443
        val user = JSONObject().put("id", u.userInfo).put("encryption", q("encryption") ?: "none")
        q("flow")?.let { user.put("flow", it) }
        val settings = JSONObject().put("vnext", JSONArray().put(
            JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))))
        return Server(u.fragment ?: host, host, port, outbound("vless", settings, stream(q, host)))
    }

    private fun vmess(s: String): Server {
        val j = JSONObject(b64(s.removePrefix("vmess://")))
        val map = mapOf(
            "type" to j.optString("net", "tcp"),
            "host" to j.optString("host"),
            "path" to j.optString("path"),
            "security" to (if (j.optString("tls") == "tls") "tls" else "none"),
            "sni" to j.optString("sni"),
            "alpn" to j.optString("alpn"),
            "fp" to j.optString("fp"),
            "headerType" to j.optString("type"),
            "serviceName" to j.optString("path")
        )
        val q = { k: String -> map[k]?.takeIf { it.isNotEmpty() } }
        val host = j.getString("add")
        val port = j.optString("port", "443").toInt()
        val user = JSONObject().put("id", j.getString("id"))
            .put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", j.optString("scy", "auto").ifEmpty { "auto" })
        val settings = JSONObject().put("vnext", JSONArray().put(
            JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))))
        return Server(j.optString("ps", host), host, port, outbound("vmess", settings, stream(q, host)))
    }

    private fun trojan(s: String): Server {
        val u = Uri.parse(s)
        val q = { k: String -> u.getQueryParameter(k)?.takeIf { it.isNotEmpty() } }
        val q2 = { k: String -> if (k == "security") (q(k) ?: "tls") else q(k) }
        val host = u.host!!
        val port = if (u.port > 0) u.port else 443
        val settings = JSONObject().put("servers", JSONArray().put(
            JSONObject().put("address", host).put("port", port).put("password", u.userInfo)))
        return Server(u.fragment ?: host, host, port, outbound("trojan", settings, stream(q2, host)))
    }

    private fun shadowsocks(s: String): Server {
        val rest = s.removePrefix("ss://")
        val name = if ('#' in rest) URLDecoder.decode(rest.substringAfter('#'), "UTF-8") else ""
        val main = rest.substringBefore('#').substringBefore('?').trimEnd('/')
        val userInfo: String
        val hostPort: String
        if ('@' in main) {
            val ui = main.substringBeforeLast('@')
            userInfo = if (':' in ui) URLDecoder.decode(ui, "UTF-8") else b64(ui)
            hostPort = main.substringAfterLast('@')
        } else {
            val d = b64(main)
            userInfo = d.substringBeforeLast('@')
            hostPort = d.substringAfterLast('@')
        }
        val method = userInfo.substringBefore(':')
        val pass = userInfo.substringAfter(':')
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = hostPort.substringAfterLast(':').toInt()
        val settings = JSONObject().put("servers", JSONArray().put(
            JSONObject().put("address", host).put("port", port).put("method", method).put("password", pass)))
        val st = JSONObject().put("network", "tcp").put("security", "none")
        return Server(name.ifEmpty { host }, host, port, outbound("shadowsocks", settings, st))
    }
}

object ConfigBuilder {
    const val SOCKS_PORT = 10808

    fun build(s: Server): String {
        val inbound = JSONObject()
            .put("tag", "socks").put("port", SOCKS_PORT).put("listen", "127.0.0.1")
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", JSONObject().put("enabled", true)
                .put("destOverride", JSONArray(listOf("http", "tls", "quic"))))
        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("servers", JSONArray(listOf("1.1.1.1", "8.8.8.8"))))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray()
                .put(s.outbound)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole")))
            .put("routing", JSONObject().put("domainStrategy", "IPIfNonMatch").put("rules", JSONArray()))
        return root.toString()
    }
}

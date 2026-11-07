package com.fastnode.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }
        scroll.addView(column)
        column.addView(label("حول التطبيق", 23f, Color.WHITE, true))
        column.addView(btn("رجوع", Color.parseColor("#2A2733"), Color.WHITE) { finish() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })

        val body = """V2Shizzi VPN

تطبيق VPN متطور، من تطوير أيادٍ عراقية، صُمم لتوفير تجربة اتصال سهلة ومستقرة وآمنة.

يدعم التطبيق تشغيل مختلف بروتوكولات وأكواد V2Ray، بالإضافة إلى إمكانية مشاركة اتصال الإنترنت عبر تقنية VPN Sharing باستخدام صلاحيات الروت (Root) و Shizuku.

أبرز مميزات التطبيق:

- دعم بروتوكولات V2Ray المختلفة.
- تشغيل أكواد VLESS وVMess وTrojan وShadowsocks.
- مشاركة اتصال VPN مع الأجهزة الأخرى.
- دعم المشاركة عبر Root و Shizuku.
- واجهة سهلة وعصرية.

فريق التطوير
@Mlokyali
@SUOHIBDEV

قناتنا الرسمية على تيليجرام

https://t.me/MRSUOHIB

© جميع الحقوق محفوظة لفريق تطوير V2Shizzi VPN.""".trimIndent()
        val about = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setLineSpacing(dp(4).toFloat(), 1.0f)
            gravity = Gravity.START
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = roundBg(CARD, dp(20), dp(1), Color.parseColor("#2A2733"))
            movementMethod = LinkMovementMethod.getInstance()
            linksClickable = true
        }
        val linked = SpannableString(body)
        fun addLink(display: String, url: String) {
            val start = body.indexOf(display)
            if (start >= 0) linked.setSpan(URLSpan(url), start, start + display.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        addLink("@Mlokyali", "https://t.me/Mlokyali")
        addLink("@SUOHIBDEV", "https://t.me/SUOHIBDEV")
        addLink("https://t.me/MRSUOHIB", "https://t.me/MRSUOHIB")
        about.text = linked
        column.addView(about, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        setContentView(scroll)
    }
}

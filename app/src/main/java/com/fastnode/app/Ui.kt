package com.fastnode.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

val BG = Color.parseColor("#0A0810")
val CARD = Color.parseColor("#121018")
val MAGENTA = Color.parseColor("#E82CC2")
val VIOLET = Color.parseColor("#9B51E8")
val MUTED = Color.parseColor("#9A93A8")

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun roundBg(color: Int, r: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = r.toFloat()
    if (stroke > 0) setStroke(stroke, strokeColor)
}

fun Context.label(t: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) =
    TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.btn(t: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
    text = t; setTextColor(fg); isAllCaps = false; textSize = 17f
    typeface = Typeface.DEFAULT_BOLD; stateListAnimator = null
    background = roundBg(bg, dp(22))
    setOnClickListener { onClick() }
}

fun Context.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundBg(CARD, dp(20), dp(1), Color.parseColor("#2A2733"))
    setPadding(dp(16), dp(16), dp(16), dp(16))
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
}

class LogoView(c: Context) : View(c) {
    var active = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.42f
        val sw = r * 0.19f
        p.strokeWidth = sw
        val c1 = if (active) Color.parseColor("#8450C8") else Color.parseColor("#3A3547")
        val c2 = if (active) Color.parseColor("#C426A8") else Color.parseColor("#2B2736")

        p.style = Paint.Style.STROKE
        p.color = c1
        val r1 = r - sw / 2
        cv.drawArc(RectF(cx - r1, cy - r1, cx + r1, cy + r1), 125f, 290f, false, p)
        p.color = c2
        val r2 = r * 0.64f
        cv.drawArc(RectF(cx - r2, cy - r2, cx + r2, cy + r2), 128f, 284f, false, p)
        p.style = Paint.Style.FILL
        cv.drawCircle(cx, cy, r * 0.17f, p)
    }
}

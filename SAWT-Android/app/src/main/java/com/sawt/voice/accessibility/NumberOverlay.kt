package com.sawt.voice.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.sawt.voice.AppGraph

/** شارات أرقام فوق عناصر الشاشة، عبر نافذة Accessibility Overlay (لا تحتاج صلاحية «الظهور فوق التطبيقات»). */
class NumberOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: BadgeView? = null

    fun show(items: List<Pair<Int, Rect>>) {
        hide()
        val v = BadgeView(service, items)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.LEFT
        try {
            wm.addView(v, lp)
            view = v
        } catch (t: Throwable) {
            AppGraph.errors.log("number-overlay", t)
        }
    }

    fun hide() {
        view?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        view = null
    }

    private class BadgeView(context: Context, private val items: List<Pair<Int, Rect>>) : View(context) {
        private val density = resources.displayMetrics.density
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OverlayColors.BLUE }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OverlayColors.WHITE
            style = Paint.Style.STROKE
            strokeWidth = density
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OverlayColors.WHITE
            textSize = 13f * density
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val w = 26f * density
            val h = 20f * density
            for ((number, r) in items) {
                val right = (r.right - loc[0]).toFloat()
                val top = (r.top - loc[1]).toFloat()
                val rect = RectF(right - w, top, right, top + h)
                canvas.drawRoundRect(rect, 6f * density, 6f * density, bg)
                canvas.drawRoundRect(rect, 6f * density, 6f * density, outline)
                canvas.drawText(number.toString(), rect.centerX(), rect.centerY() + text.textSize * 0.35f, text)
            }
        }
    }
}

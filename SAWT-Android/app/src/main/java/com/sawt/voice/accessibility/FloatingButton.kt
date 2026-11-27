package com.sawt.voice.accessibility

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.sawt.voice.AppGraph
import com.sawt.voice.R
import com.sawt.voice.engine.VoiceStatus
import com.sawt.voice.settings.AppSettings
import com.sawt.voice.util.isReduceMotion
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * الزر العائم: نافذة Accessibility Overlay خفيفة (لا تحتاج SYSTEM_ALERT_WINDOW).
 * ضغطة = تشغيل/إيقاف الاستماع، سحب = تحريك. الحركة تعمل فقط أثناء الاستماع لتوفير البطارية.
 */
class FloatingButton(
    private val service: AccessibilityService,
    private val onTap: () -> Unit,
    private val onMoved: (Int, Int) -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: ButtonView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var sizePx = 0

    fun render(s: AppSettings, status: VoiceStatus) {
        if (!s.floatingEnabled) {
            hide()
            return
        }
        val density = service.resources.displayMetrics.density
        val wantPx = (s.floatSizeDp * density).toInt()
        if (view == null || wantPx != sizePx) {
            hide()
            create(wantPx)
        }
        val v = view ?: return
        val params = lp ?: return
        v.alpha = s.floatAlpha
        v.setListening(
            status == VoiceStatus.LISTENING || status == VoiceStatus.STARTING || status == VoiceStatus.PROCESSING,
            isReduceMotion(service),
        )
        val (tx, ty) = targetPosition(s)
        if (params.x != tx || params.y != ty) {
            params.x = tx
            params.y = ty
            try { wm.updateViewLayout(v, params) } catch (_: Throwable) {}
        }
    }

    fun hide() {
        view?.let {
            it.stopAnimation()
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        view = null
        lp = null
    }

    private fun create(px: Int) {
        sizePx = px
        val params = WindowManager.LayoutParams(
            px,
            px,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.LEFT
        lp = params
        val v = ButtonView(service)
        v.contentDescription = service.getString(R.string.floating_button_description)
        try {
            wm.addView(v, params)
            view = v
        } catch (t: Throwable) {
            AppGraph.errors.log("floating-button", t)
            lp = null
        }
    }

    private fun targetPosition(s: AppSettings): Pair<Int, Int> {
        val dm = service.resources.displayMetrics
        return if (s.floatX >= 0 && s.floatY >= 0) {
            s.floatX.coerceIn(0, (dm.widthPixels - sizePx).coerceAtLeast(0)) to
                s.floatY.coerceIn(0, (dm.heightPixels - sizePx).coerceAtLeast(0))
        } else {
            (dm.widthPixels - sizePx - (16 * dm.density).toInt()) to (dm.heightPixels / 2)
        }
    }

    private inner class ButtonView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OverlayColors.SURFACE }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            color = OverlayColors.WHITE
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OverlayColors.CRIMSON_LIGHT }
        private var listening = false
        private var phase = 0f
        private var animator: ValueAnimator? = null

        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        fun setListening(active: Boolean, reduceMotion: Boolean) {
            listening = active
            if (active && !reduceMotion) startAnimation() else stopAnimation()
            invalidate()
        }

        private fun startAnimation() {
            if (animator != null) return
            animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
            animator = null
        }

        override fun onDetachedFromWindow() {
            stopAnimation()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = min(width, height) / 2f - ring.strokeWidth
            ring.color = if (listening) OverlayColors.CRIMSON_LIGHT else OverlayColors.BLUE
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, ring)
            val heights = floatArrayOf(0.30f, 0.55f, 0.80f, 0.55f, 0.30f)
            val gap = r * 0.26f
            bar.strokeWidth = r * 0.14f
            bar.color = if (listening) OverlayColors.CRIMSON_LIGHT else OverlayColors.WHITE
            for (i in heights.indices) {
                val scale = if (listening) 0.65f + 0.35f * sin(phase + i) else 1f
                val h = r * heights[i] * scale
                val x = cx + (i - 2) * gap
                canvas.drawLine(x, cy - h / 2f, x, cy + h / 2f, bar)
            }
            if (listening) canvas.drawCircle(cx + r * 0.72f, cy - r * 0.72f, r * 0.14f, dot)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val params = lp ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        val dm = resources.displayMetrics
                        params.x = (startX + dx.toInt()).coerceIn(0, (dm.widthPixels - sizePx).coerceAtLeast(0))
                        params.y = (startY + dy.toInt()).coerceIn(0, (dm.heightPixels - sizePx).coerceAtLeast(0))
                        try { wm.updateViewLayout(this, params) } catch (_: Throwable) {}
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) onMoved(params.x, params.y)
                    else {
                        performClick()
                        onTap()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return super.onTouchEvent(e)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}

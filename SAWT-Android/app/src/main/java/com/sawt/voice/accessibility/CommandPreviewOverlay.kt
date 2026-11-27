package com.sawt.voice.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/** شريط معاينة مؤقت يعرض ما سمعه صَوْت والوجهة قبل تنفيذ الأمر. */
class CommandPreviewOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var container: LinearLayout? = null
    private var hideTask: Runnable? = null

    fun show(spoken: String, action: String): Boolean {
        handler.removeCallbacksAndMessages(null)
        val view = container ?: createContainer().also { container = it }
        view.removeAllViews()
        view.addView(textView("صَوْت  ·  سمعتك", 11f, Color.rgb(214, 64, 90), true))
        view.addView(textView("«${spoken.take(110)}»", 14f, Color.WHITE, true).apply {
            setPadding(0, dp(3), 0, dp(2))
        })
        view.addView(textView("الوجهة  ·  ${action.take(110)}", 12f, Color.rgb(170, 177, 195), false))

        return try {
            if (view.parent == null) {
                val screenWidth = service.resources.displayMetrics.widthPixels
                val width = min(screenWidth - dp(28), dp(360)).coerceAtLeast(dp(240))
                val params = WindowManager.LayoutParams(
                    width,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dp(26)
                }
                windowManager.addView(view, params)
            } else {
                view.requestLayout()
            }
            hideTask = Runnable { hide() }.also { handler.postDelayed(it, 1_500L) }
            true
        } catch (_: Throwable) {
            container = null
            false
        }
    }

    fun hide() {
        hideTask?.let(handler::removeCallbacks)
        hideTask = null
        container?.let { view ->
            runCatching { if (view.parent != null) windowManager.removeView(view) }
        }
        container = null
    }

    private fun createContainer() = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        gravity = Gravity.START
        setPadding(dp(16), dp(10), dp(16), dp(11))
        background = GradientDrawable().apply {
            setColor(Color.argb(247, 12, 13, 18))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(158, 27, 50))
        }
        elevation = dp(10).toFloat()
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean) =
        TextView(service).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.START
            textDirection = android.view.View.TEXT_DIRECTION_RTL
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}

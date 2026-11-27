package com.sawt.voice.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sawt.voice.AppGraph
import com.sawt.voice.MainActivity
import com.sawt.voice.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * محرك التحكم بالشاشة. يعمل فقط بطلب المستخدم (صوت أو زر عائم):
 * رجوع/رئيسية/أخيرة/لقطة، تمرير، ضغط بالرقم أو بالنص، كتابة، قراءة النص الظاهر، أرقام العناصر.
 */
class SawtAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SawtAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var floating: FloatingButton? = null
    private var numberOverlay: NumberOverlay? = null
    private var commandPreview: CommandPreviewOverlay? = null
    private var numbered: List<ScreenReader.Item> = emptyList()
    private var numbersShownAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        numberOverlay = NumberOverlay(this)
        commandPreview = CommandPreviewOverlay(this)
        floating = FloatingButton(this, onTap = ::onFloatingTap, onMoved = { x, y ->
            AppGraph.settings.update { it.copy(floatX = x, floatY = y) }
        })
        scope.launch {
            combine(
                AppGraph.settings.settings,
                AppGraph.voice.state.map { it.status }.distinctUntilChanged(),
            ) { settings, status -> settings to status }
                .collect { (settings, status) -> floating?.render(settings, status) }
        }
    }

    /** يعرض معاينة قصيرة للأمر والوجهة قبل فتح تطبيق خارجي. */
    fun showCommandPreview(spoken: String, action: String): Boolean =
        commandPreview?.show(spoken, action) == true

    private fun onFloatingTap() {
        if (Permissions.hasMic(this)) {
            AppGraph.voice.toggle()
        } else {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            numbered.isNotEmpty() && SystemClock.elapsedRealtime() - numbersShownAt > 700
        ) {
            hideNumbers()
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        instance = null
        floating?.hide()
        numberOverlay?.hide()
        commandPreview?.hide()
        scope.cancel()
    }

    // ---- الإجراءات ----

    fun scroll(forward: Boolean): Boolean {
        val node = ScreenReader.findScrollable(rootInActiveWindow)
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (node != null && node.performAction(action)) return true
        return swipeVertical(contentUp = forward)
    }

    private fun swipeVertical(contentUp: Boolean): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val from = if (contentUp) dm.heightPixels * 0.70f else dm.heightPixels * 0.30f
        val to = if (contentUp) dm.heightPixels * 0.30f else dm.heightPixels * 0.70f
        val path = Path().apply {
            moveTo(x, from)
            lineTo(x, to)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun readScreen(): String = ScreenReader.readVisibleText(rootInActiveWindow)

    fun showNumbers(): Int {
        val items = ScreenReader.collectActionable(rootInActiveWindow, 99)
        numbered = items
        numbersShownAt = SystemClock.elapsedRealtime()
        if (items.isEmpty()) {
            numberOverlay?.hide()
        } else {
            numberOverlay?.show(items.map { it.number to it.bounds })
        }
        return items.size
    }

    fun hideNumbers() {
        numbered = emptyList()
        numberOverlay?.hide()
    }

    fun clickNumber(n: Int): Boolean {
        val item = numbered.firstOrNull { it.number == n } ?: return false
        item.node.refresh()
        val ok = clickNode(item.node, item.bounds)
        hideNumbers()
        return ok
    }

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = when (text.lowercase()) {
            "like" -> listOf("Like", "إعجاب", "أعجبني", "اعجاب", "أحب")
            "shutter" -> listOf("Shutter", "Take photo", "Take picture", "Capture", "التقاط صورة", "التقط صورة", "التقاط", "تصوير", "زر التصوير")
            else -> listOf(text)
        }
        val target = candidates.asSequence()
            .flatMap { root.findAccessibilityNodeInfosByText(it).asSequence() }
            .firstOrNull { it.isVisibleToUser && !it.isPassword } ?: return false
        val r = Rect()
        target.getBoundsInScreen(r)
        return clickNode(target, r)
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: ScreenReader.findEditable(root)
            ?: return false
        if (node.isPassword) return false
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val combined = if (current.isEmpty()) text else "$current $text"
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun clickNode(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        return tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}

package com.sawt.voice.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * قراءة شجرة إمكانية الوصول. حدود العمق وعدد العقد تمنع التجمد.
 * حقول كلمات المرور تُتجاوز دائمًا: لا تُقرأ ولا تُرقَّم.
 */
object ScreenReader {
    private const val MAX_DEPTH = 40
    private const val MAX_NODES = 2500

    data class Item(val number: Int, val node: AccessibilityNodeInfo, val bounds: Rect)

    fun readVisibleText(root: AccessibilityNodeInfo?, maxChars: Int = 1500): String {
        if (root == null) return ""
        val out = StringBuilder()
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (out.length >= maxChars || depth > MAX_DEPTH || visited++ > MAX_NODES) return
            if (!n.isVisibleToUser || n.isPassword) return
            val text = n.text?.toString()?.trim().orEmpty().ifEmpty { n.contentDescription?.toString()?.trim().orEmpty() }
            if (text.isNotEmpty()) out.append(text).append(". ")
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return out.toString().take(maxChars)
    }

    fun collectActionable(root: AccessibilityNodeInfo?, max: Int = 99): List<Item> {
        if (root == null) return emptyList()
        val found = ArrayList<Pair<AccessibilityNodeInfo, Rect>>()
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH || visited++ > MAX_NODES) return
            if (!n.isVisibleToUser) return
            val actionable = n.isEnabled && !n.isPassword &&
                (n.isClickable || n.isLongClickable || n.isCheckable || n.isEditable)
            if (actionable) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.width() > 8 && r.height() > 8) found.add(n to r)
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        // ترتيب القراءة العربي: من الأعلى للأسفل ثم من اليمين لليسار.
        val sorted = found.sortedWith(compareBy<Pair<AccessibilityNodeInfo, Rect>>({ it.second.top / 48 }, { -it.second.right }))
        return sorted.take(max).mapIndexed { i, (node, rect) -> Item(i + 1, node, rect) }
    }

    fun findScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (depth > MAX_DEPTH || visited++ > MAX_NODES) return null
            if (!n.isVisibleToUser) return null
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child, depth + 1)?.let { return it }
            }
            return null
        }
        return walk(root, 0)
    }

    fun findEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        var visited = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (depth > MAX_DEPTH || visited++ > MAX_NODES) return null
            if (!n.isVisibleToUser) return null
            if (n.isEditable && n.isEnabled) return n
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child, depth + 1)?.let { return it }
            }
            return null
        }
        return walk(root, 0)
    }
}

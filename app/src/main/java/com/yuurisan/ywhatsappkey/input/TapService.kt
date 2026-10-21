package com.yuurisan.ywhatsappkey.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yuurisan.ywhatsappkey.overlay.OverlayBounds
import java.util.concurrent.Executors
import kotlin.math.abs

class TapService : AccessibilityService() {

    data class NodeKey(val digits: String, val bounds: Rect)

    companion object {
        @Volatile
        var instance: TapService? = null
            private set

        val isReady: Boolean get() = instance != null

        private val KEY_EXACT = Regex("^\\d{4}$")
        private val KEY_SPACED = Regex("^\\d([\\s\\u00A0]\\d){3}$")

        private val GENERATE_LABELS = listOf(
            "dapatkankunciyangberbeda",
            "dapatkankunciberbeda",
            "kunciyangberbeda",
            "dapatkankunci",
            "kunciberbeda",
            "kuncibaru",
            "kunci lain",
            "kunci lainnya",
            "gantikunci",
            "getadifferentkey",
            "getanotherkey",
            "getnewkey",
            "differentkey",
            "anotherkey",
            "newkey",
            "generatekey",
            "generate",
            "refreshkey"
        ).map { it.replace(" ", "").lowercase() }

        private val SAVE_LABELS = listOf(
            "simpankunci",
            "savekey",
            "simpan",
            "save",
            "gunakan"
        ).map { it.replace(" ", "").lowercase() }
    }

    private var keyBox: Rect? = null
    private val main = Handler(Looper.getMainLooper())
    private val shotExecutor = Executors.newSingleThreadExecutor()
    private var lastPackage: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != packageName) lastPackage = pkg
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        shotExecutor.shutdownNow()
        super.onDestroy()
    }

    fun foregroundPackage(): String = lastPackage

    fun isForeignApp(): Boolean {
        val p = lastPackage
        return p.isNotEmpty() && p != packageName
    }

    /**
     * Root milik aplikasi lain. Panel kita sendiri bisa menjadi jendela aktif,
     * dan kalau itu terjadi rootInActiveWindow menunjuk ke panel sendiri.
     */
    fun root(): AccessibilityNodeInfo? {
        val own = packageName
        try {
            val ws = windows
            if (ws != null) {
                var other: AccessibilityNodeInfo? = null
                for (w in ws) {
                    val r = try {
                        w.root
                    } catch (t: Throwable) {
                        null
                    } ?: continue
                    val p = r.packageName?.toString().orEmpty()
                    if (p.isEmpty() || p == own) continue
                    if (p == lastPackage && w.isActive) return r
                    if (other == null) other = r
                }
                if (other != null) return other
            }
        } catch (t: Throwable) {
            Unit
        }
        return try {
            val r = rootInActiveWindow
            val p = r?.packageName?.toString().orEmpty()
            if (r != null && p.isNotEmpty() && p != own) r else null
        } catch (t: Throwable) {
            null
        }
    }

    fun readKey(): NodeKey? {
        val root = root() ?: return null
        val candidates = collectKeyNodes(root)
        if (candidates.isNotEmpty()) return pickKey(candidates)
        return findSplitKey(root)
    }

    /**
     * Semua kandidat empat digit di jendela aplikasi lain. Kandidat yang tertutup
     * panel sendiri dibuang supaya angka panel tidak terbaca sebagai kunci.
     */
    private fun collectKeyNodes(root: AccessibilityNodeInfo): List<NodeKey> {
        val out = ArrayList<NodeKey>(4)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val digits = normalizeDigits(text) ?: normalizeDigits(desc)
            if (digits != null) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0 && !underOverlay(r)) out.add(NodeKey(digits, r))
            }
            for (i in 0 until node.childCount) {
                val c = try {
                    node.getChild(i)
                } catch (t: Throwable) {
                    null
                }
                if (c != null) queue.add(c)
            }
        }
        return out
    }

    /**
     * Kunci yang tampil paling besar di layar. Kalau kotak kunci sudah pernah
     * diketahui, kandidat di dalam kotak itu yang dipakai supaya pembacaan stabil
     * dan tidak tertukar dengan angka lain seperti tahun atau nomor telepon.
     */
    private fun pickKey(list: List<NodeKey>): NodeKey {
        val box = keyBox
        if (box != null) {
            val near = list.filter {
                it.bounds.centerX() in box.left..box.right &&
                    it.bounds.centerY() in box.top..box.bottom
            }
            if (near.isNotEmpty()) return near.maxByOrNull { it.bounds.height() }!!
        }
        return list.maxByOrNull { it.bounds.height() * 10000 + it.bounds.width() }!!
    }

    fun setKeyBox(r: Rect?) {
        keyBox = r?.let { Rect(it) }
    }

    private fun findSplitKey(root: AccessibilityNodeInfo): NodeKey? {
        val singles = ArrayList<Pair<String, Rect>>(8)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val node = queue.removeFirst()
            visited++
            val t = node.text?.toString()?.trim().orEmpty()
            if (t.length == 1 && t[0].isDigit()) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0 && !underOverlay(r)) singles.add(t to r)
            }
            for (i in 0 until node.childCount) {
                val c = try {
                    node.getChild(i)
                } catch (x: Throwable) {
                    null
                }
                if (c != null) queue.add(c)
            }
        }
        if (singles.size < 4) return null
        singles.sortBy { it.second.centerY() }
        var best: NodeKey? = null
        var bestH = 0
        var i = 0
        while (i < singles.size) {
            val base = singles[i].second
            val row = ArrayList<Pair<String, Rect>>(8)
            var j = i
            while (j < singles.size && abs(singles[j].second.centerY() - base.centerY()) <= base.height() * 0.6f) {
                row.add(singles[j])
                j++
            }
            if (row.size >= 4) {
                row.sortBy { it.second.left }
                for (k in 0..row.size - 4) {
                    val g = row.subList(k, k + 4)
                    val gap = g.last().second.left - g.first().second.right
                    if (gap > base.width() * 3) continue
                    val h = g.maxOf { it.second.height() }
                    if (h > bestH) {
                        bestH = h
                        best = NodeKey(
                            g.joinToString("") { it.first },
                            Rect(
                                g.minOf { it.second.left },
                                g.minOf { it.second.top },
                                g.maxOf { it.second.right },
                                g.maxOf { it.second.bottom }
                            )
                        )
                    }
                }
            }
            i = if (j > i) j else i + 1
        }
        return best
    }

    private fun normalizeDigits(raw: String): String? {
        if (KEY_EXACT.matches(raw)) return raw
        if (KEY_SPACED.matches(raw)) return raw.filter { it.isDigit() }
        return null
    }

    fun findGenerateButton(point: Rect?, keyBox: Rect?): Rect? {
        if (point != null) return point
        findButtonByLabels(GENERATE_LABELS)?.let { return it }
        return fallbackBelow(keyBox)
    }

    fun findSaveButton(point: Rect?): Rect? {
        if (point != null) return point
        return findButtonByLabels(SAVE_LABELS)
    }

    private fun findButtonByLabels(labels: List<String>): Rect? {
        val root = root() ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            val text = (node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty())
                .lowercase()
                .replace(" ", "")
                .replace("\u00A0", "")
            if (text.isNotEmpty()) {
                for (label in labels) {
                    if (text.contains(label)) {
                        val target = clickableAncestor(node)
                        val r = Rect()
                        target.getBoundsInScreen(r)
                        if (r.width() > 0 && r.height() > 0 && !underOverlay(r)) return r
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val c = try {
                    node.getChild(i)
                } catch (t: Throwable) {
                    null
                }
                if (c != null) queue.add(c)
            }
        }
        return null
    }

    /**
     * Jalan terakhir: tombol klik paling besar yang berada di bawah kotak kunci.
     */
    private fun fallbackBelow(keyBox: Rect?): Rect? {
        val box = keyBox ?: return null
        val root = root() ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var best: Rect? = null
        var bestArea = 0
        var bestTop = Int.MAX_VALUE
        while (queue.isNotEmpty() && visited < 600) {
            val node = queue.removeFirst()
            visited++
            if (node.isClickable) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (r.width() > 40 && r.height() > 24 &&
                    r.top >= box.bottom - 8 && r.top < box.bottom + (box.height() * 6) &&
                    !underOverlay(r)
                ) {
                    val area = r.width() * r.height()
                    if (r.top < bestTop || (r.top == bestTop && area > bestArea)) {
                        bestTop = r.top
                        bestArea = area
                        best = r
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val c = try {
                    node.getChild(i)
                } catch (t: Throwable) {
                    null
                }
                if (c != null) queue.add(c)
            }
        }
        return best
    }

    /**
     * Tombol yang tertutup panel sendiri tidak boleh ditekan, gesture-nya akan
     * mendarat di panel dan tidak mengubah apa pun.
     */
    private fun underOverlay(r: Rect): Boolean {
        val list = OverlayBounds.rects()
        if (list.isEmpty()) return false
        for (e in list) {
            if (r.left < e.right && r.right > e.left && r.top < e.bottom && r.bottom > e.top) return true
        }
        return false
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return node
    }

    fun tap(rect: Rect): Boolean = tap(rect.centerX().toFloat(), rect.centerY().toFloat())

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 32L))
            .build()
        return try {
            dispatchGesture(gesture, null, main)
        } catch (t: Throwable) {
            false
        }
    }

    fun tapNode(node: AccessibilityNodeInfo): Boolean {
        val target = clickableAncestor(node)
        if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect()
        target.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return false
        return tap(r)
    }

    fun screenshot(callback: (Bitmap?) -> Unit) {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                shotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        var bmp: Bitmap? = null
                        try {
                            val hb = result.hardwareBuffer
                            val wrapped = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                            bmp = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                            wrapped?.recycle()
                            hb.close()
                        } catch (t: Throwable) {
                            bmp = null
                        }
                        main.post { callback(bmp) }
                    }

                    override fun onFailure(errorCode: Int) {
                        main.post { callback(null) }
                    }
                }
            )
        } catch (t: Throwable) {
            main.post { callback(null) }
        }
    }
}

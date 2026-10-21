package com.yuurisan.ywhatsappkey.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DigitSolver {

    const val GW = 16
    const val GH = 24
    const val NPIX = GW * GH

    data class Match(val digit: Int, val score: Float)

    private val scales = floatArrayOf(0.88f, 1.0f, 1.12f)

    private val specs = arrayOf(
        "sans-serif" to Typeface.NORMAL,
        "sans-serif" to Typeface.BOLD,
        "sans-serif-medium" to Typeface.NORMAL,
        "sans-serif-light" to Typeface.NORMAL,
        "sans-serif-black" to Typeface.NORMAL,
        "sans-serif-condensed" to Typeface.BOLD,
        "monospace" to Typeface.BOLD
    )

    @Volatile
    private var bank: Array<FloatArray>? = null

    @Volatile
    private var bankDigit: IntArray = IntArray(0)

    @Volatile
    private var bankAspect: FloatArray = FloatArray(0)

    fun warmUp() {
        ensure()
    }

    @Synchronized
    private fun ensure() {
        if (bank != null) return

        val arrays = ArrayList<FloatArray>(256)
        val digits = ArrayList<Int>(256)
        val aspects = ArrayList<Float>(256)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 110f
            textAlign = Paint.Align.LEFT
        }
        val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val px = IntArray(200 * 200)

        for ((family, style) in specs) {
            paint.typeface = Typeface.create(family, style)
            for (digit in 0..9) {
                canvas.drawColor(Color.BLACK)
                canvas.drawText(digit.toString(), 50f, 150f, paint)
                bmp.getPixels(px, 0, 200, 0, 0, 200, 200)
                val box = inkBox(px, 200, 200) ?: continue
                val base = normalize(px, 200, box)
                val asp = box.width().toFloat() / max(1, box.height()).toFloat()
                for (s in scales) {
                    arrays.add(stretch(base, s))
                    digits.add(digit)
                    aspects.add(asp * s)
                }
            }
        }
        bmp.recycle()

        bankDigit = digits.toIntArray()
        bankAspect = aspects.toFloatArray()
        bank = arrays.toTypedArray()
    }

    /**
     * Kanvas diisi Color.BLACK, dan hitam pekat pun tetap punya alpha 255.
     * Karena itu tinta harus diukur dari kecerahan, bukan dari alpha: kalau
     * memakai alpha, seluruh kanvas dianggap tinta, kotak digit menjadi
     * 200x200, dan semua template jadi hampir identik sehingga tidak ada
     * digit yang pernah cocok.
     */
    private fun ink(c: Int): Boolean {
        val a = (c ushr 24) and 0xFF
        if (a <= 110) return false
        val lum = (((c ushr 16) and 0xFF) * 77 + ((c ushr 8) and 0xFF) * 151 + (c and 0xFF) * 28) shr 8
        return lum > 110
    }

    private fun inkBox(px: IntArray, w: Int, h: Int): Rect? {
        var l = w
        var t = h
        var r = -1
        var b = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (ink(px[row + x])) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        if (r < 0) return null
        return Rect(l, t, r + 1, b + 1)
    }

    private fun normalize(px: IntArray, w: Int, box: Rect): FloatArray {
        val out = FloatArray(NPIX)
        val bw = max(1, box.width())
        val bh = max(1, box.height())
        val scale = min((GW - 2).toFloat() / bw, (GH - 2).toFloat() / bh)
        val dw = max(1, (bw * scale).roundToInt())
        val dh = max(1, (bh * scale).roundToInt())
        val ox = (GW - dw) / 2
        val oy = (GH - dh) / 2
        for (y in 0 until dh) {
            val sy = box.top + (y * bh / dh)
            for (x in 0 until dw) {
                val sx = box.left + (x * bw / dw)
                out[(oy + y) * GW + ox + x] =
                    if (ink(px[sy * w + sx])) 1f else 0f
            }
        }
        return out
    }

    private fun stretch(src: FloatArray, s: Float): FloatArray {
        if (s == 1f) return src
        val out = FloatArray(NPIX)
        val cx = GW / 2f
        for (y in 0 until GH) {
            val row = y * GW
            for (x in 0 until GW) {
                val sx = ((x - cx) / s + cx).roundToInt()
                if (sx in 0 until GW) out[row + x] = src[row + sx]
            }
        }
        return out
    }

    fun glyphFromMask(mask: ByteArray, w: Int, h: Int, box: Rect): Pair<FloatArray, Float>? {
        var l = box.right
        var t = box.bottom
        var r = -1
        var b = -1
        val y1 = min(box.bottom, h)
        val x1 = min(box.right, w)
        for (y in max(0, box.top) until y1) {
            val row = y * w
            for (x in max(0, box.left) until x1) {
                if (mask[row + x].toInt() == 1) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        if (r < 0) return null
        val bw = max(1, r - l + 1)
        val bh = max(1, b - t + 1)
        val out = FloatArray(NPIX)
        val scale = min((GW - 2).toFloat() / bw, (GH - 2).toFloat() / bh)
        val dw = max(1, (bw * scale).roundToInt())
        val dh = max(1, (bh * scale).roundToInt())
        val ox = (GW - dw) / 2
        val oy = (GH - dh) / 2
        for (y in 0 until dh) {
            val sy = t + (y * bh / dh)
            for (x in 0 until dw) {
                val sx = l + (x * bw / dw)
                if (mask[sy * w + sx].toInt() == 1) out[(oy + y) * GW + ox + x] = 1f
            }
        }
        return out to (bw.toFloat() / bh.toFloat())
    }

    fun match(glyph: FloatArray, aspect: Float): Match {
        ensure()
        val b = bank ?: return Match(-1, 0f)
        var bestDigit = -1
        var bestScore = -9f
        for (i in b.indices) {
            val t = b[i]
            var l1 = 0f
            var inter = 0f
            var union = 0f
            for (p in 0 until NPIX) {
                val a = t[p]
                val c = glyph[p]
                l1 += abs(a - c)
                val ta = a > 0.5f
                val tc = c > 0.5f
                if (ta && tc) inter += 1f
                if (ta || tc) union += 1f
            }
            val l1n = l1 / NPIX
            val iou = if (union > 0f) inter / union else 0f
            var score = 1f - (0.45f * l1n + 0.55f * (1f - iou))
            score -= abs(bankAspect[i] - aspect) * 0.25f
            if (score > bestScore) {
                bestScore = score
                bestDigit = bankDigit[i]
            }
        }
        return Match(bestDigit, bestScore)
    }
}

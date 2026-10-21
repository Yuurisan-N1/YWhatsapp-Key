package com.yuurisan.ywhatsappkey.vision

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

object KeyScanner {

    data class Result(val digits: String, val confidence: Float, val box: Rect)

    private const val TARGET_W = 480
    private const val MIN_CONF = 0.58f

    fun scan(src: Bitmap, hint: Rect? = null, exclude: List<Rect> = emptyList()): Result? {
        val full = Rect(0, 0, src.width, src.height)
        val crop = Rect(
            min(max(hint?.left ?: 0, 0), src.width - 1),
            min(max(hint?.top ?: 0, 0), src.height - 1),
            min(max(hint?.right ?: src.width, 1), src.width),
            min(max(hint?.bottom ?: src.height, 1), src.height)
        )
        if (crop.width() < 24 || crop.height() < 24) return scanInternal(src, full, exclude)

        val part = Bitmap.createBitmap(src, crop.left, crop.top, crop.width(), crop.height())
        val area = Rect(crop.left, crop.top, crop.right, crop.bottom)
        val r = scanInternal(part, area, exclude)
        part.recycle()
        if (r == null) {
            if (crop == full) return null
            return scanInternal(src, full, exclude)
        }
        return r
    }

    private fun scanInternal(src: Bitmap, area: Rect, exclude: List<Rect>): Result? {
        val scale = min(1f, TARGET_W.toFloat() / area.width())
        val w = max(24, (area.width() * scale).toInt())
        val h = max(24, (area.height() * scale).toInt())

        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== src) scaled.recycle()

        val gray = ByteArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = ((r * 77 + g * 151 + b * 28) shr 8).toByte()
        }

        val thr = otsu(gray)
        var best: Result? = null
        var bestScore = -1f

        for (polarity in 0..1) {
            val mask = ByteArray(w * h)
            if (polarity == 0) {
                for (i in gray.indices) if ((gray[i].toInt() and 0xFF) > thr) mask[i] = 1
            } else {
                for (i in gray.indices) if ((gray[i].toInt() and 0xFF) <= thr) mask[i] = 1
            }

            var comps = components(mask, w, h)
            comps = splitWide(comps, mask, w, h)

            val minH = max(6f, h * 0.012f)
            val maxH = h * 0.30f
            val cand = ArrayList<Comp>(comps.size)
            for (c in comps) {
                val ch = c.bottom - c.top
                val cw = c.right - c.left
                if (ch < minH || ch > maxH) continue
                if (cw < 2 || cw > ch * 1.35f) continue
                if (exclude.isNotEmpty() && insideExcluded(c, scale, area, exclude)) continue
                cand.add(c)
            }
            if (cand.size < 4) continue

            val rows = clusterRows(cand)
            for (row in rows) {
                if (row.size < 4) continue
                row.sortBy { it.left }
                for (i in 0..row.size - 4) {
                    val g = row.subList(i, i + 4)
                    var hs = 0f
                    var hMin = Float.MAX_VALUE
                    var hMax = 0f
                    for (c in g) {
                        val ch = (c.bottom - c.top).toFloat()
                        hs += ch
                        if (ch < hMin) hMin = ch
                        if (ch > hMax) hMax = ch
                    }
                    val avg = hs / 4f
                    if (avg < minH) continue
                    if (hMax / max(1f, hMin) > 1.45f) continue
                    var ok = true
                    for (k in 0 until 3) {
                        val gap = (g[k + 1].left - g[k].right).toFloat()
                        if (gap > avg * 1.7f || gap < -avg * 0.65f) {
                            ok = false
                            break
                        }
                    }
                    if (!ok) continue
                    val span = (g[3].right - g[0].left).toFloat()
                    if (span < avg * 1.35f) continue

                    val digits = StringBuilder(4)
                    var conf = 0f
                    var failed = false
                    for (c in g) {
                        val gl = DigitSolver.glyphFromMask(mask, w, h, Rect(c.left, c.top, c.right, c.bottom))
                        if (gl == null) {
                            failed = true
                            break
                        }
                        val m = DigitSolver.match(gl.first, gl.second)
                        if (m.digit < 0) {
                            failed = true
                            break
                        }
                        digits.append(m.digit)
                        conf += m.score
                    }
                    if (failed) continue
                    conf /= 4f
                    if (conf < MIN_CONF) continue

                    val score = avg * conf
                    if (score > bestScore) {
                        bestScore = score
                        val l = (g[0].left / scale).toInt() + area.left
                        val t = (min(g[0].top, g[1].top) / scale).toInt() + area.top
                        val r = (g[3].right / scale).toInt() + area.left
                        val b = (max(g[3].bottom, g[1].bottom) / scale).toInt() + area.top
                        best = Result(digits.toString(), conf, Rect(l, t, r, b))
                    }
                }
            }
            if (bestScore >= 30f) break
        }
        return best
    }

    private class Comp(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun insideExcluded(c: Comp, scale: Float, area: Rect, exclude: List<Rect>): Boolean {
        val inv = if (scale <= 0f) 1f else 1f / scale
        val l = (c.left * inv).toInt() + area.left
        val t = (c.top * inv).toInt() + area.top
        val r = (c.right * inv).toInt() + area.left
        val b = (c.bottom * inv).toInt() + area.top
        for (e in exclude) {
            if (l < e.right && r > e.left && t < e.bottom && b > e.top) return true
        }
        return false
    }

    private fun otsu(gray: ByteArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v.toInt() and 0xFF]++
        val total = gray.size
        var sumAll = 0.0
        for (t in 0 until 256) sumAll += t.toDouble() * hist[t]
        var sumB = 0.0
        var wB = 0.0
        var bestT = 127
        var bestV = -1.0
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val v = wB * wF * (mB - mF) * (mB - mF)
            if (v > bestV) {
                bestV = v
                bestT = t
            }
        }
        return bestT
    }

    private fun components(mask: ByteArray, w: Int, h: Int): List<Comp> {
        val seen = BooleanArray(w * h)
        val out = ArrayList<Comp>(32)
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (mask[start].toInt() != 1 || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var l = w
            var t = h
            var r = -1
            var b = -1
            var count = 0
            while (sp > 0) {
                val idx = stack[--sp]
                val x = idx % w
                val y = idx / w
                count++
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
                if (x > 0) {
                    val n = idx - 1
                    if (mask[n].toInt() == 1 && !seen[n]) {
                        seen[n] = true
                        stack[sp++] = n
                    }
                }
                if (x < w - 1) {
                    val n = idx + 1
                    if (mask[n].toInt() == 1 && !seen[n]) {
                        seen[n] = true
                        stack[sp++] = n
                    }
                }
                if (y > 0) {
                    val n = idx - w
                    if (mask[n].toInt() == 1 && !seen[n]) {
                        seen[n] = true
                        stack[sp++] = n
                    }
                }
                if (y < h - 1) {
                    val n = idx + w
                    if (mask[n].toInt() == 1 && !seen[n]) {
                        seen[n] = true
                        stack[sp++] = n
                    }
                }
            }
            if (count >= 6) out.add(Comp(l, t, r + 1, b + 1))
        }
        return out
    }

    private fun splitWide(comps: List<Comp>, mask: ByteArray, w: Int, h: Int): List<Comp> {
        val out = ArrayList<Comp>(comps.size + 4)
        for (c in comps) {
            val cw = c.right - c.left
            val ch = c.bottom - c.top
            if (cw > ch * 1.15f) {
                val lo = c.left + (cw * 0.3f).toInt()
                val hi = max(lo + 1, c.left + (cw * 0.7f).toInt())
                var minCol = Int.MAX_VALUE
                var minX = -1
                for (x in lo until min(hi, c.right)) {
                    var s = 0
                    for (y in c.top until min(c.bottom, h)) {
                        if (mask[y * w + x].toInt() == 1) s++
                    }
                    if (s < minCol) {
                        minCol = s
                        minX = x
                    }
                }
                if (minX > c.left && minCol <= max(1, (ch * 0.14f).toInt())) {
                    out.add(Comp(c.left, c.top, minX, c.bottom))
                    out.add(Comp(minX, c.top, c.right, c.bottom))
                    continue
                }
            }
            out.add(c)
        }
        return out
    }

    private fun clusterRows(cand: List<Comp>): List<MutableList<Comp>> {
        val rows = ArrayList<MutableList<Comp>>()
        val heights = ArrayList<Float>()
        val centers = ArrayList<Float>()

        val sorted = cand.sortedBy { (it.top + it.bottom) / 2f }
        for (c in sorted) {
            val ch = (c.bottom - c.top).toFloat()
            val cy = (c.top + c.bottom) / 2f
            var placed = false
            for (i in rows.indices) {
                val rh = heights[i]
                val rc = centers[i]
                val ratio = max(ch, rh) / max(1f, min(ch, rh))
                if (kotlin.math.abs(cy - rc) <= rh * 0.45f && ratio <= 1.45f) {
                    val row = rows[i]
                    row.add(c)
                    heights[i] = (rh * (row.size - 1) + ch) / row.size
                    centers[i] = (rc * (row.size - 1) + cy) / row.size
                    placed = true
                    break
                }
            }
            if (!placed) {
                rows.add(mutableListOf(c))
                heights.add(ch)
                centers.add(cy)
            }
        }
        return rows
    }
}

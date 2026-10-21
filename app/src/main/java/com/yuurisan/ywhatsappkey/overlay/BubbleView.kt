package com.yuurisan.ywhatsappkey.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.yuurisan.ywhatsappkey.R
import kotlin.math.abs
import kotlin.math.roundToInt

class BubbleView(context: Context) : FrameLayout(context) {

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var onMoved: ((Int, Int) -> Unit)? = null

    private val icon = ImageView(context)
    private val dot = ImageView(context)
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var downTime = 0L
    private var dragging = false

    init {
        background = context.getDrawable(R.drawable.bg_bubble)
        elevation = dp(8f)
        icon.setImageResource(R.drawable.ic_key)
        icon.setColorFilter(context.getColor(R.color.mint))
        addView(icon, LayoutParams(dp(24f).toInt(), dp(24f).toInt(), android.view.Gravity.CENTER))
        dot.background = context.getDrawable(R.drawable.bg_dot)
        dot.visibility = GONE
        val lp = LayoutParams(dp(7f).toInt(), dp(7f).toInt(), android.view.Gravity.TOP or android.view.Gravity.END)
        lp.topMargin = dp(9f).toInt()
        lp.marginEnd = dp(9f).toInt()
        addView(dot, lp)
    }

    fun attach(windowManager: WindowManager, layoutParams: WindowManager.LayoutParams) {
        wm = windowManager
        params = layoutParams
    }

    fun setActive(active: Boolean) {
        dot.visibility = if (active) VISIBLE else GONE
        icon.setColorFilter(context.getColor(if (active) R.color.mint_bright else R.color.mint))
    }

    fun setPanelOpen(open: Boolean) {
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.getColor(R.color.surface))
            setStroke(dp(if (open) 2f else 1.5f).toInt(), context.getColor(if (open) R.color.mint_bright else R.color.mint_dim))
        }
        background = ring
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params?.x ?: 0
                startY = params?.y ?: 0
                downTime = SystemClock.uptimeMillis()
                dragging = false
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(70L).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                if (dragging) {
                    val p = params ?: return true
                    p.x = startX + dx.roundToInt()
                    p.y = startY + dy.roundToInt()
                    clamp(p)
                    try {
                        wm?.updateViewLayout(this, p)
                    } catch (t: Throwable) {
                        Unit
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
                if (!dragging) {
                    if (SystemClock.uptimeMillis() - downTime >= 550L) {
                        onLongPress?.invoke()
                    } else {
                        onTap?.invoke()
                    }
                } else {
                    params?.let { onMoved?.invoke(it.x, it.y) }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun clamp(p: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val maxX = metrics.widthPixels - width
        val maxY = metrics.heightPixels - height
        if (p.x < 0) p.x = 0
        if (p.y < 0) p.y = 0
        if (p.x > maxX) p.x = maxX
        if (p.y > maxY) p.y = maxY
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}

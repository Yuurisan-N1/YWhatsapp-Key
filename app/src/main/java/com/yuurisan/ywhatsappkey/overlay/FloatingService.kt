package com.yuurisan.ywhatsappkey.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.yuurisan.ywhatsappkey.MainActivity
import com.yuurisan.ywhatsappkey.R
import com.yuurisan.ywhatsappkey.core.StopReceiver
import com.yuurisan.ywhatsappkey.core.store.Prefs
import com.yuurisan.ywhatsappkey.engine.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingService : Service() {

    companion object {
        private const val CHANNEL_ID = "ywk_float"
        private const val NOTIF_ID = 71
        private const val EXTRA_OPEN = "open"

        @Volatile
        var alive = false
            private set

        @Volatile
        private var inst: FloatingService? = null

        fun start(context: Context, openPanel: Boolean) {
            val i = Intent(context, FloatingService::class.java).putExtra(EXTRA_OPEN, openPanel)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun togglePanelNow() {
            val s = inst ?: return
            s.main.post { s.togglePanel() }
        }

        fun hidePanelNow() {
            val s = inst ?: return
            s.main.post { s.hidePanel() }
        }

        fun panicNow() {
            Engine.stop("Semua dihentikan")
            hidePanelNow()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs

    private var bubble: BubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var controller: PanelController? = null
    private var renderJob: Job? = null
    private var previewJob: Job? = null
    private var picker: View? = null
    private var pickStep = 0
    private var typing = false

    override fun onCreate() {
        super.onCreate()
        inst = this
        alive = true
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        Engine.init(applicationContext)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble == null) showBubble()
        val open = intent?.getBooleanExtra(EXTRA_OPEN, false) ?: false
        if (open && panel == null) showPanel()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        renderJob?.cancel()
        scope.cancel()
        removePicker()
        panel?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }
        panel = null
        bubble = null
        controller = null
        OverlayBounds.update(emptyList())
        alive = false
        inst = null
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, StopReceiver::class.java).setAction(StopReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggle = PendingIntent.getBroadcast(
            this,
            3,
            Intent(this, StopReceiver::class.java).setAction(StopReceiver.ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_key)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_key),
                    getString(R.string.notif_panel),
                    toggle
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop),
                    getString(R.string.notif_stop),
                    stop
                ).build()
            )
        return b.build()
    }

    private fun showBubble() {
        if (bubble != null) return
        val b = BubbleView(this)
        val p = WindowManager.LayoutParams(
            dp(50),
            dp(50),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = if (prefs.bubbleX >= 0) prefs.bubbleX else resources.displayMetrics.widthPixels - dp(64)
        p.y = if (prefs.bubbleY >= 0) prefs.bubbleY else (resources.displayMetrics.heightPixels * 0.42f).toInt()

        b.attach(wm, p)
        b.setPanelOpen(false)
        b.onTap = { togglePanel() }
        b.onLongPress = {
            Engine.stop("Semua dihentikan")
            hidePanel()
            toast(getString(R.string.panic_done))
        }
        b.onMoved = { x, y ->
            prefs.bubbleX = x
            prefs.bubbleY = y
            refreshBounds()
        }

        runCatching { wm.addView(b, p) }.onFailure { return }
        bubble = b
        bubbleParams = p
        b.post { refreshBounds() }
    }

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun showPanel() {
        if (panel != null) return
        removePicker()
        val c = PanelController(this) { hidePanel() }
        val v = c.view

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = if (prefs.panelX != Int.MIN_VALUE) prefs.panelX else dp(10)
        p.y = if (prefs.panelY != Int.MIN_VALUE) prefs.panelY
        else (resources.displayMetrics.heightPixels * 0.16f).toInt()

        c.dragOrigin = { Pair(p.x, p.y) }
        typing = false
        c.onRequestTyping = {
            if (!typing) {
                typing = true
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                runCatching { wm.updateViewLayout(v, p) }
                v.post {
                    c.focusTarget()
                    val imm = getSystemService(InputMethodManager::class.java)
                    imm?.showSoftInput(c.targetView, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        c.onTypingDone = {
            if (typing) {
                typing = false
                p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                runCatching { wm.updateViewLayout(v, p) }
            }
        }
        c.onMove = { x, y ->
            p.x = x
            p.y = y
            clampPanel(p)
            runCatching { wm.updateViewLayout(v, p) }
            refreshBounds()
        }
        c.onMoveEnd = {
            prefs.panelX = p.x
            prefs.panelY = p.y
            refreshBounds()
        }
        c.onPickPoint = { startPick() }

        runCatching { wm.addView(v, p) }.onFailure { return }
        panel = v
        panelParams = p
        controller = c
        bubble?.setPanelOpen(true)

        v.post {
            clampPanel(p)
            runCatching { wm.updateViewLayout(v, p) }
            refreshBounds()
            c.render(Engine.state.value)
            Engine.readOnce()
        }

        previewJob = scope.launch {
            while (isActive) {
                delay(2500)
                if (!Engine.isRunning) Engine.readOnce()
            }
        }

        renderJob = scope.launch {
            Engine.state.collect { st ->
                controller?.render(st)
                bubble?.setActive(st.phase == Engine.Phase.SCANNING)
            }
        }
    }

    private fun hidePanel() {
        val v = panel ?: return
        renderJob?.cancel()
        renderJob = null
        previewJob?.cancel()
        previewJob = null
        runCatching { wm.removeView(v) }
        panel = null
        panelParams = null
        controller = null
        bubble?.setPanelOpen(false)
        refreshBounds()
    }

    private fun clampPanel(p: WindowManager.LayoutParams) {
        val v = panel ?: return
        val m = resources.displayMetrics
        val w = if (v.width > 0) v.width else dp(292)
        val h = if (v.height > 0) v.height else dp(320)
        val maxX = (m.widthPixels - w).coerceAtLeast(0)
        val maxY = (m.heightPixels - h).coerceAtLeast(0)
        if (p.x > maxX) p.x = maxX
        if (p.y > maxY) p.y = maxY
        if (p.x < 0) p.x = 0
        if (p.y < 0) p.y = 0
    }

    private fun startPick() {
        if (picker != null) return
        Engine.stop("Kalibrasi titik")
        hidePanel()

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START

        val v = View(this)
        v.setBackgroundColor(0x33000000)
        v.isClickable = true
        pickStep = 0
        v.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                pickStep++
                if (pickStep == 1) {
                    prefs.genX = e.rawX.toInt()
                    prefs.genY = e.rawY.toInt()
                    toast(getString(R.string.pick_step2))
                } else {
                    prefs.saveX = e.rawX.toInt()
                    prefs.saveY = e.rawY.toInt()
                    removePicker()
                    showPanel()
                    Engine.note(getString(R.string.pick_done))
                }
            }
            true
        }

        runCatching { wm.addView(v, p) }.onFailure { return }
        picker = v
        toast(getString(R.string.pick_step1))
        main.postDelayed(pickTimeout, 20_000L)
    }

    private val pickTimeout = Runnable {
        if (picker != null) {
            removePicker()
            showPanel()
            Engine.note(getString(R.string.pick_cancel))
        }
    }

    private fun removePicker() {
        main.removeCallbacks(pickTimeout)
        val v = picker ?: return
        picker = null
        pickStep = 0
        runCatching { wm.removeView(v) }
    }

    private fun refreshBounds() {
        val list = ArrayList<Rect>(2)
        bubble?.let { b ->
            if (b.isAttachedToWindow) {
                val loc = IntArray(2)
                b.getLocationOnScreen(loc)
                if (b.width > 0 && b.height > 0) {
                    list.add(Rect(loc[0], loc[1], loc[0] + b.width, loc[1] + b.height))
                }
            }
        }
        panel?.let { p ->
            if (p.isAttachedToWindow) {
                val loc = IntArray(2)
                p.getLocationOnScreen(loc)
                if (p.width > 0 && p.height > 0) {
                    list.add(Rect(loc[0], loc[1], loc[0] + p.width, loc[1] + p.height))
                }
            }
        }
        OverlayBounds.update(list)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

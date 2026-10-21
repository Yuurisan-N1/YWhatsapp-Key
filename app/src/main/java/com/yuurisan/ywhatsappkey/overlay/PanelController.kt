package com.yuurisan.ywhatsappkey.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.yuurisan.ywhatsappkey.App
import com.yuurisan.ywhatsappkey.R
import com.yuurisan.ywhatsappkey.core.store.Prefs
import com.yuurisan.ywhatsappkey.engine.Engine
import kotlin.math.abs
import kotlin.math.roundToInt

class PanelController(
    private val ctx: Context,
    private val onClose: () -> Unit
) {

    val view: View = LayoutInflater.from(ctx).inflate(R.layout.panel, null)

    var onPickPoint: (() -> Unit)? = null
    var onRequestTyping: (() -> Unit)? = null
    var onTypingDone: (() -> Unit)? = null
    var dragOrigin: (() -> Pair<Int, Int>)? = null
    var onMove: ((Int, Int) -> Unit)? = null
    var onMoveEnd: (() -> Unit)? = null

    private val prefs = Prefs(ctx)

    private val headerRow = view.findViewById<View>(R.id.headerRow)
    private val body = view.findViewById<View>(R.id.body)
    private val stateDot = view.findViewById<View>(R.id.stateDot)
    private val titleText = view.findViewById<TextView>(R.id.titleText)
    private val phaseText = view.findViewById<TextView>(R.id.phaseText)
    private val minBtn = view.findViewById<ImageView>(R.id.minBtn)
    private val closeBtn = view.findViewById<ImageView>(R.id.closeBtn)
    private val keyText = view.findViewById<TextView>(R.id.keyText)
    private val newKeyBtn = view.findViewById<View>(R.id.newKeyBtn)
    private val targetInput = view.findViewById<EditText>(R.id.targetInput)
    private val targetLabel = view.findViewById<TextView>(R.id.targetLabel)
    private val twinChip = view.findViewById<TextView>(R.id.twinChip)
    private val pickBtn = view.findViewById<TextView>(R.id.pickBtn)
    private val metaText = view.findViewById<TextView>(R.id.metaText)
    private val startBtn = view.findViewById<TextView>(R.id.startBtn)
    private val stopBtn = view.findViewById<TextView>(R.id.stopBtn)
    private val autoChip = view.findViewById<TextView>(R.id.autoChip)
    private val logText = view.findViewById<TextView>(R.id.logText)
    private val devRow = view.findViewById<View>(R.id.devRow)

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    companion object {
        private const val MAX_TARGETS = 16
    }
    private var downRawX = 0f
    private var downRawY = 0f
    private var baseX = 0
    private var baseY = 0
    private var dragging = false

    private var lastKey = "\u0000"
    private var lastPhase: Engine.Phase? = null
    private var lastMeta = "\u0000"
    private var lastLog = "\u0000"
    private var lastAutoOn: Boolean? = null
    private var lastTwinOn: Boolean? = null
    private var lastTarget = "\u0000"

    init {
        keyText.text = ctx.getString(R.string.panel_key_none)
        metaText.text = ""

        targetInput.setText(prefs.targetList.joinToString(" "))
        renderTargetLabel()
        lastTarget = targetInput.text.toString()
        targetInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                onRequestTyping?.invoke()
            } else {
                commitTarget()
                onTypingDone?.invoke()
            }
        }
        targetInput.setOnClickListener { onRequestTyping?.invoke() }
        targetInput.setOnEditorActionListener { _, _, _ ->
            commitTarget()
            false
        }

        startBtn.setOnClickListener {
            commitTarget()
            Engine.start()
        }
        stopBtn.setOnClickListener { Engine.stop() }
        newKeyBtn.setOnClickListener { Engine.manualGenerate() }
        closeBtn.setOnClickListener { onClose() }

        renderAuto()

        twinChip.setOnClickListener {
            prefs.twinMode = !prefs.twinMode
            renderTwin()
        }

        pickBtn.setOnClickListener { onPickPoint?.invoke() }

        autoChip.setOnClickListener {
            prefs.autoSave = !prefs.autoSave
            renderAuto()
            Engine.note(
                ctx.getString(if (prefs.autoSave) R.string.panel_auto_on else R.string.panel_auto_off)
            )
        }

        devRow.setOnClickListener {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(App.DEV_URL))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (t: Throwable) {
                Unit
            }
        }

        minBtn.setOnClickListener { setCollapsed(!prefs.panelCollapsed) }

        attachDrag()
        setCollapsed(prefs.panelCollapsed)
        renderTwin()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag() {
        headerRow.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    val origin = dragOrigin?.invoke()
                    baseX = origin?.first ?: 0
                    baseY = origin?.second ?: 0
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) onMove?.invoke(baseX + dx.roundToInt(), baseY + dy.roundToInt())
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) onMoveEnd?.invoke()
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    fun setCollapsed(collapsed: Boolean) {
        prefs.panelCollapsed = collapsed
        body.visibility = if (collapsed) View.GONE else View.VISIBLE
        minBtn.rotation = if (collapsed) 180f else 0f
    }

    fun focusTarget() {
        targetInput.requestFocus()
        targetInput.setSelection(targetInput.text.length)
    }

    val targetView: View get() = targetInput

    /**
     * Satu kolom boleh memuat banyak target: digit dipotong tiap empat angka,
     * jadi "0986 6036" maupun "09866036" sama-sama menjadi dua target. Twin
     * bukan pengganti daftar ini, twin ditambahkan di atasnya oleh Engine.
     */
    private fun commitTarget() {
        val digits = targetInput.text.toString().filter { it.isDigit() }
        val groups = digits.chunked(4)
            .filter { it.length == 4 }
            .distinct()
            .take(MAX_TARGETS)
        val value = groups.joinToString(" ")
        if (targetInput.text.toString() != value) {
            targetInput.setText(value)
            targetInput.setSelection(value.length)
        }
        prefs.targetList = groups
        lastTarget = value
        renderTargetLabel()
    }

    private fun renderTargetLabel() {
        val n = prefs.targetList.size
        targetLabel.text = if (n > 1) {
            ctx.getString(R.string.panel_target_multi, n)
        } else {
            ctx.getString(R.string.panel_target)
        }
    }

    private fun renderAuto() {
        val on = prefs.autoSave
        if (lastAutoOn == on) return
        lastAutoOn = on
        autoChip.setTextColor(ctx.getColor(if (on) R.color.mint_bright else R.color.text_secondary))
        autoChip.isSelected = on
    }

    private fun renderTwin() {
        val on = prefs.twinMode
        if (lastTwinOn == on) return
        lastTwinOn = on
        twinChip.setTextColor(ctx.getColor(if (on) R.color.mint_bright else R.color.text_secondary))
        twinChip.isSelected = on
    }

    fun render(s: Engine.State) {
        if (s.key != lastKey) {
            lastKey = s.key
            keyText.text = if (s.key.length == 4) s.key else ctx.getString(R.string.panel_key_none)
        }

        if (s.phase != lastPhase) {
            lastPhase = s.phase
            phaseText.setText(
                when (s.phase) {
                    Engine.Phase.IDLE -> R.string.panel_idle
                    Engine.Phase.SCANNING -> R.string.panel_scan
                    Engine.Phase.FOUND -> R.string.panel_found
                    Engine.Phase.STOPPED -> R.string.panel_error
                }
            )
            phaseText.setTextColor(
                ctx.getColor(
                    when (s.phase) {
                        Engine.Phase.FOUND -> R.color.mint_bright
                        Engine.Phase.STOPPED -> R.color.warn
                        else -> R.color.mint
                    }
                )
            )
            stateDot.alpha = if (s.phase == Engine.Phase.SCANNING) 1f else 0.45f
            titleText.setTextColor(
                ctx.getColor(if (s.phase == Engine.Phase.SCANNING) R.color.text_secondary else R.color.text_dim)
            )
        }

        val src = when (s.source) {
            "node" -> "teks"
            "solver" -> "layar"
            else -> "-"
        }
        val meta = "${s.attempts} · ${s.ms} ms · $src"
        if (meta != lastMeta) {
            lastMeta = meta
            metaText.text = meta
        }

        val joined = s.log.joinToString("\n")
        if (joined != lastLog) {
            lastLog = joined
            logText.text = joined
        }

        renderTwin()
        renderAuto()

        val current = targetInput.text.toString()
        val wanted = prefs.targetList.joinToString(" ")
        if (!targetInput.isFocused && current != wanted && wanted != lastTarget) {
            lastTarget = wanted
            targetInput.setText(wanted)
        }
    }
}

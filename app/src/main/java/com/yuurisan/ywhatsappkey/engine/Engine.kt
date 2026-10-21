package com.yuurisan.ywhatsappkey.engine

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.yuurisan.ywhatsappkey.R
import com.yuurisan.ywhatsappkey.core.store.HistoryStore
import com.yuurisan.ywhatsappkey.core.store.Prefs
import com.yuurisan.ywhatsappkey.input.TapService
import com.yuurisan.ywhatsappkey.overlay.OverlayBounds
import com.yuurisan.ywhatsappkey.vision.KeyScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

object Engine {

    enum class Phase { IDLE, SCANNING, FOUND, STOPPED }

    data class State(
        val phase: Phase = Phase.IDLE,
        val key: String = "",
        val attempts: Int = 0,
        val ms: Int = 0,
        val confidence: Int = 0,
        val source: String = "",
        val log: List<String> = emptyList(),
        val hasGenerated: Boolean = false
    )

    private const val TAP_WAIT_MS = 700L
    private const val MISS_WAIT_MS = 400L
    private const val GIVE_UP_MS = 25_000L
    private const val MAX_ATTEMPTS = 4000
    private const val COUNTER_EMIT_MS = 200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var prefs: Prefs
    private lateinit var history: HistoryStore
    private var appContext: Context? = null
    private var job: Job? = null
    private var keyBounds: Rect? = null
    private fun stamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    @Volatile
    private var stopRequested = false
    private var lastCounterEmit = 0L
    private var lastLogMsg = ""
    private var repeatCount = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = Prefs(context)
        history = HistoryStore(context)
        _state.value = _state.value.copy(hasGenerated = prefs.hasGenerated)
        DigitWarm.start()
    }

    val isRunning: Boolean get() = !stopRequested && job?.isActive == true

    fun start() {
        if (isRunning) return
        if (!TapService.isReady) {
            push("Aksesibilitas belum aktif")
            return
        }
        stopRequested = false
        val targets = resolveTargets()
        keyBounds = null
        lastCounterEmit = 0L
        lastLogMsg = ""
        repeatCount = 0
        _state.value = _state.value.copy(
            phase = Phase.SCANNING,
            attempts = 0,
            ms = 0,
            log = emptyList()
        )
        push(
            if (targets.isEmpty()) {
                appContext?.getString(R.string.log_no_target) ?: "Target kosong"
            } else {
                appContext?.getString(R.string.log_start_multi, targets.size)
                    ?: "Mesin dimulai, ${targets.size} target"
            }
        )
        job = scope.launch { loop(targets) }
    }

    fun stop(reason: String? = null) {
        stopRequested = true
        job?.cancel()
        job = null
        if (reason != null) push(reason)
        _state.value = _state.value.copy(phase = Phase.STOPPED)
    }

    fun reset() {
        stop()
        _state.value = State(hasGenerated = prefs.hasGenerated)
    }

    private fun resolveTargets(): Set<String> {
        val explicit = prefs.targets.mapNotNull { normalize(it) }.toSet()
        val twin = if (prefs.twinMode) prefs.twinSet() else emptySet()
        return explicit + twin
    }

    private fun normalize(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length == 4) digits else null
    }

    private fun genPoint(): Rect? {
        if (!prefs.hasGenPoint) return null
        val x = prefs.genX
        val y = prefs.genY
        return Rect(x - 1, y - 1, x + 1, y + 1)
    }

    private fun covered(r: Rect, exclude: List<Rect>): Boolean {
        for (e in exclude) {
            if (r.left < e.right && r.right > e.left && r.top < e.bottom && r.bottom > e.top) return true
        }
        return false
    }

    private fun savePoint(): Rect? {
        if (!prefs.hasSavePoint) return null
        val x = prefs.saveX
        val y = prefs.saveY
        return Rect(x - 1, y - 1, x + 1, y + 1)
    }

    private suspend fun loop(targets: Set<String>) {
        var attempts = 0
        var lastProgress = SystemClock.elapsedRealtime()
        var prevDigits: String? = null
        var lastTapDigits: String? = null
        var stuck = 0
        var blind = 0

        while (job?.isActive == true && !stopRequested) {
            val t0 = SystemClock.elapsedRealtime()
            val svc = TapService.instance
            if (svc == null) {
                push("Aksesibilitas belum aktif")
                stopRequested = true
                _state.value = _state.value.copy(phase = Phase.STOPPED)
                job = null
                return
            }

            val exclude = OverlayBounds.rects()
            val nodeKey = svc.readKey()
            var digits = nodeKey?.digits
            var source = if (digits != null) "node" else ""
            var conf = if (digits != null) 100 else 0

            if (digits == null) {
                val bmp = grab()
                if (bmp != null) {
                    val hit = KeyScanner.scan(bmp, keyBounds, exclude)
                    bmp.recycle()
                    if (hit != null) {
                        digits = hit.digits
                        source = "solver"
                        conf = (hit.confidence * 100).toInt()
                        keyBounds = expand(hit.box, 1.6f)
                        svc.setKeyBox(hit.box)
                    }
                }
            } else {
                nodeKey?.let {
                    keyBounds = expand(it.bounds, 1.6f)
                    svc.setKeyBox(it.bounds)
                }
            }

            attempts++
            val ms = (SystemClock.elapsedRealtime() - t0).toInt().coerceAtLeast(0)

            if (digits == null) blind++ else blind = 0

            if (digits != null && digits != prevDigits) {
                lastProgress = SystemClock.elapsedRealtime()
            }
            if (lastTapDigits != null && digits == null) {
                lastTapDigits = null
            }
            if (digits != null && lastTapDigits != null) {
                if (digits == lastTapDigits) {
                    stuck++
                } else {
                    stuck = 0
                }
                lastTapDigits = null
                if (stuck >= 8) {
                    push(appContext?.getString(R.string.log_stuck, stuck) ?: "Angka tidak berubah")
                    stopRequested = true
                    _state.value = _state.value.copy(phase = Phase.STOPPED)
                    job = null
                    return
                }
            }
            prevDigits = digits

            emitCounters(
                _state.value.copy(
                    phase = Phase.SCANNING,
                    key = digits ?: _state.value.key,
                    attempts = attempts,
                    ms = ms,
                    confidence = conf,
                    source = source
                )
            )
            if (stopRequested) return

            if (digits != null && (targets.isEmpty() || targets.contains(digits))) {
                if (confirm(svc, digits, exclude)) {
                    onFound(svc, digits)
                    return
                }
                delay(150)
                continue
            }

            if (attempts > MAX_ATTEMPTS) {
                push("Batas percobaan tercapai, mesin berhenti")
                stopRequested = true
                _state.value = _state.value.copy(phase = Phase.STOPPED)
                job = null
                return
            }

            /**
             * Tanpa angka yang terbaca, menekan tombol berarti menembak dalam
             * gelap: target bisa lewat tanpa pernah terlihat. Berhenti saja.
             */
            if (blind >= 3) {
                push(appContext?.getString(R.string.log_blind) ?: "Angka tidak terbaca")
                stopRequested = true
                _state.value = _state.value.copy(phase = Phase.STOPPED)
                job = null
                return
            }

            val point = genPoint()
            if (point != null && covered(point, exclude)) {
                push("Titik tombol tertutup panel, geser panel")
                delay(MISS_WAIT_MS)
                continue
            }

            val btn = svc.findGenerateButton(point, keyBounds)
            if (btn == null) {
                push("Tombol tidak ditemukan")
                if (SystemClock.elapsedRealtime() - lastProgress > GIVE_UP_MS) {
                    push("Tombol tidak ada, mesin berhenti")
                    stopRequested = true
                    _state.value = _state.value.copy(phase = Phase.STOPPED)
                    job = null
                    return
                }
                delay(MISS_WAIT_MS)
                continue
            }

            if (!svc.tap(btn)) {
                push("Tombol tidak bisa ditekan")
                delay(MISS_WAIT_MS)
                continue
            }

            if (!prefs.hasGenerated) {
                prefs.hasGenerated = true
                _state.value = _state.value.copy(hasGenerated = true)
            }
            push("Menekan kunci baru")
            lastTapDigits = digits
            delay(TAP_WAIT_MS)
        }
    }

    private suspend fun confirm(svc: TapService, key: String, exclude: List<Rect>): Boolean {
        val node = svc.readKey()?.digits
        if (node != null) return node == key
        val bmp = grab() ?: return false
        val hit = KeyScanner.scan(bmp, keyBounds, exclude)
        bmp.recycle()
        return hit != null && hit.digits == key
    }

    private fun onFound(svc: TapService, key: String) {
        _state.value = _state.value.copy(phase = Phase.FOUND, key = key)
        push("Kunci $key cocok")
        prefs.lastKey = key
        buzz()

        if (!prefs.autoSave) {
            history.add(key)
            push(appContext?.getString(R.string.log_hold, key) ?: "Auto simpan mati")
            stopRequested = true
            job = null
            return
        }

        val save = svc.findSaveButton(savePoint())
        if (save == null) {
            push("Tombol simpan tidak ditemukan")
            stopRequested = true
            job = null
            return
        }
        svc.tap(save)
        history.add(key)
        push("Kunci $key disimpan")
        stopRequested = true
        job = null
    }

    fun manualGenerate() {
        val svc = TapService.instance ?: return
        val btn = svc.findGenerateButton(genPoint(), keyBounds)
        if (btn == null) {
            push("Tombol tidak ditemukan")
            return
        }
        svc.tap(btn)
        push("Menekan kunci baru")
        scope.launch {
            delay(650)
            readOnce()
        }
    }

    fun readOnce() {
        scope.launch {
            val svc = TapService.instance ?: return@launch
            val exclude = OverlayBounds.rects()
            val nodeKey = svc.readKey()
            if (nodeKey != null) {
                _state.value = _state.value.copy(key = nodeKey.digits, source = "node", confidence = 100)
                return@launch
            }
            val bmp = grab() ?: run {
                _state.value = _state.value.copy(key = "", source = "", confidence = 0)
                return@launch
            }
            val hit = KeyScanner.scan(bmp, keyBounds, exclude)
            bmp.recycle()
            if (hit == null) {
                _state.value = _state.value.copy(key = "", source = "", confidence = 0)
            } else {
                _state.value = _state.value.copy(
                    key = hit.digits,
                    source = "solver",
                    confidence = (hit.confidence * 100).toInt()
                )
            }
        }
    }

    private suspend fun grab(): android.graphics.Bitmap? = suspendCancellableCoroutine { cont ->
        val svc = TapService.instance
        if (svc == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        svc.screenshot { bmp -> if (cont.isActive) cont.resume(bmp) }
    }

    private fun expand(r: Rect, factor: Float): Rect {
        val cx = r.exactCenterX()
        val cy = r.exactCenterY()
        val hw = r.width() * factor / 2f
        val hh = r.height() * factor / 2f
        return Rect(
            (cx - hw).toInt(),
            (cy - hh).toInt(),
            (cx + hw).toInt(),
            (cy + hh).toInt()
        )
    }

    private fun emitCounters(next: State) {
        if (stopRequested) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCounterEmit < COUNTER_EMIT_MS) return
        lastCounterEmit = now
        _state.value = next
    }

    private fun push(line: String) {
        val cur = _state.value.log
        if (line == lastLogMsg) {
            repeatCount++
            val entry = "${stamp()}  $line (x$repeatCount)"
            val next = if (cur.isEmpty()) listOf(entry) else cur.dropLast(1) + entry
            _state.value = _state.value.copy(log = next.takeLast(3))
        } else {
            lastLogMsg = line
            repeatCount = 1
            val entry = "${stamp()}  $line"
            _state.value = _state.value.copy(log = (cur + entry).takeLast(3))
        }
    }

    private fun buzz() {
        if (!prefs.vibrate) return
        val ctx = appContext ?: return
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun historyEntries(): List<HistoryStore.Entry> = if (::history.isInitialized) history.all() else emptyList()

    fun note(line: String) {
        push(line)
    }
}

package com.yuurisan.ywhatsappkey.engine

import com.yuurisan.ywhatsappkey.vision.DigitSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

object DigitWarm {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { DigitSolver.warmUp() }
    }
}

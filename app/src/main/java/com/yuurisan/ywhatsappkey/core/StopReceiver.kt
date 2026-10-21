package com.yuurisan.ywhatsappkey.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yuurisan.ywhatsappkey.engine.Engine
import com.yuurisan.ywhatsappkey.overlay.FloatingService

class StopReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "com.yuurisan.ywhatsappkey.action.STOP"
        const val ACTION_TOGGLE = "com.yuurisan.ywhatsappkey.action.TOGGLE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                Engine.stop("Dihentikan dari notifikasi")
                FloatingService.hidePanelNow()
            }
            ACTION_TOGGLE -> FloatingService.togglePanelNow()
        }
    }
}

package com.yuurisan.ywhatsappkey

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.yuurisan.ywhatsappkey.engine.Engine

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        channel()
        Engine.init(this)
    }

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            ch.setShowBadge(false)
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        const val CHANNEL = "ywk_panel"
        const val DEV_URL = "https://github.com/Yuurisan-N1"
    }
}

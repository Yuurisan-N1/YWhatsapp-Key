package com.yuurisan.ywhatsappkey

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.yuurisan.ywhatsappkey.input.TapService
import com.yuurisan.ywhatsappkey.overlay.FloatingService

class MainActivity : Activity() {

    private lateinit var stateAccess: TextView
    private lateinit var stateOverlay: TextView
    private lateinit var stateBattery: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stateAccess = findViewById(R.id.stateAccess)
        stateOverlay = findViewById(R.id.stateOverlay)
        stateBattery = findViewById(R.id.stateBattery)

        findViewById<View>(R.id.rowAccess).setOnClickListener {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.rowOverlay).setOnClickListener {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<View>(R.id.rowBattery).setOnClickListener {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            if (!open(i)) open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        findViewById<View>(R.id.startBtn).setOnClickListener { launchPanel() }
        findViewById<View>(R.id.devRow).setOnClickListener {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(App.DEV_URL)))
        }

        askNotifications()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        pill(stateAccess, accessibilityOn())
        pill(stateOverlay, Settings.canDrawOverlays(this))
        pill(stateBattery, ignoringBattery())
    }

    private fun pill(tv: TextView, on: Boolean) {
        tv.setText(if (on) R.string.state_on else R.string.state_off)
        tv.setTextColor(ContextCompat.getColor(this, if (on) R.color.mint else R.color.text_dim))
        tv.background = ContextCompat.getDrawable(this, R.drawable.bg_chip)
        tv.isSelected = on
    }

    private fun accessibilityOn(): Boolean {
        if (TapService.isReady) return true
        val expected = "$packageName/${TapService::class.java.name}"
        val flat = "$packageName/.input.TapService"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, true) || it.equals(flat, true) }
    }

    private fun ignoringBattery(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun launchPanel() {
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.err_overlay))
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!accessibilityOn()) {
            toast(getString(R.string.err_access))
        }
        FloatingService.start(this, true)
        moveTaskToBack(true)
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
    }

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (t: Throwable) {
        false
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

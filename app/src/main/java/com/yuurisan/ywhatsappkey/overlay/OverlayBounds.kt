package com.yuurisan.ywhatsappkey.overlay

import android.graphics.Rect

/**
 * Bounds layar dari view overlay milik aplikasi ini sendiri.
 * Dipakai supaya solver tidak membaca angka dari panel sendiri.
 */
object OverlayBounds {

    @Volatile
    private var current: List<Rect> = emptyList()

    fun update(rects: List<Rect>) {
        current = if (rects.isEmpty()) emptyList() else rects
    }

    fun rects(): List<Rect> = current
}

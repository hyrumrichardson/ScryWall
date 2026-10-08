package com.hyrumrichardson.scrywall

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object WallpaperRenderer {

    /** Full physical screen size in portrait orientation (width, height). */
    fun screenSize(context: Context): Pair<Int, Int> {
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val p = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(p)
        return min(p.x, p.y) to max(p.x, p.y)
    }

    /**
     * Draws [src] onto a screen-shaped canvas using [mode].
     * [scale] shrinks the output (used for the on-screen preview) while keeping
     * the result proportional to the real wallpaper.
     */
    fun render(src: Bitmap, screenW: Int, screenH: Int, mode: ScaleMode, scale: Float = 1f): Bitmap {
        val w = max(1, (screenW * scale).roundToInt())
        val h = max(1, (screenH * scale).roundToInt())
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val sw = src.width.toFloat()
        val sh = src.height.toFloat()

        fun drawAt(s: Float, bmp: Bitmap = src, bw: Float = sw, bh: Float = sh) {
            val dw = bw * s
            val dh = bh * s
            val left = (w - dw) / 2f
            val top = (h - dh) / 2f
            canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
        }

        when (mode) {
            ScaleMode.FILL -> drawAt(max(w / sw, h / sh))
            ScaleMode.FIT -> drawAt(min(w / sw, h / sh))
            ScaleMode.FIT_BLUR -> {
                // Cheap blur: shrink the image to a few pixels, then let bilinear
                // filtering smear it back up to full size.
                val tinyW = max(1, (sw / 28f).roundToInt())
                val tinyH = max(1, (sh / 28f).roundToInt())
                val tiny = Bitmap.createScaledBitmap(src, tinyW, tinyH, true)
                drawAt(max(w / tinyW.toFloat(), h / tinyH.toFloat()), tiny, tinyW.toFloat(), tinyH.toFloat())
                canvas.drawColor(Color.argb(90, 0, 0, 0))
                tiny.recycle()
                drawAt(min(w / sw, h / sh))
            }
            ScaleMode.STRETCH -> canvas.drawBitmap(src, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
            ScaleMode.CENTER -> drawAt(scale)
        }
        return out
    }
}

object WallpaperSetter {
    /**
     * Picks a random card from the saved search or deck and sets it as the wallpaper.
     * Returns the card's name.
     */
    suspend fun changeNow(context: Context): String {
        val prefs = Prefs(context)
        val query = prefs.activeQuery.ifBlank { prefs.query }
        if (query.isBlank()) throw SourceException("No search saved yet.")

        val card = prefs.activeSource.random(query)
        val url = card.imageUrl(prefs.style) ?: throw SourceException("Card has no image.")
        val src = Scryfall.downloadBitmap(url)
        val (w, h) = WallpaperRenderer.screenSize(context)

        withContext(Dispatchers.Default) {
            val out = WallpaperRenderer.render(src, w, h, prefs.scale)
            src.recycle()
            WallpaperManager.getInstance(context).setBitmap(out, null, true, prefs.target.flags)
            out.recycle()
        }
        prefs.lastCard = card.name
        prefs.lastChanged = System.currentTimeMillis()
        return card.name
    }
}

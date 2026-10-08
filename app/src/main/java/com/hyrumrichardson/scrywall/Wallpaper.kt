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

        fun drawAt(s: Float) {
            val dw = sw * s
            val dh = sh * s
            val left = (w - dw) / 2f
            val top = (h - dh) / 2f
            canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), paint)
        }

        when (mode) {
            ScaleMode.FILL -> drawAt(max(w / sw, h / sh))
            ScaleMode.FIT -> drawAt(min(w / sw, h / sh))
            ScaleMode.FIT_BLUR -> {
                // Blur a 1/8-size screen-filling copy (cheap, and blur loses no detail
                // that matters), then let bilinear filtering scale it back up.
                val smallW = max(1, w / 8)
                val smallH = max(1, h / 8)
                val small = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
                Canvas(small).apply {
                    drawColor(Color.BLACK)
                    val s = max(smallW / sw, smallH / sh)
                    val dw = sw * s
                    val dh = sh * s
                    val l = (smallW - dw) / 2f
                    val t = (smallH - dh) / 2f
                    drawBitmap(src, null, RectF(l, t, l + dw, t + dh), paint)
                }
                boxBlur(small, radius = max(1, smallW / 14))
                canvas.drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
                small.recycle()
                canvas.drawColor(Color.argb(90, 0, 0, 0))
                drawAt(min(w / sw, h / sh))
            }
            ScaleMode.STRETCH -> canvas.drawBitmap(src, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
            ScaleMode.CENTER -> drawAt(scale)
        }
        return out
    }

    /** Three box-blur passes in place, which together look close to a Gaussian blur. */
    private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int = 3) {
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        repeat(passes) {
            blurRows(a, b, w, h, radius) // rows of a -> columns of b
            blurRows(b, a, h, w, radius) // and back again, so a is upright
        }
        bmp.setPixels(a, 0, w, 0, 0, w, h)
    }

    /** Blurs each row of [src] (w x h) and writes the result transposed into [dst] (h x w). */
    private fun blurRows(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                sr += p shr 16 and 0xFF
                sg += p shr 8 and 0xFF
                sb += p and 0xFF
            }
            for (x in 0 until w) {
                dst[x * h + y] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                // Slide the window: add the pixel entering on the right, drop the one leaving on the left.
                val add = src[row + min(x + r + 1, w - 1)]
                val sub = src[row + max(x - r, 0)]
                sr += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
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

        val card = when (prefs.activeSource) {
            Source.SCRYFALL -> Scryfall.random(query)
            // Keep the saved name current in case the deck is renamed on Moxfield.
            Source.MOXFIELD -> Moxfield.deck(query).also { prefs.activeDeckName = it.name }.cards.random()
        }
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

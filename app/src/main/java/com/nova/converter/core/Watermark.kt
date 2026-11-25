package com.nova.converter.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.File

/**
 * Watermark text ko pehle ek PNG me render karta hai, phir FFmpeg `overlay` filter
 * se video pe composite karta hai.
 *
 * Ye FFmpeg ke `drawtext` filter ka replacement hai — hamari bundled FFmpeg build
 * (ffmpeg-kit-full-gpl 7.1.6) me libfreetype ke bawajood `drawtext` compile hua hi
 * nahi hai. Isliye text rendering Android Canvas se hoti hai (system font + exact
 * color/size control), FFmpeg sirf overlay karta hai — koi extra filter dependency nahi.
 */
object Watermark {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * @param videoWidth  source video ka width (text ko fit karne ke liye)
     * @param videoHeight source video ka height (font size isi se proportional hai)
     * @return PNG file ka absolute path, ya null (text blank / context missing / OOM)
     */
    fun render(
        text: String,
        videoWidth: Int,
        videoHeight: Int,
        sizePercent: Float,
        colorName: String
    ): String? {
        val ctx = appContext ?: return null
        val label = text.trim()
        if (label.isEmpty()) return null

        val vh = videoHeight.coerceIn(64, 7680)
        val vw = videoWidth.coerceIn(64, 7680)
        val pct = sizePercent.coerceIn(2f, 12f)
        val maxWidth = (vw * 0.9f).toInt().coerceAtLeast(64)
        val rgb = textColor(colorName)

        return try {
            var textSize = (vh * pct / 100f).coerceAtLeast(8f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = rgb
                this.textSize = textSize
            }
            val bounds = Rect()

            // lambi text ko video width me fit karo (kitni bhi baar chhota karke)
            var guard = 0
            paint.getTextBounds(label, 0, label.length, bounds)
            while (bounds.width() > maxWidth && guard < 24 && textSize > 6f) {
                textSize *= 0.85f
                paint.textSize = textSize
                paint.getTextBounds(label, 0, label.length, bounds)
                guard++
            }

            val pad = (textSize * 0.20f).toInt().coerceAtLeast(4)
            val w = (bounds.width() + pad * 2).coerceAtLeast(8)
            val h = (bounds.height() + pad * 2).coerceAtLeast(8)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // drawtext ka box=1:boxcolor=black@0.3 equivalent
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = 0x4D000000 })

            val x = pad - bounds.left.toFloat()
            val y = h / 2f - bounds.exactCenterY()
            canvas.drawText(label, x, y, paint)

            val dir = File(ctx.cacheDir, "wm").apply { mkdirs() }
            val file = File(dir, "wm_${label.hashCode()}_${w}x${h}_${pct.toInt()}.png")
            file.outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bmp.recycle()
            file.absolutePath
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun textColor(colorName: String): Int {
        val key = colorName.lowercase()
        val alpha = if (key == "black") 0xD9 else 0xE6   // 0.85 / 0.9
        val rgb = when (key) {
            "black" -> 0x000000
            "yellow" -> 0xFFEB3B
            "red" -> 0xF44336
            else -> 0xFFFFFF
        }
        return (alpha shl 24) or rgb
    }
}

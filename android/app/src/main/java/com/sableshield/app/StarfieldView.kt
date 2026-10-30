package com.sableshield.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.random.Random

/**
 * The site's starfield background, on Android: a soft radial centre glow
 * (#1A1A1A fading to #050505 at 0.6 opacity) plus slow-drifting,
 * twinkling glow sprites — white-hot cores in white, cyan (0,240,255)
 * and amethyst (157,78,221), exactly like the site's canvas starfield.
 * Pure decoration — zero audio, zero network.
 */
class StarfieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Star(
        var x: Float,
        var y: Float,
        val r: Float,
        val speed: Float,
        val drift: Float,
        var phase: Float,
        val color: Int
    )

    private val stars = mutableListOf<Star>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glow: RadialGradient? = null
    private val glowPaint = Paint()

    // Pre-rendered glow sprites, one per star colour (site technique).
    private val sprites = mutableMapOf<Int, Bitmap>()

    private fun makeGlow(rgb: Int): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val gradient = RadialGradient(
            32f, 32f, 32f,
            intArrayOf(
                Color.WHITE,
                Color.argb(230, Color.red(rgb), Color.green(rgb), Color.blue(rgb)),
                Color.argb(97, Color.red(rgb), Color.green(rgb), Color.blue(rgb)),
                Color.argb(0, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            ),
            floatArrayOf(0f, 0.18f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        val p = Paint().apply { shader = gradient }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        return bmp
    }

    private val animator = object : Runnable {
        override fun run() {
            for (s in stars) {
                s.y += s.speed
                s.x += s.drift
                if (s.y > height + 10) {
                    s.y = -10f
                    s.x = Random.nextFloat() * width
                }
                if (s.x > width + 10) s.x = -10f
                if (s.x < -10) s.x = width + 10f
                s.phase += 0.05f
            }
            invalidate()
            postDelayed(this, 50)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        glow = RadialGradient(
            w / 2f, h / 2f, min(w, h).toFloat(),
            intArrayOf(Color.parseColor("#1A1A1A"), Color.parseColor("#050505")),
            floatArrayOf(0f, 0.7f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = glow
        glowPaint.alpha = 153 // 0.6 like the site's hero overlay
        val colors = listOf(
            Color.WHITE,
            Color.rgb(0, 240, 255),
            Color.rgb(157, 78, 221)
        )
        for (col in colors) {
            if (!sprites.containsKey(col)) sprites[col] = makeGlow(col)
        }
        if (stars.isEmpty()) {
            repeat(90) {
                stars.add(
                    Star(
                        x = Random.nextFloat() * w,
                        y = Random.nextFloat() * h,
                        r = 0.5f + Random.nextFloat() * 1.4f,
                        speed = 0.3f + Random.nextFloat() * 0.9f,
                        drift = (Random.nextFloat() - 0.5f) * 0.08f,
                        phase = Random.nextFloat() * 6.28f,
                        color = colors.random()
                    )
                )
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(animator)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animator)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#050505"))
        glow?.let { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint) }
        for (s in stars) {
            val twinkle = (0.7f + 0.3f * (0.5f + 0.5f * kotlin.math.sin(s.phase))).coerceIn(0f, 1f)
            val sprite = sprites[s.color] ?: continue
            val size = s.r * 9f
            paint.alpha = (255 * twinkle).toInt()
            canvas.drawBitmap(sprite, null, android.graphics.RectF(s.x - size / 2, s.y - size / 2, s.x + size / 2, s.y + size / 2), paint)
        }
        paint.alpha = 255
    }
}

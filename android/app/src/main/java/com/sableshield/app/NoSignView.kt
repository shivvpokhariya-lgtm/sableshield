package com.sableshield.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The site's no-sign logo, on Android — beside /shield, slightly bigger
 * than the hero text's x-height, dark tone, living only beside the shield:
 *
 *  - 0.8s: the runner dot fades in at the top of the track (the start line).
 *  - 3.2s: the dot runs one full lap (1.8s), leaving the hot-pink ->
 *    dark-red gradient trace behind it; the dark disc fades in as it runs.
 *  - lap complete: the slash draws in (0.5s), the runner fades out, and
 *    the logo goes live forever: glow pulse (3s) + breathe (3.6s) +
 *    slow rotating dashed halo (14s).
 *
 * Pure decoration — zero audio, zero network.
 */
class NoSignView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A0A0A")
        style = Paint.Style.FILL
    }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val runnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2E63")
        style = Paint.Style.FILL
    }
    private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#59FF2E63")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 6f * density), 0f)
    }

    // 0 = waiting for the lap, 1 = tracing, 2 = live
    private var phase = 0
    private var dotAlpha = 0f
    private var lapProgress = 0f
    private var slashProgress = 0f
    private var liveStart = 0L

    private val liveTicker = object : Runnable {
        override fun run() {
            if (phase == 2) {
                invalidate()
                postDelayed(this, 50)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Dot fades in at 0.8s, with /shield.
        postDelayed({
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600
                addUpdateListener { dotAlpha = it.animatedValue as Float; invalidate() }
                start()
            }
        }, 800)
        // Lap starts at 3.2s, runs 1.8s.
        postDelayed({
            phase = 1
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1800
                interpolator = LinearInterpolator()
                addUpdateListener { lapProgress = it.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        phase = 2
                        liveStart = System.currentTimeMillis()
                        ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = 500
                            addUpdateListener {
                                slashProgress = it.animatedValue as Float
                                invalidate()
                            }
                            start()
                        }
                        post(liveTicker)
                    }
                })
                start()
            }
        }, 3200)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(liveTicker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.88f
        if (r <= 0) return

        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor("#FF2E63"), Color.parseColor("#7F1D1D"),
            Shader.TileMode.CLAMP
        )
        tracePaint.shader = gradient
        slashPaint.shader = gradient

        var breathe = 1f
        var glow = 0f
        var haloAngle = 0f
        if (phase == 2 && liveStart > 0) {
            val t = (System.currentTimeMillis() - liveStart) / 1000f
            breathe = 1f + 0.04f * (0.5f + 0.5f * sin(2 * Math.PI * t / 3.6).toFloat())
            glow = 0.5f + 0.5f * sin(2 * Math.PI * t / 3.0).toFloat()
            haloAngle = (t / 14f * 360f) % 360f
        }

        canvas.save()
        canvas.scale(breathe, breathe, cx, cy)

        // Dark disc — fades in while tracing, solid once live.
        val discAlpha = when (phase) {
            0 -> 0f
            1 -> min(1f, lapProgress * 2f)
            else -> 1f
        }
        discPaint.alpha = (255 * discAlpha).toInt()
        canvas.drawCircle(cx, cy, r * 0.94f, discPaint)

        // Gradient trace arc, from the top, clockwise.
        tracePaint.alpha = 255
        if (phase == 2) {
            tracePaint.setShadowLayer(18f + 14f * glow, 0f, 0f, Color.parseColor("#FF2E63"))
        } else {
            tracePaint.clearShadowLayer()
        }
        val sweep = if (phase == 2) 360f else 360f * lapProgress
        if (sweep > 0f) {
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, sweep, false, tracePaint)
        }
        tracePaint.clearShadowLayer()

        // Runner dot — at the top while waiting, travelling during the lap,
        // fading out once live.
        if (phase != 2 && dotAlpha > 0f) {
            val ang = -Math.PI / 2 + lapProgress * 2 * Math.PI
            val dx = cx + r * cos(ang).toFloat()
            val dy = cy + r * sin(ang).toFloat()
            runnerPaint.alpha = (255 * dotAlpha).toInt()
            runnerPaint.setShadowLayer(16f, 0f, 0f, Color.parseColor("#FF2E63"))
            canvas.drawCircle(dx, dy, 7f * density, runnerPaint)
            runnerPaint.clearShadowLayer()
        }

        // Slash — draws in over 0.5s once the lap completes.
        if (slashProgress > 0f) {
            val ex = 0.39f * r
            val ey = 0.39f * r
            val sx = cx - ex
            val sy = cy - ey
            val tx = cx + ex
            val ty = cy + ey
            canvas.drawLine(
                sx, sy,
                sx + (tx - sx) * slashProgress,
                sy + (ty - sy) * slashProgress,
                slashPaint
            )
        }

        // Rotating dashed halo, live only.
        if (phase == 2) {
            canvas.save()
            canvas.rotate(haloAngle, cx, cy)
            canvas.drawCircle(cx, cy, r * 1.09f, haloPaint)
            canvas.restore()
        }

        canvas.restore()
    }
}

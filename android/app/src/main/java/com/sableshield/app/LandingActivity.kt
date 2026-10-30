package com.sableshield.app

import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * The app's front door — deliberately almost empty, exactly like the
 * site's hero and nothing else: the /shield wordmark with the live
 * no-sign logo, and one button below it. No tagline, no badges, no
 * overlay lines, no info at all. The button slides into the main app
 * (which asks for permissions) with a smooth page transition.
 */
class LandingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)

        val heroText = findViewById<TextView>(R.id.landingHeroText)
        val logoView = findViewById<View>(R.id.landingNoSign)
        val useButton = findViewById<Button>(R.id.btnUseSableShield)

        // Same white -> silver gradient as the site's /shield hero.
        heroText.post {
            heroText.paint.shader = LinearGradient(
                0f, 0f, heroText.width.toFloat(), 0f,
                intArrayOf(
                    Color.WHITE,
                    Color.parseColor("#E6E6E6"),
                    Color.parseColor("#C0C0C0")
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            heroText.invalidate()
        }

        // Site-style staged entrance: shield rises first, button follows.
        val density = resources.displayMetrics.density
        for ((view, delay) in listOf(heroText to 800L, logoView to 800L, useButton to 1600L)) {
            view.alpha = 0f
            view.translationY = 24f * density
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(1600)
                .start()
        }

        useButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }
    }
}

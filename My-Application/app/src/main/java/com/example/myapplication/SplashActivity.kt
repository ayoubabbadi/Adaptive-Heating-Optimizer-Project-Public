package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.logoImage)
        val appName = findViewById<TextView>(R.id.appName)



        logo.alpha = 1f
        logo.scaleX = 0f
        logo.scaleY = 0f
        logo.rotation = -180f


        appName.alpha = 0f
        appName.translationY = 50f


        logo.animate().apply {

            startDelay = 500

            duration = 1000
            scaleX(1f)
            scaleY(1f)
            rotation(0f)
            interpolator = OvershootInterpolator(2.0f)
        }.start()


        appName.animate().apply {

            startDelay = 800

            duration = 800
            alpha(1f)
            translationY(0f)
            interpolator = OvershootInterpolator(1.0f)


            withEndAction {

                appName.postDelayed({
                    val intent = Intent(this@SplashActivity, LaunchActivity::class.java)
                    startActivity(intent)

                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }, 1000)
            }
        }.start()
    }
}
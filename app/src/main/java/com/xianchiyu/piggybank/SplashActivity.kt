package com.xianchiyu.piggybank

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import android.widget.TextView

class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 透明状态栏，图片延伸到顶部
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        window.statusBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_splash)

        val progressBar = findViewById<ProgressBar>(R.id.splash_progress)
        val percentText = findViewById<TextView>(R.id.splash_percent)

        // 进度条走完后直接跳转，不做空等
        val animator = ValueAnimator.ofInt(0, 100)
        animator.duration = 1500
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { anim ->
            val progress = anim.animatedValue as Int
            progressBar.progress = progress
            percentText.text = "$progress%"
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                PiggyData.init(this@SplashActivity)
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
            }
        })
        animator.start()
    }
}
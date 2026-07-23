package com.xianchiyu.piggybank

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import android.content.Intent

class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progressBar = findViewById<ProgressBar>(R.id.splash_progress)
        val percentText = findViewById<TextView>(R.id.splash_percent)

        // 进度动画：模拟 WebView / 资源加载进度
        val animator = ValueAnimator.ofInt(0, 100)
        animator.duration = 1200
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { anim ->
            val progress = anim.animatedValue as Int
            progressBar.progress = progress
            percentText.text = "$progress%"
        }
        animator.start()

        // 动画结束后跳转
        Handler(Looper.getMainLooper()).postDelayed({
            PiggyData.init(this)
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1400)
    }
}
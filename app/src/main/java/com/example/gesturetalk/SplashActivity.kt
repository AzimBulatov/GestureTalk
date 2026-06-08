package com.example.gesturetalk

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var logoIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var appSlogan: TextView
    private lateinit var loadingDots: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        logoIcon = findViewById(R.id.logoIcon)
        appName = findViewById(R.id.appName)
        appSlogan = findViewById(R.id.appSlogan)
        loadingDots = findViewById(R.id.loadingDots)

        // Начальная прозрачность
        logoIcon.alpha = 0f
        appName.alpha = 0f
        appSlogan.alpha = 0f
        loadingDots.alpha = 0f

        startAnimations()
    }

    private fun startAnimations() {
        // Начальное масштабирование
        logoIcon.scaleX = 0.3f
        logoIcon.scaleY = 0.3f
        
        // Анимация иконки: появление + масштабирование
        logoIcon.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                // Пульсация иконки
                startPulseAnimation()
                // Показываем название
                animateAppName()
            }
            .start()
    }

    private fun startPulseAnimation() {
        logoIcon.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                logoIcon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(600)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun animateAppName() {
        appName.translationY = 30f
        
        appName.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                animateSlogan()
            }
            .start()
    }

    private fun animateSlogan() {
        appSlogan.translationY = 20f
        
        appSlogan.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                animateLoadingDots()
            }
            .start()
    }

    private fun animateLoadingDots() {
        loadingDots.animate()
            .alpha(1f)
            .setDuration(400)
            .setStartDelay(200)
            .withEndAction {
                startDotsAnimation()
                // Переход на главный экран через 2 секунды
                loadingDots.postDelayed({
                    navigateToMain()
                }, 2000)
            }
            .start()
    }

    private fun startDotsAnimation() {
        val animator = ObjectAnimator.ofFloat(loadingDots, "alpha", 1f, 0.3f, 1f)
        animator.duration = 1000
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.start()
    }

    private fun navigateToMain() {
        // Анимация исчезновения
        val fadeOutDuration = 300L
        
        logoIcon.animate().alpha(0f).setDuration(fadeOutDuration).start()
        appName.animate().alpha(0f).setDuration(fadeOutDuration).start()
        appSlogan.animate().alpha(0f).setDuration(fadeOutDuration).start()
        loadingDots.animate()
            .alpha(0f)
            .setDuration(fadeOutDuration)
            .withEndAction {
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
                // Плавный переход между активностями
                overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
            }
            .start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Отключаем кнопку назад на splash screen
    }
}

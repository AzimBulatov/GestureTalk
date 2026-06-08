package com.example.gesturetalk

import androidx.appcompat.app.AppCompatActivity

/**
 * Базовая активность для отслеживания времени в приложении
 * Все активности должны наследоваться от этого класса
 */
open class BaseActivity : AppCompatActivity() {
    
    override fun onResume() {
        super.onResume()
        // Начинаем отслеживание времени когда активность становится видимой
        com.example.gesturetalk.utils.AchievementsManager.startSession(this)
    }
    
    override fun onPause() {
        super.onPause()
        // Останавливаем отслеживание времени когда активность уходит в фон
        com.example.gesturetalk.utils.AchievementsManager.endSession(this)
    }
}

package com.example.gesturetalk.utils

import android.app.Activity
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.example.gesturetalk.R
import com.example.gesturetalk.databinding.DialogAchievementUnlockedBinding
import com.example.gesturetalk.databinding.NotificationAchievementProgressBinding
import com.google.android.material.snackbar.Snackbar

/**
 * Класс для показа уведомлений о достижениях
 */
object AchievementNotifier {
    
    // Данные о достижениях
    data class Achievement(
        val id: Int,
        val icon: String,
        val name: String,
        val description: String,
        val target: Int = 0  // Целевое значение для прогресса
    )
    
    // Список всех достижений
    private val achievements = mapOf(
        1 to Achievement(1, "🔥", "Новичок", "Зайдите в приложение 3 дня подряд", 3),
        2 to Achievement(2, "🔥", "Постоянный пользователь", "Зайдите в приложение 7 дней подряд", 7),
        3 to Achievement(3, "🔥", "Мастер постоянства", "Зайдите в приложение 30 дней подряд", 30),
        4 to Achievement(4, "📖", "Исследователь", "Откройте справочник 5 раз", 5),
        5 to Achievement(5, "💬", "Болтун", "Отправьте 10 сообщений AI", 10),
        6 to Achievement(6, "👋", "Мастер жестов", "Распознайте 50 жестов", 50),
        7 to Achievement(7, "🎯", "Профессионал", "Распознайте 100 жестов", 100),
        8 to Achievement(8, "⚙️", "Настройщик", "Откройте настройки", 1),
        9 to Achievement(9, "📚", "Знаток справочника", "Откройте справочник 20 раз", 20),
        10 to Achievement(10, "🗣️", "Собеседник", "Отправьте 50 сообщений AI", 50),
        11 to Achievement(11, "👤", "Ученик", "Заполните профиль", 1),
        12 to Achievement(12, "🌙", "Ночной совёнок", "Используйте приложение после 22:00", 1),
        13 to Achievement(13, "☀️", "Ранняя пташка", "Используйте приложение до 8:00", 1),
        14 to Achievement(14, "📝", "Писатель", "Отправьте сообщение AI длиннее 100 символов", 1),
        15 to Achievement(15, "📜", "Летописец", "Создайте чат с 20+ сообщениями", 20),
        18 to Achievement(18, "✏️", "Персонализация", "Заполните все поля профиля", 1),
        19 to Achievement(19, "🎂", "День рождения", "Укажите дату рождения", 1),
        21 to Achievement(21, "🏃", "Марафон", "Проведите в приложении более 30 минут за сессию", 30)
    )
    
    /**
     * Показать диалог о получении достижения
     */
    fun showAchievementDialog(activity: Activity, achievementId: Int) {
        val achievement = achievements[achievementId] ?: return
        
        // Вибрация
        vibrateSuccess(activity)
        
        // Создаем диалог
        val binding = DialogAchievementUnlockedBinding.inflate(LayoutInflater.from(activity))
        
        binding.tvAchievementIcon.text = achievement.icon
        binding.tvAchievementName.text = achievement.name
        binding.tvAchievementDescription.text = achievement.description
        
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setCancelable(true)
            .create()
        
        // Прозрачный фон для скругленных углов
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        binding.btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Показать уведомление о прогрессе
     */
    fun showProgressNotification(activity: Activity, message: String) {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content)
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(activity.getColor(R.color.accent_primary))
            .setTextColor(activity.getColor(android.R.color.white))
            .show()
    }
    
    /**
     * Показать прогресс достижения в виде карточки сверху экрана
     */
    fun showAchievementProgress(activity: Activity, achievementId: Int, current: Int) {
        val achievement = achievements[achievementId] ?: return
        
        // Не показываем если уже достигнуто
        if (current >= achievement.target) return
        
        // Создаем уведомление
        val binding = NotificationAchievementProgressBinding.inflate(LayoutInflater.from(activity))
        
        binding.tvProgressIcon.text = achievement.icon
        binding.tvProgressName.text = achievement.name
        binding.tvProgressDescription.text = achievement.description
        binding.tvProgressCount.text = "$current/${achievement.target}"
        
        val progress = (current.toFloat() / achievement.target * 100).toInt()
        binding.progressBar.progress = progress
        
        // Создаем Snackbar с кастомным view
        val rootView = activity.findViewById<FrameLayout>(android.R.id.content)
        val snackbar = Snackbar.make(rootView, "", Snackbar.LENGTH_LONG)
        
        // Заменяем стандартный layout на наш
        val snackbarLayout = snackbar.view as Snackbar.SnackbarLayout
        snackbarLayout.setPadding(0, 0, 0, 0)
        snackbarLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        snackbarLayout.elevation = 0f
        
        // Удаляем стандартный TextView
        val textView = snackbarLayout.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)
        textView.visibility = android.view.View.INVISIBLE
        
        // Добавляем наш layout
        snackbarLayout.addView(binding.root, 0)
        
        // Позиционируем сверху
        val params = snackbarLayout.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.TOP
        params.topMargin = 16
        snackbarLayout.layoutParams = params
        
        snackbar.show()
    }
    
    /**
     * Вибрация при получении достижения
     */
    private fun vibrateSuccess(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Паттерн: короткая-пауза-длинная
                val pattern = longArrayOf(0, 100, 50, 200)
                val amplitudes = intArrayOf(0, 128, 0, 255)
                it.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(300)
            }
        }
    }
    
    /**
     * Проверить и показать уведомление о достижении
     */
    fun checkAndNotify(activity: Activity, achievementKey: String, achievementId: Int) {
        val prefs = activity.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        val notifiedKey = "notified_$achievementKey"
        
        // Проверяем достигнуто ли достижение
        val isAchieved = when {
            achievementKey.contains("days_streak") -> {
                val streak = prefs.getInt("days_streak", 0)
                val target = achievementKey.substringAfter("_").toIntOrNull() ?: 0
                streak >= target
            }
            achievementKey.contains("_count") -> {
                val count = prefs.getInt(achievementKey.substringBefore("_count"), 0)
                val target = achievementKey.substringAfterLast("_").toIntOrNull() ?: 0
                count >= target
            }
            else -> prefs.getBoolean(achievementKey, false)
        }
        
        // Если достижение получено и еще не показывали уведомление
        if (isAchieved && !prefs.getBoolean(notifiedKey, false)) {
            showAchievementDialog(activity, achievementId)
            prefs.edit().putBoolean(notifiedKey, true).apply()
        }
    }
}

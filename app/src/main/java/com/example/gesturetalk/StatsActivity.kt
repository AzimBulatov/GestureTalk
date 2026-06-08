package com.example.gesturetalk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.gesturetalk.databinding.ActivityStatsBinding
import com.example.gesturetalk.ui.NavTransitions.openBottomNavTab
import com.example.gesturetalk.ui.NavTransitions.setupGestureTalkBottomNav

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Обновляем серию дней при открытии любой активности
        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)
        
        setupBottomNav()
        loadAchievements()
    }

    private fun loadAchievements() {
        val prefs = getSharedPreferences("GestureTalkStats", MODE_PRIVATE)
        
        // Загружаем статистику
        val gesturesRecognized = prefs.getInt("gestures_recognized", 0)
        val guideOpens = prefs.getInt("guide_opens", 0)
        val aiMessages = prefs.getInt("ai_messages", 0)
        val daysStreak = prefs.getInt("days_streak", 0)
        val lettersViewed = prefs.getInt("letters_viewed", 0)
        val longMessageSent = prefs.getBoolean("long_message_sent", false)
        val chatWith20Messages = prefs.getBoolean("chat_with_20_messages", false)
        val usedAfter22 = prefs.getBoolean("used_after_22", false)
        val usedBefore8 = prefs.getBoolean("used_before_8", false)
        val profileComplete = prefs.getBoolean("profile_complete", false)
        val birthdaySet = prefs.getBoolean("birthday_set", false)
        val sprint5Gestures = prefs.getBoolean("sprint_5_gestures", false)
        val session30Min = prefs.getBoolean("session_30_min", false)
        
        // Достижение 1: Серия дней - 3 дня
        updateAchievement(binding.tvAchievement1Progress, binding.progressAchievement1, daysStreak, 3)
        
        // Достижение 2: Серия дней - 7 дней
        updateAchievement(binding.tvAchievement2Progress, binding.progressAchievement2, daysStreak, 7)
        
        // Достижение 3: Серия дней - 30 дней
        updateAchievement(binding.tvAchievement3Progress, binding.progressAchievement3, daysStreak, 30)
        
        // Достижение 4: Исследователь (5 открытий справочника)
        updateAchievement(binding.tvAchievement4Progress, binding.progressAchievement4, guideOpens, 5)
        
        // Достижение 5: Болтун (10 сообщений AI)
        updateAchievement(binding.tvAchievement5Progress, binding.progressAchievement5, aiMessages, 10)
        
        // Достижение 6: Мастер жестов (50 жестов)
        updateAchievement(binding.tvAchievement6Progress, binding.progressAchievement6, gesturesRecognized, 50)
        
        // Достижение 7: Профессионал (100 жестов)
        updateAchievement(binding.tvAchievement7Progress, binding.progressAchievement7, gesturesRecognized, 100)
        
        // Достижение 8: Настройщик (открыть настройки)
        val settingsOpened = prefs.getBoolean("settings_opened", false)
        updateAchievement(binding.tvAchievement8Progress, binding.progressAchievement8, if (settingsOpened) 1 else 0, 1)
        
        // Достижение 9: Знаток справочника (20 открытий)
        updateAchievement(binding.tvAchievement9Progress, binding.progressAchievement9, guideOpens, 20)
        
        // Достижение 10: Собеседник (50 сообщений AI)
        updateAchievement(binding.tvAchievement10Progress, binding.progressAchievement10, aiMessages, 50)
        
        // Достижение 11: Ученик (заполнить профиль)
        updateAchievement(binding.tvAchievement11Progress, binding.progressAchievement11, if (profileComplete) 1 else 0, 1)
        
        // Достижение 12: Ночной совёнок
        updateAchievement(binding.tvAchievement12Progress, binding.progressAchievement12, if (usedAfter22) 1 else 0, 1)
        
        // Достижение 13: Ранняя пташка
        updateAchievement(binding.tvAchievement13Progress, binding.progressAchievement13, if (usedBefore8) 1 else 0, 1)
        
        // Достижение 14: Писатель
        updateAchievement(binding.tvAchievement14Progress, binding.progressAchievement14, if (longMessageSent) 1 else 0, 1)
        
        // Достижение 15: Летописец
        updateAchievement(binding.tvAchievement15Progress, binding.progressAchievement15, if (chatWith20Messages) 1 else 0, 1)
        
        // Достижение 18: Персонализация
        updateAchievement(binding.tvAchievement18Progress, binding.progressAchievement18, if (profileComplete) 1 else 0, 1)
        
        // Достижение 19: День рождения
        updateAchievement(binding.tvAchievement19Progress, binding.progressAchievement19, if (birthdaySet) 1 else 0, 1)
        
        // Достижение 21: Марафон
        updateAchievement(binding.tvAchievement21Progress, binding.progressAchievement21, if (session30Min) 1 else 0, 1)
        
        // Обновляем статистику
        binding.tvGesturesRecognized.text = gesturesRecognized.toString()
        binding.tvGuideOpens.text = guideOpens.toString()
        binding.tvAiMessages.text = aiMessages.toString()
        binding.tvDaysStreak.text = daysStreak.toString()
        
        val chatsCreated = prefs.getInt("chats_created", 0)
        binding.tvChatsCreated.text = chatsCreated.toString()
        
        val timeSpent = prefs.getInt("time_spent_minutes", 0)
        binding.tvTimeSpent.text = "$timeSpent мин"
    }
    
    private fun updateAchievement(
        progressText: android.widget.TextView,
        progressBar: android.widget.ProgressBar,
        current: Int,
        target: Int
    ) {
        val clamped = current.coerceAtMost(target)
        progressText.text = "$clamped/$target"
        progressBar.progress = (clamped * 100) / target
    }

    private fun setupBottomNav() {
        setupGestureTalkBottomNav(binding.bottomNav, R.id.nav_stats) { nav ->
            nav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_guide_online -> {
                        openBottomNavTab(GuideOnlineActivity::class.java, R.id.nav_guide_online, R.id.nav_stats)
                        true
                    }
                    R.id.nav_ai -> {
                        openBottomNavTab(AskAiActivity::class.java, R.id.nav_ai, R.id.nav_stats)
                        true
                    }
                    R.id.nav_recognition -> {
                        openBottomNavTab(MainActivity::class.java, R.id.nav_recognition, R.id.nav_stats)
                        true
                    }
                    R.id.nav_stats -> true
                    R.id.nav_profile -> {
                        openBottomNavTab(ProfileActivity::class.java, R.id.nav_profile, R.id.nav_stats)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_stats
        loadAchievements() // Перезагружаем при возврате на экран
    }
}

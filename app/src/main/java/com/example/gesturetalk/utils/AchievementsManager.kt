package com.example.gesturetalk.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.example.gesturetalk.firebase.FirebaseStatsService
import java.util.Calendar

/**
 * Менеджер достижений - отслеживает прогресс пользователя
 */
object AchievementsManager {
    
    private const val PREFS_NAME = "GestureTalkStats"
    private val firebaseStats = FirebaseStatsService()
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Синхронизировать с Firebase после изменения
     */
    private fun syncToFirebase(context: Context) {
        try {
            firebaseStats.syncToFirebase(context)
        } catch (e: Exception) {
            android.util.Log.e("AchievementsManager", "Failed to sync to Firebase", e)
        }
    }
    
    /**
     * Проверить достижения и показать уведомления
     */
    private fun checkAchievements(context: Context) {
        if (context is Activity) {
            val prefs = getPrefs(context)
            
            // Проверяем достижения по серии дней
            val daysStreak = prefs.getInt("days_streak", 0)
            when {
                daysStreak >= 30 -> AchievementNotifier.checkAndNotify(context, "days_streak_30", 3)
                daysStreak >= 7 -> AchievementNotifier.checkAndNotify(context, "days_streak_7", 2)
                daysStreak >= 3 -> AchievementNotifier.checkAndNotify(context, "days_streak_3", 1)
            }
            
            // Проверяем достижения по жестам
            val gesturesRecognized = prefs.getInt("gestures_recognized", 0)
            when {
                gesturesRecognized >= 100 -> AchievementNotifier.checkAndNotify(context, "gestures_count_100", 7)
                gesturesRecognized >= 50 -> AchievementNotifier.checkAndNotify(context, "gestures_count_50", 6)
            }
            
            // Проверяем достижения по справочнику
            val guideOpens = prefs.getInt("guide_opens", 0)
            when {
                guideOpens >= 20 -> AchievementNotifier.checkAndNotify(context, "guide_opens_20", 9)
                guideOpens >= 5 -> AchievementNotifier.checkAndNotify(context, "guide_opens_5", 4)
            }
            
            // Проверяем достижения по AI
            val aiMessages = prefs.getInt("ai_messages", 0)
            when {
                aiMessages >= 50 -> AchievementNotifier.checkAndNotify(context, "ai_messages_50", 10)
                aiMessages >= 10 -> AchievementNotifier.checkAndNotify(context, "ai_messages_10", 5)
            }
            
            // Проверяем булевы достижения
            if (prefs.getBoolean("settings_opened", false)) {
                AchievementNotifier.checkAndNotify(context, "settings_opened", 8)
            }
            if (prefs.getBoolean("profile_complete", false)) {
                AchievementNotifier.checkAndNotify(context, "profile_complete", 11)
                AchievementNotifier.checkAndNotify(context, "profile_complete", 18)
            }
            if (prefs.getBoolean("birthday_set", false)) {
                AchievementNotifier.checkAndNotify(context, "birthday_set", 19)
            }
            if (prefs.getBoolean("used_after_22", false)) {
                AchievementNotifier.checkAndNotify(context, "used_after_22", 12)
            }
            if (prefs.getBoolean("used_before_8", false)) {
                AchievementNotifier.checkAndNotify(context, "used_before_8", 13)
            }
            if (prefs.getBoolean("long_message_sent", false)) {
                AchievementNotifier.checkAndNotify(context, "long_message_sent", 14)
            }
            if (prefs.getBoolean("chat_with_20_messages", false)) {
                AchievementNotifier.checkAndNotify(context, "chat_with_20_messages", 15)
            }
            if (prefs.getBoolean("session_30_min", false)) {
                AchievementNotifier.checkAndNotify(context, "session_30_min", 21)
            }
        }
    }
    
    /**
     * Показать прогресс для достижений с прогресс-баром
     */
    private fun showProgressForAchievement(context: Context, achievementId: Int, current: Int) {
        if (context is Activity) {
            AchievementNotifier.showAchievementProgress(context, achievementId, current)
        }
    }
    
    /**
     * Увеличить счетчик распознанных жестов
     */
    fun incrementGesturesRecognized(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt("gestures_recognized", 0)
        val newValue = current + 1
        prefs.edit().putInt("gestures_recognized", newValue).apply()
        
        // Показываем прогресс для ближайшего достижения
        when {
            newValue < 50 -> showProgressForAchievement(context, 6, newValue)  // Мастер жестов
            newValue < 100 -> showProgressForAchievement(context, 7, newValue)  // Профессионал
        }
        
        syncToFirebase(context)
        checkAchievements(context)
        
        // Проверяем достижение "Спринтер" (5 жестов за минуту)
        checkSprintAchievement(context)
    }
    
    /**
     * Увеличить счетчик открытий справочника
     */
    fun incrementGuideOpens(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt("guide_opens", 0)
        val newValue = current + 1
        prefs.edit().putInt("guide_opens", newValue).apply()
        
        // Показываем прогресс для ближайшего достижения
        when {
            newValue < 5 -> showProgressForAchievement(context, 4, newValue)  // Исследователь
            newValue < 20 -> showProgressForAchievement(context, 9, newValue)  // Знаток справочника
        }
        
        syncToFirebase(context)
        checkAchievements(context)
    }
    
    /**
     * Увеличить счетчик сообщений AI
     */
    fun incrementAiMessages(context: Context, messageLength: Int) {
        val prefs = getPrefs(context)
        val current = prefs.getInt("ai_messages", 0)
        val newValue = current + 1
        prefs.edit().putInt("ai_messages", newValue).apply()
        
        // Показываем прогресс для ближайшего достижения
        when {
            newValue < 10 -> showProgressForAchievement(context, 5, newValue)  // Болтун
            newValue < 50 -> showProgressForAchievement(context, 10, newValue)  // Собеседник
        }
        
        // Проверяем достижение "Писатель" (сообщение > 100 символов)
        if (messageLength > 100) {
            prefs.edit().putBoolean("long_message_sent", true).apply()
        }
        syncToFirebase(context)
        checkAchievements(context)
    }
    
    /**
     * Добавить просмотренную букву
     */
    fun addLetterViewed(context: Context, letter: String) {
        val prefs = getPrefs(context)
        val viewedLetters = prefs.getStringSet("viewed_letters", mutableSetOf()) ?: mutableSetOf()
        val newSet = viewedLetters.toMutableSet()
        newSet.add(letter)
        prefs.edit().putStringSet("viewed_letters", newSet).apply()
        prefs.edit().putInt("letters_viewed", newSet.size).apply()
    }
    
    /**
     * Обновить серию дней
     */
    fun updateDaysStreak(context: Context) {
        val prefs = getPrefs(context)
        val lastVisit = prefs.getLong("last_visit_date", 0)
        val today = getTodayTimestamp()
        
        if (lastVisit == 0L) {
            // Первый визит
            prefs.edit()
                .putLong("last_visit_date", today)
                .putInt("days_streak", 1)
                .apply()
            showProgressForAchievement(context, 1, 1)  // Новичок
        } else {
            val daysDiff = ((today - lastVisit) / (24 * 60 * 60 * 1000)).toInt()
            
            when {
                daysDiff == 0 -> {
                    // Тот же день - ничего не делаем
                }
                daysDiff == 1 -> {
                    // Следующий день - увеличиваем серию
                    val currentStreak = prefs.getInt("days_streak", 0)
                    val newStreak = currentStreak + 1
                    prefs.edit()
                        .putLong("last_visit_date", today)
                        .putInt("days_streak", newStreak)
                        .apply()
                    
                    // Показываем прогресс для ближайшего достижения
                    when {
                        newStreak < 3 -> showProgressForAchievement(context, 1, newStreak)  // Новичок
                        newStreak < 7 -> showProgressForAchievement(context, 2, newStreak)  // Постоянный пользователь
                        newStreak < 30 -> showProgressForAchievement(context, 3, newStreak)  // Мастер постоянства
                    }
                }
                else -> {
                    // Пропущены дни - сбрасываем серию
                    prefs.edit()
                        .putLong("last_visit_date", today)
                        .putInt("days_streak", 1)
                        .apply()
                    showProgressForAchievement(context, 1, 1)  // Новичок
                }
            }
        }
        syncToFirebase(context)
        checkAchievements(context)
    }
    
    /**
     * Проверить время использования для достижений "Ночной совёнок" и "Ранняя пташка"
     */
    fun checkTimeBasedAchievements(context: Context) {
        val prefs = getPrefs(context)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Ночной совёнок (после 22:00)
        if (hour >= 22 || hour < 6) {
            prefs.edit().putBoolean("used_after_22", true).apply()
        }
        
        // Ранняя пташка (до 8:00)
        if (hour < 8) {
            prefs.edit().putBoolean("used_before_8", true).apply()
        }
        
        checkAchievements(context)
    }
    
    /**
     * Отметить что настройки были открыты
     */
    fun markSettingsOpened(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean("settings_opened", true).apply()
        syncToFirebase(context)
        checkAchievements(context)
    }
    
    /**
     * Проверить заполненность профиля
     */
    fun checkProfileComplete(context: Context, name: String?, email: String?, birthdate: String?) {
        val prefs = getPrefs(context)
        val isComplete = !name.isNullOrBlank() && !email.isNullOrBlank() && !birthdate.isNullOrBlank()
        prefs.edit().putBoolean("profile_complete", isComplete).apply()
        
        if (!birthdate.isNullOrBlank()) {
            prefs.edit().putBoolean("birthday_set", true).apply()
        }
        syncToFirebase(context)
        checkAchievements(context)
    }
    
    /**
     * Увеличить счетчик созданных чатов
     */
    fun incrementChatsCreated(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt("chats_created", 0)
        prefs.edit().putInt("chats_created", current + 1).apply()
        syncToFirebase(context)
    }
    
    /**
     * Проверить достижение "Летописец" (чат с 20+ сообщениями)
     */
    fun checkChatMessagesAchievement(context: Context, messageCount: Int) {
        if (messageCount >= 20) {
            val prefs = getPrefs(context)
            prefs.edit().putBoolean("chat_with_20_messages", true).apply()
            syncToFirebase(context)
            checkAchievements(context)
        }
    }
    
    /**
     * Обновить среднюю уверенность модели
     */
    fun updateAverageConfidence(context: Context, confidence: Float) {
        val prefs = getPrefs(context)
        val totalRecognitions = prefs.getInt("gestures_recognized", 0)
        val currentAvg = prefs.getFloat("avg_confidence", 0f)
        
        if (totalRecognitions > 0) {
            val newAvg = ((currentAvg * (totalRecognitions - 1)) + confidence) / totalRecognitions
            prefs.edit().putFloat("avg_confidence", newAvg).apply()
        } else {
            prefs.edit().putFloat("avg_confidence", confidence).apply()
        }
    }
    
    /**
     * Начать отслеживание сессии
     */
    fun startSession(context: Context) {
        val prefs = getPrefs(context)
        val currentTime = System.currentTimeMillis()
        
        // Сохраняем время старта только если его еще нет
        if (!prefs.contains("session_start_time")) {
            prefs.edit().putLong("session_start_time", currentTime).apply()
        }
    }
    
    /**
     * Сохранить текущее время сессии без завершения
     */
    fun saveCurrentSessionTime(context: Context) {
        val prefs = getPrefs(context)
        val startTime = prefs.getLong("session_start_time", 0)
        
        if (startTime > 0) {
            val currentTime = System.currentTimeMillis()
            val duration = (currentTime - startTime) / 1000 / 60 // в минутах
            
            if (duration > 0) {
                // Обновляем общее время
                val totalTime = prefs.getInt("time_spent_minutes", 0)
                prefs.edit()
                    .putInt("time_spent_minutes", totalTime + duration.toInt())
                    .putLong("session_start_time", currentTime) // Обновляем старт для следующего интервала
                    .apply()
                
                // Проверяем достижение "Марафон" (30+ минут за сессию)
                val sessionTotalTime = prefs.getInt("current_session_time", 0) + duration.toInt()
                prefs.edit().putInt("current_session_time", sessionTotalTime).apply()
                
                if (sessionTotalTime >= 30) {
                    prefs.edit().putBoolean("session_30_min", true).apply()
                }
                
                syncToFirebase(context)
            }
        }
    }
    
    /**
     * Завершить сессию и проверить достижение "Марафон"
     */
    fun endSession(context: Context) {
        val prefs = getPrefs(context)
        val startTime = prefs.getLong("session_start_time", 0)
        
        if (startTime > 0) {
            val currentTime = System.currentTimeMillis()
            val duration = (currentTime - startTime) / 1000 / 60 // в минутах
            
            if (duration > 0) {
                // Обновляем общее время
                val totalTime = prefs.getInt("time_spent_minutes", 0)
                prefs.edit().putInt("time_spent_minutes", totalTime + duration.toInt()).apply()
                
                // Проверяем достижение "Марафон" (30+ минут за сессию)
                val sessionTotalTime = prefs.getInt("current_session_time", 0) + duration.toInt()
                if (sessionTotalTime >= 30) {
                    prefs.edit().putBoolean("session_30_min", true).apply()
                }
            }
            
            // Очищаем данные сессии
            prefs.edit()
                .remove("session_start_time")
                .remove("current_session_time")
                .apply()
            
            syncToFirebase(context)
            checkAchievements(context)
        }
    }
    
    /**
     * Проверить достижение "Спринтер" (5 жестов за минуту)
     */
    private fun checkSprintAchievement(context: Context) {
        val prefs = getPrefs(context)
        val sprintGestures = prefs.getStringSet("sprint_gestures", mutableSetOf()) ?: mutableSetOf()
        val currentTime = System.currentTimeMillis()
        
        // Добавляем текущее время
        val newSet = sprintGestures.toMutableSet()
        newSet.add(currentTime.toString())
        
        // Удаляем записи старше 1 минуты
        val oneMinuteAgo = currentTime - 60000
        val filtered = newSet.filter { it.toLongOrNull()?.let { time -> time > oneMinuteAgo } ?: false }.toMutableSet()
        
        prefs.edit().putStringSet("sprint_gestures", filtered).apply()
        
        // Проверяем достижение
        if (filtered.size >= 5) {
            prefs.edit().putBoolean("sprint_5_gestures", true).apply()
        }
    }
    
    /**
     * Получить timestamp начала текущего дня
     */
    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

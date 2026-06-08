package com.example.gesturetalk.utils

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Вспомогательный класс для отладки достижений
 */
object AchievementsDebugHelper {
    
    private const val TAG = "AchievementsDebug"
    
    /**
     * Вывести текущее состояние достижений в лог
     */
    fun logCurrentState(context: Context) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        
        val lastVisit = prefs.getLong("last_visit_date", 0)
        val daysStreak = prefs.getInt("days_streak", 0)
        val guideOpens = prefs.getInt("guide_opens", 0)
        val aiMessages = prefs.getInt("ai_messages", 0)
        val chatsCreated = prefs.getInt("chats_created", 0)
        val longMessageSent = prefs.getBoolean("long_message_sent", false)
        val chatWith20Messages = prefs.getBoolean("chat_with_20_messages", false)
        val uniqueGesturesViewed = prefs.getInt("unique_gestures_viewed", 0)
        val usedAfter22 = prefs.getBoolean("used_after_22", false)
        val usedBefore8 = prefs.getBoolean("used_before_8", false)
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val lastVisitStr = if (lastVisit > 0) dateFormat.format(Date(lastVisit)) else "никогда"
        
        Log.d(TAG, "=== СОСТОЯНИЕ ДОСТИЖЕНИЙ ===")
        Log.d(TAG, "Последний визит: $lastVisitStr")
        Log.d(TAG, "Серия дней: $daysStreak")
        Log.d(TAG, "Открытий справочника: $guideOpens")
        Log.d(TAG, "Сообщений AI: $aiMessages")
        Log.d(TAG, "Создано чатов: $chatsCreated")
        Log.d(TAG, "Длинное сообщение: ${if (longMessageSent) "да" else "нет"}")
        Log.d(TAG, "Чат с 20+ сообщениями: ${if (chatWith20Messages) "да" else "нет"}")
        Log.d(TAG, "Уникальных жестов: $uniqueGesturesViewed")
        Log.d(TAG, "Ночной совёнок: ${if (usedAfter22) "да" else "нет"}")
        Log.d(TAG, "Ранняя пташка: ${if (usedBefore8) "да" else "нет"}")
        Log.d(TAG, "Сегодня: ${dateFormat.format(Date())}")
        Log.d(TAG, "===========================")
    }
    
    /**
     * Сбросить серию дней (для тестирования)
     */
    fun resetDaysStreak(context: Context) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("last_visit_date")
            .remove("days_streak")
            .apply()
        Log.d(TAG, "Серия дней сброшена")
    }
    
    /**
     * Установить серию дней вручную (для тестирования)
     */
    fun setDaysStreak(context: Context, days: Int) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        val today = getTodayTimestamp()
        prefs.edit()
            .putLong("last_visit_date", today)
            .putInt("days_streak", days)
            .apply()
        Log.d(TAG, "Серия дней установлена: $days")
    }
    
    /**
     * Установить счетчик открытий справочника (для тестирования)
     */
    fun setGuideOpens(context: Context, count: Int) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        prefs.edit().putInt("guide_opens", count).apply()
        Log.d(TAG, "Открытий справочника установлено: $count")
    }
    
    /**
     * Сбросить счетчик открытий справочника (для тестирования)
     */
    fun resetGuideOpens(context: Context) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        prefs.edit().remove("guide_opens").apply()
        Log.d(TAG, "Счетчик открытий справочника сброшен")
    }
    
    /**
     * Установить счетчик сообщений AI (для тестирования)
     */
    fun setAiMessages(context: Context, count: Int) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        prefs.edit().putInt("ai_messages", count).apply()
        Log.d(TAG, "Сообщений AI установлено: $count")
    }
    
    /**
     * Сбросить все AI достижения (для тестирования)
     */
    fun resetAiAchievements(context: Context) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("ai_messages")
            .remove("chats_created")
            .remove("long_message_sent")
            .remove("chat_with_20_messages")
            .apply()
        Log.d(TAG, "AI достижения сброшены")
    }
    
    /**
     * Установить счетчик уникальных жестов (для тестирования)
     */
    fun setUniqueGesturesViewed(context: Context, count: Int) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        prefs.edit().putInt("unique_gestures_viewed", count).apply()
        Log.d(TAG, "Уникальных жестов установлено: $count")
    }
    
    /**
     * Сбросить счетчик уникальных жестов (для тестирования)
     */
    fun resetUniqueGestures(context: Context) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("viewed_gestures")
            .remove("unique_gestures_viewed")
            .apply()
        Log.d(TAG, "Счетчик уникальных жестов сброшен")
    }
    
    /**
     * Симулировать визит N дней назад (для тестирования)
     */
    fun simulateVisitDaysAgo(context: Context, daysAgo: Int) {
        val prefs = context.getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        prefs.edit()
            .putLong("last_visit_date", calendar.timeInMillis)
            .putInt("days_streak", 1)
            .apply()
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        Log.d(TAG, "Симулирован визит $daysAgo дней назад: ${dateFormat.format(calendar.time)}")
    }
    
    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

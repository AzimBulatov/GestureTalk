package com.example.gesturetalk.firebase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Сервис для синхронизации статистики и достижений с Firebase Realtime Database
 */
class FirebaseStatsService {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    
    companion object {
        private const val TAG = "FirebaseStatsService"
        private const val STATS_PATH = "user_stats"
        private const val PREFS_NAME = "GestureTalkStats"
    }
    
    /**
     * Синхронизировать локальные данные с Firebase
     */
    fun syncToFirebase(context: Context) {
        val userId = auth.currentUser?.uid ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val statsData = hashMapOf<String, Any>(
            // Счетчики
            "gestures_recognized" to prefs.getInt("gestures_recognized", 0),
            "guide_opens" to prefs.getInt("guide_opens", 0),
            "ai_messages" to prefs.getInt("ai_messages", 0),
            "days_streak" to prefs.getInt("days_streak", 0),
            "chats_created" to prefs.getInt("chats_created", 0),
            "time_spent_minutes" to prefs.getInt("time_spent_minutes", 0),
            "letters_viewed" to prefs.getInt("letters_viewed", 0),
            
            // Булевы достижения
            "settings_opened" to prefs.getBoolean("settings_opened", false),
            "profile_complete" to prefs.getBoolean("profile_complete", false),
            "birthday_set" to prefs.getBoolean("birthday_set", false),
            "long_message_sent" to prefs.getBoolean("long_message_sent", false),
            "chat_with_20_messages" to prefs.getBoolean("chat_with_20_messages", false),
            "used_after_22" to prefs.getBoolean("used_after_22", false),
            "used_before_8" to prefs.getBoolean("used_before_8", false),
            "session_30_min" to prefs.getBoolean("session_30_min", false),
            
            // Временные метки
            "last_visit_date" to prefs.getLong("last_visit_date", 0),
            "last_sync" to System.currentTimeMillis()
        )
        
        database.reference
            .child(STATS_PATH)
            .child(userId)
            .setValue(statsData)
            .addOnSuccessListener {
                Log.d(TAG, "Stats synced to Firebase successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync stats to Firebase", e)
            }
    }
    
    /**
     * Загрузить данные из Firebase в локальное хранилище
     */
    fun loadFromFirebase(context: Context, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onComplete(false)
            return
        }
        
        database.reference
            .child(STATS_PATH)
            .child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.d(TAG, "No stats found in Firebase, using local data")
                        onComplete(false)
                        return
                    }
                    
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    
                    // Загружаем счетчики
                    editor.putInt("gestures_recognized", snapshot.child("gestures_recognized").getValue(Int::class.java) ?: 0)
                    editor.putInt("guide_opens", snapshot.child("guide_opens").getValue(Int::class.java) ?: 0)
                    editor.putInt("ai_messages", snapshot.child("ai_messages").getValue(Int::class.java) ?: 0)
                    editor.putInt("days_streak", snapshot.child("days_streak").getValue(Int::class.java) ?: 0)
                    editor.putInt("chats_created", snapshot.child("chats_created").getValue(Int::class.java) ?: 0)
                    editor.putInt("time_spent_minutes", snapshot.child("time_spent_minutes").getValue(Int::class.java) ?: 0)
                    editor.putInt("letters_viewed", snapshot.child("letters_viewed").getValue(Int::class.java) ?: 0)
                    
                    // Загружаем булевы достижения
                    editor.putBoolean("settings_opened", snapshot.child("settings_opened").getValue(Boolean::class.java) ?: false)
                    editor.putBoolean("profile_complete", snapshot.child("profile_complete").getValue(Boolean::class.java) ?: false)
                    editor.putBoolean("birthday_set", snapshot.child("birthday_set").getValue(Boolean::class.java) ?: false)
                    editor.putBoolean("long_message_sent", snapshot.child("long_message_sent").getValue(Boolean::class.java) ?: false)
                    editor.putBoolean("chat_with_20_messages", snapshot.child("chat_with_20_messages").getValue(Boolean::class.java) ?: false)
                    editor.putBoolean("used_after_22", snapshot.child("used_after_22").getValue(Boolean::class.java) ?: false)
                    editor.putBoolean("used_before_8", snapshot.child("used_before_8").getValue(Boolean::class.java) ?: false)
                    editor.putBoolean("session_30_min", snapshot.child("session_30_min").getValue(Boolean::class.java) ?: false)
                    
                    // Загружаем временные метки
                    editor.putLong("last_visit_date", snapshot.child("last_visit_date").getValue(Long::class.java) ?: 0)
                    
                    editor.apply()
                    
                    Log.d(TAG, "Stats loaded from Firebase successfully")
                    onComplete(true)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to load stats from Firebase", error.toException())
                    onComplete(false)
                }
            })
    }
    
    /**
     * Объединить локальные и облачные данные (выбрать максимальные значения)
     */
    fun mergeStats(context: Context, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onComplete(false)
            return
        }
        
        database.reference
            .child(STATS_PATH)
            .child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    
                    if (!snapshot.exists()) {
                        // Нет данных в облаке - загружаем локальные
                        syncToFirebase(context)
                        onComplete(true)
                        return
                    }
                    
                    // Объединяем данные (берем максимальные значения для счетчиков)
                    val localGestures = prefs.getInt("gestures_recognized", 0)
                    val cloudGestures = snapshot.child("gestures_recognized").getValue(Int::class.java) ?: 0
                    editor.putInt("gestures_recognized", maxOf(localGestures, cloudGestures))
                    
                    val localGuideOpens = prefs.getInt("guide_opens", 0)
                    val cloudGuideOpens = snapshot.child("guide_opens").getValue(Int::class.java) ?: 0
                    editor.putInt("guide_opens", maxOf(localGuideOpens, cloudGuideOpens))
                    
                    val localAiMessages = prefs.getInt("ai_messages", 0)
                    val cloudAiMessages = snapshot.child("ai_messages").getValue(Int::class.java) ?: 0
                    editor.putInt("ai_messages", maxOf(localAiMessages, cloudAiMessages))
                    
                    val localDaysStreak = prefs.getInt("days_streak", 0)
                    val cloudDaysStreak = snapshot.child("days_streak").getValue(Int::class.java) ?: 0
                    editor.putInt("days_streak", maxOf(localDaysStreak, cloudDaysStreak))
                    
                    val localChats = prefs.getInt("chats_created", 0)
                    val cloudChats = snapshot.child("chats_created").getValue(Int::class.java) ?: 0
                    editor.putInt("chats_created", maxOf(localChats, cloudChats))
                    
                    val localTime = prefs.getInt("time_spent_minutes", 0)
                    val cloudTime = snapshot.child("time_spent_minutes").getValue(Int::class.java) ?: 0
                    editor.putInt("time_spent_minutes", maxOf(localTime, cloudTime)) // Берем максимум, а не сумму!
                    
                    // Для булевых достижений - если хоть где-то true, то true
                    editor.putBoolean("settings_opened", 
                        prefs.getBoolean("settings_opened", false) || 
                        (snapshot.child("settings_opened").getValue(Boolean::class.java) ?: false))
                    
                    editor.putBoolean("profile_complete",
                        prefs.getBoolean("profile_complete", false) ||
                        (snapshot.child("profile_complete").getValue(Boolean::class.java) ?: false))
                    
                    editor.putBoolean("birthday_set",
                        prefs.getBoolean("birthday_set", false) ||
                        (snapshot.child("birthday_set").getValue(Boolean::class.java) ?: false))
                    
                    editor.putBoolean("long_message_sent",
                        prefs.getBoolean("long_message_sent", false) ||
                        (snapshot.child("long_message_sent").getValue(Boolean::class.java) ?: false))
                    
                    editor.putBoolean("chat_with_20_messages",
                        prefs.getBoolean("chat_with_20_messages", false) ||
                        (snapshot.child("chat_with_20_messages").getValue(Boolean::class.java) ?: false))
                    
                    editor.putBoolean("used_after_22",
                        prefs.getBoolean("used_after_22", false) ||
                        (snapshot.child("used_after_22").getValue(Boolean::class.java) ?: false))
                    
                    editor.putBoolean("used_before_8",
                        prefs.getBoolean("used_before_8", false) ||
                        (snapshot.child("used_before_8").getValue(Boolean::class.java) ?: false))
                    
                    editor.putBoolean("session_30_min",
                        prefs.getBoolean("session_30_min", false) ||
                        (snapshot.child("session_30_min").getValue(Boolean::class.java) ?: false))
                    
                    editor.apply()
                    
                    // Сохраняем объединенные данные обратно в Firebase
                    syncToFirebase(context)
                    
                    Log.d(TAG, "Stats merged successfully")
                    onComplete(true)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to merge stats", error.toException())
                    onComplete(false)
                }
            })
    }
}

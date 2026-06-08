package com.example.gesturetalk.utils

import android.content.Context
import com.example.gesturetalk.firebase.FirebaseAuthService
import com.google.firebase.auth.FirebaseUser

/**
 * Локальный кэш профиля пользователя (SharedPreferences).
 * Загрузка из Firebase — один раз на пользователя; дальше чтение только отсюда.
 */
object UserProfileCache {

    private const val PREFS_NAME = "user_profile_cache"

    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_BIRTH_DATE = "birth_date"
    private const val KEY_LEARNING_STATUS = "learning_status"
    private const val KEY_SYNCED = "synced_from_remote"

    data class Profile(
        val fullName: String = "",
        val birthDate: String = "",
        val learningStatus: String = "Начинающий",
        val email: String = ""
    )

    fun hasCache(context: Context, userId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SYNCED, false) && prefs.getString(KEY_USER_ID, null) == userId
    }

    fun get(context: Context, userId: String): Profile? {
        if (!hasCache(context, userId)) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Profile(
            fullName = prefs.getString(KEY_FULL_NAME, "") ?: "",
            birthDate = prefs.getString(KEY_BIRTH_DATE, "") ?: "",
            learningStatus = prefs.getString(KEY_LEARNING_STATUS, "Начинающий") ?: "Начинающий",
            email = prefs.getString(KEY_EMAIL, "") ?: ""
        )
    }

    fun save(context: Context, userId: String, profile: Profile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, profile.email)
            .putString(KEY_FULL_NAME, profile.fullName)
            .putString(KEY_BIRTH_DATE, profile.birthDate)
            .putString(KEY_LEARNING_STATUS, profile.learningStatus)
            .putBoolean(KEY_SYNCED, true)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun fromRemoteMap(email: String, map: Map<String, Any>): Profile {
        return Profile(
            fullName = map["fullName"]?.toString() ?: "",
            birthDate = map["birthDate"]?.toString() ?: "",
            learningStatus = map["learningStatus"]?.toString()?.takeIf { it.isNotEmpty() } ?: "Начинающий",
            email = email
        )
    }

    /**
     * Однократная загрузка из Realtime Database и сохранение в локальный кэш.
     */
    suspend fun syncFromRemote(
        context: Context,
        firebaseService: FirebaseAuthService,
        user: FirebaseUser
    ): Result<Profile> {
        return firebaseService.getUserProfile().map { map ->
            val profile = fromRemoteMap(user.email ?: "", map)
            save(context, user.uid, profile)
            profile
        }
    }
}

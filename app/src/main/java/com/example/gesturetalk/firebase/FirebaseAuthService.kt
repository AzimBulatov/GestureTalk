package com.example.gesturetalk.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class FirebaseAuthService {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    
    /**
     * Получить текущего пользователя
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    /**
     * Регистрация нового пользователя
     */
    suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                // Создаем профиль пользователя в Realtime Database
                createUserProfile(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Не удалось создать пользователя"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Вход в аккаунт
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Не удалось войти в аккаунт"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Выход из аккаунта
     */
    fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Создание профиля пользователя в Realtime Database
     */
    private suspend fun createUserProfile(user: FirebaseUser) {
        try {
            val userProfile = mapOf(
                "email" to (user.email ?: ""),
                "fullName" to "",
                "birthDate" to "",
                "learningStatus" to "Начинающий",
                "createdAt" to System.currentTimeMillis()
            )
            
            database.child("users")
                .child(user.uid)
                .setValue(userProfile)
                .await()
                
            android.util.Log.d("FirebaseAuth", "✓ Профиль создан в Realtime Database")
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuth", "Ошибка создания профиля: ${e.message}", e)
        }
    }
    
    /**
     * Получение профиля пользователя из Realtime Database
     */
    suspend fun getUserProfile(): Result<Map<String, Any>> {
        return try {
            val user = getCurrentUser()
            if (user != null) {
                try {
                    val snapshot = database.child("users")
                        .child(user.uid)
                        .get()
                        .await()
                    
                    if (snapshot.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val data = snapshot.value as? Map<String, Any> ?: emptyMap()
                        Result.success(data)
                    } else {
                        // Создаем профиль если его нет
                        createUserProfile(user)
                        Result.success(mapOf(
                            "email" to (user.email ?: ""),
                            "fullName" to "",
                            "birthDate" to "",
                            "learningStatus" to "Начинающий"
                        ))
                    }
                } catch (databaseError: Exception) {
                    android.util.Log.w("FirebaseAuth", "Realtime Database недоступен: ${databaseError.message}")
                    // Если база недоступна, возвращаем базовые данные из Firebase Auth
                    Result.success(mapOf(
                        "email" to (user.email ?: ""),
                        "fullName" to "",
                        "birthDate" to "",
                        "learningStatus" to "Начинающий"
                    ))
                }
            } else {
                Result.failure(Exception("Пользователь не авторизован"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Обновление профиля пользователя в Realtime Database
     */
    suspend fun updateUserProfile(updates: Map<String, Any>): Result<Unit> {
        return try {
            val user = getCurrentUser()
            if (user != null) {
                database.child("users")
                    .child(user.uid)
                    .updateChildren(updates)
                    .await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Пользователь не авторизован"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
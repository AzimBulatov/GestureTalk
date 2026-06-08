package com.example.gesturetalk

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gesturetalk.databinding.ActivityAuthBinding
import com.example.gesturetalk.firebase.FirebaseAuthService
import com.example.gesturetalk.ui.NavTransitions.navigateWithFade
import com.example.gesturetalk.utils.UserProfileCache
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAuthBinding
    private val firebaseService = FirebaseAuthService()
    private var isLoginMode = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupListeners()
    }
    
    private fun setupUI() {
        updateUI()
    }
    
    private fun setupListeners() {
        binding.btnSubmit.setOnClickListener {
            if (isLoginMode) {
                login()
            } else {
                register()
            }
        }
        
        binding.btnToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }
    }
    
    private fun updateUI() {
        if (isLoginMode) {
            binding.tvTitle.text = "Вход в аккаунт"
            binding.btnSubmit.text = "Войти"
            binding.btnToggleMode.text = "Нет аккаунта? Зарегистрироваться"
        } else {
            binding.tvTitle.text = "Регистрация"
            binding.btnSubmit.text = "Зарегистрироваться"
            binding.btnToggleMode.text = "Есть аккаунт? Войти"
        }
    }
    
    private fun register() {
        val loginInput = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        if (loginInput.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (password.length < 6) {
            Toast.makeText(this, "Пароль должен быть не менее 6 символов", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Конвертируем любой ввод в email формат для Firebase
        val email = convertToEmailFormat(loginInput)
        
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Регистрация..."
        
        lifecycleScope.launch {
            firebaseService.signUp(email, password)
                .onSuccess { user ->
                    UserProfileCache.syncFromRemote(this@AuthActivity, firebaseService, user)
                    Toast.makeText(this@AuthActivity, "Регистрация успешна! Добро пожаловать!", Toast.LENGTH_SHORT).show()
                    navigateWithFade(Intent(this@AuthActivity, MainActivity::class.java), finishCurrent = true)
                }
                .onFailure { error ->
                    Toast.makeText(this@AuthActivity, "Ошибка регистрации: ${error.message}", Toast.LENGTH_LONG).show()
                    binding.btnSubmit.isEnabled = true
                    updateUI()
                }
        }
    }
    
    private fun login() {
        val loginInput = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        if (loginInput.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Конвертируем любой ввод в email формат для Firebase
        val email = convertToEmailFormat(loginInput)
        
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Вход..."
        
        lifecycleScope.launch {
            firebaseService.signIn(email, password)
                .onSuccess { user ->
                    UserProfileCache.syncFromRemote(this@AuthActivity, firebaseService, user)
                    Toast.makeText(this@AuthActivity, "Добро пожаловать!", Toast.LENGTH_SHORT).show()
                    navigateWithFade(Intent(this@AuthActivity, MainActivity::class.java), finishCurrent = true)
                }
                .onFailure { error ->
                    Toast.makeText(this@AuthActivity, "Ошибка входа: ${error.message}", Toast.LENGTH_LONG).show()
                    binding.btnSubmit.isEnabled = true
                    updateUI()
                }
        }
    }
    
    /**
     * Конвертирует любой ввод (телефон, логин) в валидный email формат для Firebase
     */
    private fun convertToEmailFormat(input: String): String {
        // Если уже email - оставляем как есть
        if (input.contains("@") && input.contains(".")) {
            return input
        }
        
        // Иначе делаем из этого email для Firebase
        val cleanInput = input.replace(Regex("[^a-zA-Z0-9]"), "") // убираем спецсимволы
        return "${cleanInput}@gesturetalk.app"
    }
}
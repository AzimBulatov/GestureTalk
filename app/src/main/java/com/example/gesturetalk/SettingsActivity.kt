package com.example.gesturetalk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.gesturetalk.databinding.ActivitySettingsBinding
import com.example.gesturetalk.firebase.FirebaseAuthService
import com.example.gesturetalk.ui.NavTransitions.finishWithFade
import com.example.gesturetalk.ui.NavTransitions.openBottomNavTab
import com.example.gesturetalk.ui.NavTransitions.setupGestureTalkBottomNav
import com.example.gesturetalk.utils.UserProfileCache

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private val firebaseService = FirebaseAuthService()

    companion object {
        private const val PREFS_NAME = "GestureTalkPrefs"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_USE_FRONT_CAMERA = "use_front_camera"
        private const val KEY_SHOW_LANDMARKS = "show_landmarks"
        private const val KEY_CAMERA_RESOLUTION = "camera_resolution"
        private const val KEY_CAMERA_FPS = "camera_fps"
        private const val KEY_ENABLE_AUTOFOCUS = "enable_autofocus"
        private const val KEY_ENABLE_STABILIZATION = "enable_stabilization"
        private const val KEY_LIGHTING_METHOD = "lighting_method"
        private const val KEY_GAMMA_VALUE = "gamma_value"
        private const val KEY_CLAHE_CLIP_LIMIT = "clahe_clip_limit"
        private const val KEY_HIDE_LOGS = "hide_logs"
        private const val DEFAULT_MODEL = "mvit32-2.onnx"
        
        fun getSelectedModel(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val model = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL) ?: DEFAULT_MODEL
            android.util.Log.d("SettingsActivity", "getSelectedModel() вызван: prefs=$PREFS_NAME, key=$KEY_MODEL_NAME, value=$model, all=${prefs.all}")
            return model
        }
        
        fun saveSelectedModel(context: Context, modelName: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_MODEL_NAME, modelName).apply()
        }
        
        fun getUseFrontCamera(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_USE_FRONT_CAMERA, true)
        }
        
        fun saveUseFrontCamera(context: Context, useFront: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_USE_FRONT_CAMERA, useFront).apply()
        }
        
        fun getShowLandmarks(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SHOW_LANDMARKS, true) // По умолчанию включено
        }
        
        fun saveShowLandmarks(context: Context, show: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_SHOW_LANDMARKS, show).apply()
        }
        
        // Camera resolution: "SD" (480x640), "HD" (720x1280), "FHD" (1080x1920)
        fun getCameraResolution(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_CAMERA_RESOLUTION, "HD") ?: "HD"
        }
        
        fun saveCameraResolution(context: Context, resolution: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CAMERA_RESOLUTION, resolution).apply()
        }
        
        // Camera FPS: 30 or 60
        fun getCameraFPS(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_CAMERA_FPS, 30) // По умолчанию 30 FPS
        }
        
        fun saveCameraFPS(context: Context, fps: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_CAMERA_FPS, fps).apply()
        }
        
        fun getEnableAutofocus(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ENABLE_AUTOFOCUS, true) // По умолчанию включен
        }
        
        fun saveEnableAutofocus(context: Context, enable: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLE_AUTOFOCUS, enable).apply()
        }
        
        fun getEnableStabilization(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ENABLE_STABILIZATION, true) // По умолчанию включена
        }
        
        fun saveEnableStabilization(context: Context, enable: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLE_STABILIZATION, enable).apply()
        }
        
        // Lighting correction settings
        fun getLightingMethod(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_LIGHTING_METHOD, "ADAPTIVE_BRIGHTNESS") ?: "ADAPTIVE_BRIGHTNESS"
        }
        
        fun saveLightingMethod(context: Context, method: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LIGHTING_METHOD, method).apply()
        }
        
        fun getGammaValue(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_GAMMA_VALUE, 1.2f)
        }
        
        fun saveGammaValue(context: Context, value: Float) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putFloat(KEY_GAMMA_VALUE, value).apply()
        }
        
        fun getCLAHEClipLimit(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_CLAHE_CLIP_LIMIT, 2.0f)
        }
        
        fun saveCLAHEClipLimit(context: Context, value: Float) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putFloat(KEY_CLAHE_CLIP_LIMIT, value).apply()
        }
        
        fun getHideLogs(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_HIDE_LOGS, false) // По умолчанию логи показываются
        }
        
        fun saveHideLogs(context: Context, hide: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_HIDE_LOGS, hide).apply()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Отслеживаем открытие настроек
        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)
        com.example.gesturetalk.utils.AchievementsManager.markSettingsOpened(this)
        
        setupToolbar()
        setupBottomNav()
        
        // Загружаем выбор ПОСЛЕ того как layout полностью создан
        binding.root.post {
            loadCurrentSelection()
            // Устанавливаем listeners ПОСЛЕ загрузки текущего выбора
            setupListeners()
            setupLogout()
        }
    }

    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
            firebaseService.signOut()
                .onSuccess {
                    UserProfileCache.clear(this)
                    Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, AuthActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
                .onFailure { error ->
                    Toast.makeText(this, "Ошибка выхода: ${error.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finishWithFade()
        }
    }

    private fun setupBottomNav() {
        setupGestureTalkBottomNav(binding.bottomNav, R.id.nav_profile) { nav ->
            nav.setOnItemSelectedListener { item ->
                if (item.itemId == R.id.nav_profile) {
                    true
                } else {
                    when (item.itemId) {
                        R.id.nav_guide_online -> {
                            openBottomNavTab(GuideOnlineActivity::class.java, R.id.nav_guide_online, R.id.nav_profile)
                            true
                        }
                        R.id.nav_ai -> {
                            openBottomNavTab(AskAiActivity::class.java, R.id.nav_ai, R.id.nav_profile)
                            true
                        }
                        R.id.nav_recognition -> {
                            openBottomNavTab(MainActivity::class.java, R.id.nav_recognition, R.id.nav_profile)
                            true
                        }
                        R.id.nav_stats -> {
                            openBottomNavTab(StatsActivity::class.java, R.id.nav_stats, R.id.nav_profile)
                            true
                        }
                        else -> false
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_profile
    }
    
    private fun loadCurrentSelection() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentModel = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL) ?: DEFAULT_MODEL
        
        android.util.Log.d("SettingsActivity", "=== ЗАГРУЗКА НАСТРОЕК ===")
        android.util.Log.d("SettingsActivity", "Текущая модель из prefs: $currentModel")
        
        // Модель
        binding.radioGroupModel.clearCheck()
        when (currentModel) {
            "mvit32-2.onnx" -> binding.radioMvit32.isChecked = true
            "asl1000.onnx" -> binding.radioAsl1000.isChecked = true
            else -> binding.radioMvit32.isChecked = true
        }
        
        // Разрешение камеры
        val resolution = getCameraResolution(this)
        when (resolution) {
            "SD" -> binding.radioResolutionSD.isChecked = true
            "HD" -> binding.radioResolutionHD.isChecked = true
            "FHD" -> binding.radioResolutionFHD.isChecked = true
            else -> binding.radioResolutionHD.isChecked = true
        }
        
        // FPS
        val fps = getCameraFPS(this)
        when (fps) {
            30 -> binding.radioFPS30.isChecked = true
            60 -> binding.radioFPS60.isChecked = true
            else -> binding.radioFPS30.isChecked = true
        }
        
        // Переключатели
        binding.switchAutofocus.isChecked = getEnableAutofocus(this)
        binding.switchStabilization.isChecked = getEnableStabilization(this)
        binding.switchShowLandmarks.isChecked = getShowLandmarks(this)
        binding.switchHideLogs.isChecked = getHideLogs(this)
        
        // Коррекция освещения
        val lightingMethod = getLightingMethod(this)
        when (lightingMethod) {
            "NONE" -> binding.radioLightingNone.isChecked = true
            "ADAPTIVE_BRIGHTNESS" -> binding.radioLightingAdaptive.isChecked = true
            "HISTOGRAM_EQUALIZATION" -> binding.radioLightingHistogram.isChecked = true
            "CLAHE" -> binding.radioLightingCLAHE.isChecked = true
            "GAMMA_CORRECTION" -> binding.radioLightingGamma.isChecked = true
            else -> binding.radioLightingAdaptive.isChecked = true
        }
        
        // Параметры
        val gammaValue = getGammaValue(this)
        val gammaProgress = ((gammaValue - 0.5f) / 1.5f * 30).toInt().coerceIn(0, 30)
        binding.seekBarGamma.progress = gammaProgress
        binding.textGammaValue.text = "%.1f".format(gammaValue)
        
        val claheClip = getCLAHEClipLimit(this)
        val claheProgress = ((claheClip - 1.0f) / 3.0f * 30).toInt().coerceIn(0, 30)
        binding.seekBarCLAHEClip.progress = claheProgress
        binding.textCLAHEClip.text = "%.1f".format(claheClip)
        
        // Показываем/скрываем параметры в зависимости от выбранного метода
        updateLightingParamsVisibility(lightingMethod)
        
        Toast.makeText(this, "Текущая модель: $currentModel", Toast.LENGTH_SHORT).show()
    }
    
    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
        
        // Listeners для переключателей (НЕ сохраняем сразу - только при нажатии кнопки "Сохранить")
        // Это нужно чтобы все настройки применялись одновременно при перезапуске
        
        // Listeners для коррекции освещения
        binding.radioGroupLighting.setOnCheckedChangeListener { _, checkedId ->
            val method = when (checkedId) {
                R.id.radioLightingNone -> "NONE"
                R.id.radioLightingAdaptive -> "ADAPTIVE_BRIGHTNESS"
                R.id.radioLightingHistogram -> "HISTOGRAM_EQUALIZATION"
                R.id.radioLightingCLAHE -> "CLAHE"
                R.id.radioLightingGamma -> "GAMMA_CORRECTION"
                else -> "ADAPTIVE_BRIGHTNESS"
            }
            updateLightingParamsVisibility(method)
        }
        
        binding.seekBarGamma.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val gamma = 0.5f + (progress / 30f) * 1.5f
                binding.textGammaValue.text = "%.1f".format(gamma)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        binding.seekBarCLAHEClip.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val clip = 1.0f + (progress / 30f) * 3.0f
                binding.textCLAHEClip.text = "%.1f".format(clip)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }
    
    private fun updateLightingParamsVisibility(method: String) {
        binding.layoutGammaParams.visibility = if (method == "GAMMA_CORRECTION") {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        
        binding.layoutCLAHEParams.visibility = if (method == "CLAHE") {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
    
    private fun saveSettings() {
        // Сохраняем модель
        val checkedModelId = binding.radioGroupModel.checkedRadioButtonId
        val selectedModel = when (checkedModelId) {
            R.id.radioMvit32 -> "mvit32-2.onnx"
            R.id.radioAsl1000 -> "asl1000.onnx"
            else -> "mvit32-2.onnx"
        }
        
        // Сохраняем разрешение камеры
        val checkedResolutionId = binding.radioGroupResolution.checkedRadioButtonId
        val selectedResolution = when (checkedResolutionId) {
            R.id.radioResolutionSD -> "SD"
            R.id.radioResolutionHD -> "HD"
            R.id.radioResolutionFHD -> "FHD"
            else -> "HD"
        }
        
        // Сохраняем FPS
        val checkedFPSId = binding.radioGroupFPS.checkedRadioButtonId
        val selectedFPS = when (checkedFPSId) {
            R.id.radioFPS30 -> 30
            R.id.radioFPS60 -> 60
            else -> 30
        }
        
        // Сохраняем коррекцию освещения
        val checkedLightingId = binding.radioGroupLighting.checkedRadioButtonId
        val selectedLightingMethod = when (checkedLightingId) {
            R.id.radioLightingNone -> "NONE"
            R.id.radioLightingAdaptive -> "ADAPTIVE_BRIGHTNESS"
            R.id.radioLightingHistogram -> "HISTOGRAM_EQUALIZATION"
            R.id.radioLightingCLAHE -> "CLAHE"
            R.id.radioLightingGamma -> "GAMMA_CORRECTION"
            else -> "ADAPTIVE_BRIGHTNESS"
        }
        
        val gammaValue = 0.5f + (binding.seekBarGamma.progress / 30f) * 1.5f
        val claheClip = 1.0f + (binding.seekBarCLAHEClip.progress / 30f) * 3.0f
        
        // Сохраняем переключатели
        val enableAutofocus = binding.switchAutofocus.isChecked
        val enableStabilization = binding.switchStabilization.isChecked
        val showLandmarks = binding.switchShowLandmarks.isChecked
        val hideLogs = binding.switchHideLogs.isChecked
        
        android.util.Log.d("SettingsActivity", "Сохранение настроек:")
        android.util.Log.d("SettingsActivity", "  Модель: $selectedModel")
        android.util.Log.d("SettingsActivity", "  Разрешение: $selectedResolution")
        android.util.Log.d("SettingsActivity", "  FPS: $selectedFPS")
        android.util.Log.d("SettingsActivity", "  Автофокус: $enableAutofocus")
        android.util.Log.d("SettingsActivity", "  Стабилизация: $enableStabilization")
        android.util.Log.d("SettingsActivity", "  Landmarks: $showLandmarks")
        android.util.Log.d("SettingsActivity", "  Освещение: $selectedLightingMethod")
        android.util.Log.d("SettingsActivity", "  Gamma: $gammaValue")
        android.util.Log.d("SettingsActivity", "  CLAHE: $claheClip")
        
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_MODEL_NAME, selectedModel)
            putString(KEY_CAMERA_RESOLUTION, selectedResolution)
            putInt(KEY_CAMERA_FPS, selectedFPS)
            putBoolean(KEY_ENABLE_AUTOFOCUS, enableAutofocus)
            putBoolean(KEY_ENABLE_STABILIZATION, enableStabilization)
            putBoolean(KEY_SHOW_LANDMARKS, showLandmarks)
            putBoolean(KEY_HIDE_LOGS, hideLogs)
            putString(KEY_LIGHTING_METHOD, selectedLightingMethod)
            putFloat(KEY_GAMMA_VALUE, gammaValue)
            putFloat(KEY_CLAHE_CLIP_LIMIT, claheClip)
            commit()
        }
        
        val lightingText = when (selectedLightingMethod) {
            "NONE" -> "Освещение: выкл"
            "ADAPTIVE_BRIGHTNESS" -> "Освещение: Adaptive"
            "HISTOGRAM_EQUALIZATION" -> "Освещение: Histogram"
            "CLAHE" -> "Освещение: CLAHE"
            "GAMMA_CORRECTION" -> "Освещение: Gamma"
            else -> ""
        }
        
        Toast.makeText(
            this, 
            "Сохранено:\n• $selectedModel\n• $selectedResolution @ ${selectedFPS}FPS\n• $lightingText\nПерезапуск...", 
            Toast.LENGTH_LONG
        ).show()
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finishAffinity()
        }, 1500)
    }
}


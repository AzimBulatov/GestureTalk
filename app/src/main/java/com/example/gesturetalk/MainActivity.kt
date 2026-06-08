package com.example.gesturetalk

// Главная активность приложения GestureTalk - распознавание жестов в реальном времени
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.gesturetalk.camera.CameraManager
import com.example.gesturetalk.databinding.ActivityMainBinding
import com.example.gesturetalk.ml.HybridGestureRecognizer
import com.example.gesturetalk.ml.ImagePreprocessor
import com.example.gesturetalk.ml.ModelConfig
import com.example.gesturetalk.ui.NavTransitions.navigateWithFade
import com.example.gesturetalk.ui.NavTransitions.openBottomNavTab
import com.example.gesturetalk.ui.NavTransitions.setupGestureTalkBottomNav
import com.example.gesturetalk.firebase.FirebaseAuthService
import com.example.gesturetalk.utils.Constants
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var cameraManager: CameraManager? = null
    private val firebaseService = FirebaseAuthService()
    
    // Используем общий интерфейс для обоих типов распознавателей
    private var gestureRecognizer: Any? = null // HybridGestureRecognizer или ASLGestureRecognizer
    
    private var recognitionJob: Job? = null
    private var timeTrackingJob: Job? = null
    
    private var isRecognizing = false
    private val wordHistory = mutableListOf<String>()
    private val logLines = mutableListOf<String>()
    
    private lateinit var config: ModelConfig
    
    // Кэшируем настройку визуализации чтобы не читать каждый кадр
    private var showLandmarks = true
    
    private var frameCallbackCount = 0
    private var lastFrameLogTime = 0L
    private var lastUIUpdateTime = 0L
    private var addFrameCallCount = 0
    private var restartCameraOnNextResume = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Проверяем авторизацию Firebase
        if (firebaseService.getCurrentUser() == null) {
            navigateWithFade(Intent(this, AuthActivity::class.java), finishCurrent = true)
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Инициализация достижений
        com.example.gesturetalk.utils.AchievementsManager.updateDaysStreak(this)
        com.example.gesturetalk.utils.AchievementsManager.checkTimeBasedAchievements(this)
        // НЕ вызываем startSession здесь - будем вызывать в onResume
        
        // ВРЕМЕННО: Сбрасываем некорректное время (удалить после исправления)
        val prefs = getSharedPreferences("GestureTalkStats", Context.MODE_PRIVATE)
        val currentTime = prefs.getInt("time_spent_minutes", 0)
        if (currentTime > 100) {  // Если больше 100 минут - явно неправильно
            prefs.edit().putInt("time_spent_minutes", 0).apply()
            addLog("Сброшено некорректное время: $currentTime минут")
        }
        
        // Загружаем и объединяем данные из Firebase
        val firebaseStats = com.example.gesturetalk.firebase.FirebaseStatsService()
        firebaseStats.mergeStats(this) { success ->
            if (success) {
                addLog("Статистика синхронизирована с Firebase")
            }
        }
        
        // НЕ запускаем периодическое сохранение здесь - будем запускать в onResume
        
        // Отладка достижений (можно закомментировать в продакшене)
        com.example.gesturetalk.utils.AchievementsDebugHelper.logCurrentState(this)
        
        setupUI()
        loadConfiguration()
        checkPermissionsAndInitialize()
    }
    
    private fun setupUI() {
        setupGestureTalkBottomNav(binding.bottomNav, R.id.nav_recognition) { nav ->
            nav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_guide_online -> {
                        openBottomNavTab(
                            GuideOnlineActivity::class.java,
                            R.id.nav_guide_online,
                            R.id.nav_recognition
                        )
                        true
                    }
                    R.id.nav_recognition -> true
                    R.id.nav_stats -> {
                        openBottomNavTab(
                            StatsActivity::class.java,
                            R.id.nav_stats,
                            R.id.nav_recognition
                        )
                        true
                    }
                    R.id.nav_ai -> {
                        openBottomNavTab(
                            AskAiActivity::class.java,
                            R.id.nav_ai,
                            R.id.nav_recognition
                        )
                        true
                    }
                    R.id.nav_profile -> {
                        openProfile()
                        true
                    }
                    else -> false
                }
            }
        }

        binding.btnStartStop.setOnClickListener {
            if (isRecognizing) {
                stopRecognition()
            } else {
                startRecognition()
            }
        }
        
        binding.btnClear.setOnClickListener {
            clearHistory()
        }
        
        binding.btnSettings.setOnClickListener {
            openProfile()
        }
        
        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }
        
        binding.btnInfo.setOnClickListener {
            showTutorial()
        }
        
        binding.btnCopyLogs.setOnClickListener {
            copyLogsToClipboard()
        }
    }
    
    /**
     * Копирование логов в буфер обмена
     */
    private fun copyLogsToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("GestureTalk Logs", logLines.joinToString("\n"))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Логи скопированы (${logLines.size} строк)", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Добавление лога в UI
     */
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logMessage = "[$timestamp] $message"
        
        Log.d(Constants.TAG, message)
        
        runOnUiThread {
            logLines.add(logMessage)
            
            // НЕ ограничиваем количество строк - храним ВСЕ логи
            // while (logLines.size > 100) {
            //     logLines.removeAt(0)
            // }
            
            // Проверяем что binding инициализирован
            if (::binding.isInitialized) {
                // Проверяем настройку скрытия логов
                val hideLogs = SettingsActivity.getHideLogs(this)
                
                if (hideLogs) {
                    binding.logsHeaderLayout.visibility = View.GONE
                    binding.tvLogs.visibility = View.GONE
                    binding.logScrollView.visibility = View.GONE
                } else {
                    binding.logsHeaderLayout.visibility = View.VISIBLE
                    binding.tvLogs.visibility = View.VISIBLE
                    binding.logScrollView.visibility = View.VISIBLE
                    binding.tvLogs.text = logLines.joinToString("\n")
                    
                    // Автоскролл вниз
                    binding.logScrollView.post {
                        binding.logScrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
    }
    
    /**
     * Загрузка конфигурации из assets
     */
    private fun loadConfiguration() {
        try {
            val json = assets.open("config.json").bufferedReader().use { it.readText() }
            val configData = Gson().fromJson(json, ConfigData::class.java)
            
            addLog("=== ЗАГРУЗКА МОДЕЛИ ===")
            
            // Получаем выбранную модель из настроек (ВАЖНО!)
            val selectedModel = SettingsActivity.getSelectedModel(this)
            
            addLog("getSelectedModel() вернул: $selectedModel")
            
            Log.d(Constants.TAG, "=== ЗАГРУЗКА КОНФИГУРАЦИИ ===")
            Log.d(Constants.TAG, "Выбранная модель из настроек: $selectedModel")
            
            // Логируем все настройки при старте
            val resolution = SettingsActivity.getCameraResolution(this)
            val fps = SettingsActivity.getCameraFPS(this)
            val autofocus = SettingsActivity.getEnableAutofocus(this)
            val stabilization = SettingsActivity.getEnableStabilization(this)
            val landmarks = SettingsActivity.getShowLandmarks(this)
            
            addLog("=== НАСТРОЙКИ ПРИЛОЖЕНИЯ ===")
            addLog("Модель: $selectedModel")
            addLog("Разрешение: $resolution")
            addLog("FPS: $fps")
            addLog("Автофокус: ${if (autofocus) "вкл" else "выкл"}")
            addLog("Стабилизация: ${if (stabilization) "вкл" else "выкл"}")
            addLog("Landmarks: ${if (landmarks) "вкл" else "выкл"}")
            addLog("===========================")
            
            // Читаем debug info из SettingsActivity
            val debugPrefs = getSharedPreferences("SettingsDebug", Context.MODE_PRIVATE)
            val lastSave = debugPrefs.getString("last_save", "нет данных")
            val lastLoad = debugPrefs.getString("last_load", "нет данных")
            
            addLog("=== DEBUG НАСТРОЕК ===")
            addLog("Последнее сохранение: $lastSave")
            addLog("Последняя загрузка: $lastLoad")
            
            // Читаем напрямую из SharedPreferences
            val mainPrefs = getSharedPreferences("GestureTalkPrefs", Context.MODE_PRIVATE)
            val directRead = mainPrefs.getString("model_name", "НЕ НАЙДЕНО")
            addLog("Прямое чтение из prefs: $directRead")
            addLog("Все ключи в prefs: ${mainPrefs.all.keys}")
            addLog("======================")
            
            // Slovo: второе число в имени mvit{T}-{interval}.onnx; для ASL — из config.json
            val mvitInterval = """mvit(\d+)-(\d+)\.onnx""".toRegex().find(selectedModel)?.groupValues?.getOrNull(2)?.toIntOrNull()
            val frameInterval = when {
                selectedModel.startsWith("asl", ignoreCase = true) -> configData.frame_interval
                mvitInterval != null -> mvitInterval
                else -> configData.frame_interval
            }
            
            config = ModelConfig(
                modelName = selectedModel,
                frameInterval = frameInterval,
                mean = configData.mean.map { it.toFloat() }.toFloatArray(),
                std = configData.std.map { it.toFloat() }.toFloatArray(),
                inputSize = configData.input_size,
                inferenceIntervalMs = configData.inference_interval_ms,
                stableRepeats = configData.stable_repeats,
                minConfidence = configData.min_confidence,
                repeatCooldownMs = configData.repeat_cooldown_ms,
                maxWordsDisplay = configData.max_words_display
            )
            
            addLog("Конфигурация загружена: ${config.modelName}")
            addLog("Интервал кадров: ${config.frameInterval}")
            addLog("Размер входа: ${config.inputSize}x${config.inputSize}")
            
            // Загружаем настройку визуализации landmarks
            showLandmarks = SettingsActivity.getShowLandmarks(this)
            addLog("Визуализация landmarks: ${if (showLandmarks) "включена" else "выключена"}")
            
            val modelInfo = when {
                config.modelName.startsWith("asl", ignoreCase = true) -> "ASL (landmarks)"
                mvitInterval != null -> "MViT, frame_interval из имени модели = $mvitInterval"
                else -> "Прочее (frame_interval из config.json)"
            }
            addLog("Тип модели: $modelInfo")
            
            Log.d(Constants.TAG, "Конфигурация создана: modelName=${config.modelName}, frameInterval=${config.frameInterval}")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to load configuration", e)
            addLog("ОШИБКА: Не удалось загрузить конфигурацию")
            Toast.makeText(this, "Ошибка загрузки конфигурации", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    /**
     * Проверка разрешений
     */
    private fun checkPermissionsAndInitialize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            initializeApp()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                Constants.CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == Constants.CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeApp()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.camera_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    /**
     * Инициализация приложения
     */
    private fun initializeApp() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvCurrentWord.text = getString(R.string.model_loading)
        addLog("Инициализация приложения...")
        
        lifecycleScope.launch {
            val modelPath = prepareModel()
            
            if (modelPath != null) {
                initializeRecognizer(modelPath)
            } else {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    addLog("ОШИБКА: Не удалось подготовить модель")
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.model_load_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Подготовка модели (копирование из res/raw в internal storage)
     */
    private suspend fun prepareModel(): String? = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(filesDir, "models")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            
            val modelFile = File(modelDir, config.modelName)
            
            // Удаляем старые модели (все кроме текущей)
            modelDir.listFiles()?.forEach { file ->
                if (file.name != config.modelName && file.name.endsWith(".onnx")) {
                    val deleted = file.delete()
                    Log.d(Constants.TAG, "Удалена старая модель: ${file.name}, success=$deleted")
                    withContext(Dispatchers.Main) {
                        addLog("Удалена старая модель: ${file.name}")
                    }
                }
            }
            
            // Если модель уже скопирована, используем её
            if (modelFile.exists()) {
                withContext(Dispatchers.Main) {
                    addLog("Используется кэшированная модель: ${config.modelName}")
                }
                Log.d(Constants.TAG, "Using cached model: ${modelFile.absolutePath}")
                return@withContext modelFile.absolutePath
            }
            
            // Копируем модель из res/raw
            val modelResourceName = config.modelName.replace("-", "_").replace(".onnx", "")
            val resourceId = resources.getIdentifier(modelResourceName, "raw", packageName)
            
            if (resourceId == 0) {
                Log.e(Constants.TAG, "Model resource not found: $modelResourceName")
                withContext(Dispatchers.Main) {
                    addLog("ОШИБКА: Ресурс модели не найден: $modelResourceName")
                }
                return@withContext null
            }
            
            withContext(Dispatchers.Main) {
                addLog("Копирование модели из res/raw/$modelResourceName...")
            }
            Log.d(Constants.TAG, "Copying model from res/raw/$modelResourceName...")
            
            resources.openRawResource(resourceId).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            withContext(Dispatchers.Main) {
                addLog("Модель скопирована: ${modelFile.length() / 1024 / 1024} МБ")
            }
            Log.d(Constants.TAG, "Model copied successfully: ${modelFile.absolutePath}")
            modelFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Error preparing model", e)
            withContext(Dispatchers.Main) {
                addLog("ОШИБКА при подготовке модели: ${e.message}")
            }
            null
        }
    }
    
    /**
     * Инициализация распознавателя
     */
    private suspend fun initializeRecognizer(modelPath: String) {
        addLog("Инициализация системы распознавания...")
        
        // Определяем тип модели по имени
        val isASLModel = config.modelName.startsWith("asl")
        
        if (isASLModel) {
            // ASL модели - используем ASLGestureRecognizer
            addLog("Тип: ASL TGCN (landmarks-based)")
            
            val aslRecognizer = com.example.gesturetalk.ml.ASLGestureRecognizer(
                this, 
                config.modelName,
                { logMessage -> addLog(logMessage) }
            )
            
            val success = aslRecognizer.initialize()
            
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                
                if (success) {
                    gestureRecognizer = aslRecognizer
                    val actualWindowSize = aslRecognizer.getWindowSize()
                    binding.tvCurrentWord.text = getString(R.string.show_gesture)
                    // Статистика на превью
                    binding.tvCameraStats.text =
                        getString(
                            R.string.camera_stats_asl,
                            0,
                            actualWindowSize,
                            getString(R.string.asl_status_buffering)
                        )
                    
                    addLog("✓ ASL система готова")
                    addLog("Размер окна: $actualWindowSize кадров")
                    initializeCamera()
                } else {
                    addLog("ОШИБКА: Не удалось инициализировать ASL модель")
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка загрузки ASL модели",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            // Slovo модели - используем HybridGestureRecognizer
            addLog("Тип: РЖЯ-1000 (video-based)")
            
            val hybridRecognizer = HybridGestureRecognizer(this, config) { logMessage ->
                addLog(logMessage)
            }
            
            val success = hybridRecognizer.initialize(modelPath)
            
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                
                if (success) {
                    gestureRecognizer = hybridRecognizer
                    val actualWindowSize = hybridRecognizer.getWindowSize()
                    binding.tvCurrentWord.text = getString(R.string.show_gesture)
                    // Обновляем счетчик кадров
                    binding.tvCameraStats.text =
                        getString(R.string.camera_stats_hybrid, 0, actualWindowSize, 0)
                    
                    addLog("✓ Slovo система готова")
                    addLog("Размер окна: $actualWindowSize кадров")
                    addLog("Интервал: ${config.frameInterval}")
                    initializeCamera()
                } else {
                    addLog("ОШИБКА: Не удалось инициализировать Slovo модель")
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.model_load_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Инициализация камеры
     */
    private fun initializeCamera() {
        addLog("Инициализация камеры...")
        cameraManager = CameraManager(this, this, binding.previewView) { message ->
            addLog(message)
        }
        
        // Загружаем сохраненный выбор камеры
        val useFrontCamera = SettingsActivity.getUseFrontCamera(this)
        cameraManager?.setFrontCamera(useFrontCamera)
        
        cameraManager?.startCamera { bitmap ->
            onCameraFrame(bitmap)
        }
        addLog("✓ Камера запущена (${binding.previewView.width}x${binding.previewView.height})")
        addLog("Камера: ${if (useFrontCamera) "фронтальная" else "задняя"}")
    }
    
    /**
     * Обработка кадра с камеры
     */
    private fun onCameraFrame(bitmap: Bitmap) {
        frameCallbackCount++
        
        val currentTime = System.currentTimeMillis()
        
        if (frameCallbackCount == 1) {
            addLog("✓ Первый кадр получен от камеры (${bitmap.width}x${bitmap.height})")
        }
        
        // Логируем РЕДКО - раз в 3 секунды
        if ((currentTime - lastFrameLogTime) > 3000) {
            addLog("Камера: $frameCallbackCount кадров")
            lastFrameLogTime = currentTime
        }
        
        // Обновляем счетчик кадров ВСЕГДА (даже если распознавание не запущено)
        // НО не чаще чем раз в 100ms чтобы не лагало
        val recognizer = gestureRecognizer
        if (recognizer != null && (currentTime - lastUIUpdateTime) > 100) {
            runOnUiThread {
                when (recognizer) {
                    is HybridGestureRecognizer -> {
                        val seg = recognizer.getCurrentFrameCount()
                        val q = recognizer.getPendingInferenceCount()
                        val busy = recognizer.isInferenceRunning()
                        val modelStatus = recognizer.getUiStatusText()
                        val onnx = if (busy) "\nONNX…" else ""
                        binding.tvCameraStats.text =
                            getString(R.string.camera_stats_hybrid, seg, recognizer.getWindowSize(), q) + onnx
                        binding.tvCurrentWord.text = modelStatus
                    }
                    is com.example.gesturetalk.ml.ASLGestureRecognizer -> {
                        val currentFrames = recognizer.getCurrentFrameCount()
                        val windowSize = recognizer.getWindowSize()
                        val status = if (currentFrames < windowSize) {
                            getString(R.string.asl_status_buffering)
                        } else {
                            getString(R.string.asl_status_ready)
                        }
                        binding.tvCameraStats.text =
                            getString(R.string.camera_stats_asl, currentFrames, windowSize, status)
                        if (isRecognizing) {
                            binding.tvCurrentWord.text = status
                        }
                    }
                    else -> { }
                }
            }
            lastUIUpdateTime = currentTime
        }
        
        if (!isRecognizing) {
            if (frameCallbackCount == 2) {
                addLog("Камера работает, но распознавание не запущено. Нажмите СТАРТ.")
            }
            return
        }
        
        try {
            // SLOVO: Вызываем addFrame для КАЖДОГО кадра камеры!
            // Внутри addFrame уже есть фильтрация по frame_interval
            when (val gRec = gestureRecognizer) {
                is HybridGestureRecognizer -> {
                    gRec.addFrame(bitmap)
                    updateLandmarksOverlay(gRec.getLastDetectionResult())
                }
                is com.example.gesturetalk.ml.ASLGestureRecognizer -> {
                    gRec.addFrame(bitmap)
                    updateLandmarksOverlay(gRec.getLastDetectionResult())
                }
                else -> {
                    if (addFrameCallCount == 0) {
                        addLog("ОШИБКА: gestureRecognizer неизвестного типа!")
                    }
                }
            }
            addFrameCallCount++
        } catch (e: Exception) {
            addLog("ОШИБКА в addFrame(): ${e.message}")
            Log.e(Constants.TAG, "Error in addFrame", e)
        }
        // НЕ вызываем bitmap.recycle() - addFrame() может использовать bitmap асинхронно
        // GC сам освободит память когда нужно
    }
    
    /**
     * Запуск распознавания
     */
    private fun startRecognition() {
        isRecognizing = true
        binding.btnStartStop.text = getString(R.string.stop)
        binding.btnStartStop.setIconResource(android.R.drawable.ic_media_pause)
        binding.tvCurrentWord.text = getString(R.string.show_gesture)
        
        addLog("--- Распознавание запущено ---")
        
        // Slovo: результаты приходят из очереди сегментов пока камера продолжает работу.
        // ASL: прежний цикл по завершению жеста.
        recognitionJob = lifecycleScope.launch(Dispatchers.Default) {
            when (val rec = gestureRecognizer) {
                is HybridGestureRecognizer -> {
                    rec.pipelineResults.collect { result ->
                        if (!isRecognizing) return@collect
                        try {
                            val lastWord = if (wordHistory.isNotEmpty()) wordHistory.last() else null
                            val word = rec.processResult(result, lastWord)
                            if (word != null) {
                                addLog(">>> ✓ РАСПОЗНАНО (конвейер): $word (${String.format("%.1f", result.confidence * 100)}%) <<<")
                                withContext(Dispatchers.Main) {
                                    onWordRecognized(word)
                                }
                            } else {
                                // Жест не прошел фильтр (низкая уверенность или повтор)
                                if (result.confidence < config.minConfidence) {
                                    addLog("  ✗ Низкая уверенность: ${result.word} (${String.format("%.1f", result.confidence * 100)}%)")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(Constants.TAG, "Hybrid pipeline", e)
                        }
                    }
                }
                is com.example.gesturetalk.ml.ASLGestureRecognizer -> {
                    var cycleCount = 0
                    while (isRecognizing) {
                        cycleCount++
                        val aslRecognizer = rec
                        val currentFrames = aslRecognizer.getCurrentFrameCount()
                        val windowSize = aslRecognizer.getWindowSize()
                        
                        if (aslRecognizer.isGestureComplete()) {
                            addLog("Цикл #$cycleCount: Жест завершен! (движение остановилось)")
                            addLog("  Кадров собрано: $currentFrames")
                            aslRecognizer.padBufferToWindowSize()
                            addLog("  ✓ Буфер дополнен до $windowSize кадров")
                            addLog("  Модель думает... (это займет ~5 секунд)")
                            withContext(Dispatchers.Main) {
                                binding.tvCurrentWord.text = getString(R.string.asl_recognizing)
                            }
                            try {
                                val result = withTimeout(15000) {
                                    aslRecognizer.recognize()
                                }
                                if (result != null) {
                                    addLog("✓ Модель закончила думать!")
                                    addLog("  Результат: '${result.word}' (${String.format("%.1f", result.confidence * 100)}%)")
                                    val word = aslRecognizer.processResult(result)
                                    if (word != null) {
                                        addLog(">>> ✓ РАСПОЗНАНО: $word <<<")
                                        withContext(Dispatchers.Main) {
                                            onWordRecognized(word)
                                        }
                                    } else {
                                        addLog("  ✗ Не показано (фильтр не пропустил)")
                                    }
                                } else {
                                    addLog("  ОШИБКА: recognize() вернул null")
                                }
                                aslRecognizer.clearBuffer()
                            } catch (e: TimeoutCancellationException) {
                                addLog("ОШИБКА: Распознавание зависло (таймаут 15 сек)")
                                aslRecognizer.clearBuffer()
                            } catch (e: Exception) {
                                addLog("ОШИБКА в цикле распознавания: ${e.message}")
                                Log.e(Constants.TAG, "Recognition loop error", e)
                                aslRecognizer.clearBuffer()
                            } finally {
                                withContext(Dispatchers.Main) {
                                    binding.tvCurrentWord.text = getString(R.string.show_gesture)
                                }
                            }
                        } else {
                            if (cycleCount % 5 == 0) {
                                addLog("Цикл #$cycleCount: Жду завершения жеста ($currentFrames кадров)")
                            }
                        }
                        delay(config.inferenceIntervalMs)
                    }
                }
                else -> {
                    while (isRecognizing) {
                        addLog("ОШИБКА: recognizer не инициализирован")
                        delay(config.inferenceIntervalMs)
                    }
                }
            }
        }
    }
    
    /**
     * Остановка распознавания
     */
    private fun stopRecognition() {
        isRecognizing = false
        recognitionJob?.cancel()
        recognitionJob = null
        
        // Проверяем что binding инициализирован
        if (::binding.isInitialized) {
            binding.btnStartStop.text = getString(R.string.start)
            binding.btnStartStop.setIconResource(android.R.drawable.ic_media_play)
            binding.tvCurrentWord.text = getString(R.string.show_gesture)
        }
        
        addLog("--- Распознавание остановлено ---")
    }
    
    /**
     * Обработка распознанного слова
     */
    private fun onWordRecognized(word: String) {
        wordHistory.add(word)
        
        // Ограничиваем историю
        while (wordHistory.size > config.maxWordsDisplay) {
            wordHistory.removeAt(0)
        }
        
        updateHistoryDisplay()
        
        // Обновляем достижения
        com.example.gesturetalk.utils.AchievementsManager.incrementGesturesRecognized(this)
    }
    
    /**
     * Обновление отображения истории
     */
    private fun updateHistoryDisplay() {
        binding.tvWordHistory.text = wordHistory.joinToString("  ")
        
        // Автоскролл вправо
        binding.tvWordHistory.post {
            val scrollView = binding.tvWordHistory.parent as? android.widget.HorizontalScrollView
            scrollView?.fullScroll(View.FOCUS_RIGHT)
        }
    }
    
    /**
     * Обновление overlay с landmarks для визуализации
     */
    private fun updateLandmarksOverlay(detection: com.example.gesturetalk.mediapipe.HandDetector.HandDetectionResult?) {
        // Используем кэшированное значение вместо чтения из SharedPreferences каждый кадр
        if (!showLandmarks) {
            return // Не обновляем overlay если визуализация выключена
        }
        
        if (detection != null && detection.hasHands) {
            binding.landmarksOverlay.updateLandmarks(
                pose = detection.poseLandmarks,
                hands = detection.handLandmarks,
                center = detection.centerPoint,
                box = detection.boundingBox
            )
        } else {
            // Нет рук - очищаем overlay
            binding.landmarksOverlay.clear()
        }
    }
    
    /**
     * Очистка истории
     */
    private fun clearHistory() {
        wordHistory.clear()
        logLines.clear()
        binding.tvWordHistory.text = ""
        binding.tvLogs.text = ""
        binding.tvCurrentWord.text = getString(R.string.show_gesture)
        addLog("История и логи очищены")
    }
    
    /**
     * Переключение камеры
     */
    private fun switchCamera() {
        cameraManager?.switchCamera()
        val isFront = cameraManager?.isFrontCamera() ?: true
        
        // Сохраняем выбор
        SettingsActivity.saveUseFrontCamera(this, isFront)
        
        addLog("Камера переключена: ${if (isFront) "фронтальная" else "задняя"}")
        Toast.makeText(
            this,
            if (isFront) "Фронтальная камера" else "Задняя камера",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Показать инструкцию по использованию
     */
    private fun showTutorial() {
        com.example.gesturetalk.ui.TutorialDialog(this).show()
    }
    
    /**
     * Открытие профиля
     */
    private fun openProfile() {
        restartCameraOnNextResume = true
        openBottomNavTab(
            ProfileActivity::class.java,
            R.id.nav_profile,
            R.id.nav_recognition
        )
    }
    
    override fun onResume() {
        super.onResume()
        binding.bottomNav.selectedItemId = R.id.nav_recognition
        
        // Начинаем отслеживание времени когда приложение активно
        com.example.gesturetalk.utils.AchievementsManager.startSession(this)
        startTimeTracking()
        
        // Обновляем настройку визуализации при возврате из настроек
        showLandmarks = SettingsActivity.getShowLandmarks(this)
        
        // Если визуализация выключена - очищаем overlay
        if (!showLandmarks) {
            binding.landmarksOverlay.clear()
        }
        
        // Перезапускаем камеру только после экрана настроек (иначе вкладки дергаются и лагают).
        if (restartCameraOnNextResume) {
            cameraManager?.restartCamera()
            restartCameraOnNextResume = false
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Останавливаем отслеживание времени когда приложение уходит в фон
        timeTrackingJob?.cancel()
        timeTrackingJob = null
        
        // Завершаем сессию и сохраняем время
        com.example.gesturetalk.utils.AchievementsManager.endSession(this)
    }
    
    /**
     * Запуск периодического отслеживания времени
     */
    private fun startTimeTracking() {
        timeTrackingJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                delay(60000) // Каждую минуту
                com.example.gesturetalk.utils.AchievementsManager.saveCurrentSessionTime(this@MainActivity)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Останавливаем отслеживание времени (на случай если onPause не вызвался)
        timeTrackingJob?.cancel()
        timeTrackingJob = null
        
        // Проверяем что binding инициализирован перед использованием
        if (::binding.isInitialized) {
            stopRecognition()
        }
        
        cameraManager?.release()
        
        // Освобождаем ресурсы в зависимости от типа recognizer
        when (val recognizer = gestureRecognizer) {
            is HybridGestureRecognizer -> recognizer.release()
            is com.example.gesturetalk.ml.ASLGestureRecognizer -> recognizer.release()
        }
    }
}

/**
 * Data class для парсинга конфигурации
 */
data class ConfigData(
    val model_name: String,
    val frame_interval: Int,
    val mean: List<Double>,
    val std: List<Double>,
    val input_size: Int,
    val inference_interval_ms: Long,
    val stable_repeats: Int,
    val min_confidence: Float,
    val repeat_cooldown_ms: Long,
    val max_words_display: Int
)


package com.example.gesturetalk.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.gesturetalk.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Движок распознавания жестов на базе ONNX Runtime (логика Slovo demo.py).
 * MediaPipe не используется здесь — только полный кадр → препроцессинг → окно → ONNX.
 */
class GestureRecognizer(
    private val context: Context,
    private val config: ModelConfig,
    private val onLog: ((String) -> Unit)? = null
) {
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val preprocessor = ImagePreprocessor(config.mean, config.std, config.inputSize, onLog)
    
    // Словарь классов (index -> слово)
    private val classes: Map<String, String> by lazy {
        loadClasses()
    }
    
    // Окно кадров для модели
    private val frameWindow = Collections.synchronizedList(mutableListOf<FloatArray>())
    
    private var windowSize = 32 // По умолчанию, будет обновлено из модели
    
    // SLOVO APPROACH: Простой счетчик кадров для frame_interval
    private var frameCounter = 0
    
    /** Пока идёт инференс — не кладём новые кадры в буфер (как один поток в demo.py во время run). */
    private val isInferencing = AtomicBoolean(false)
    
    /**
     * Инициализация ONNX Runtime и загрузка модели
     */
    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(Constants.TAG, "Initializing ONNX Runtime...")
            
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            
            // На big.LITTLE 6–8 потоков часто перегружает малые ядра и растягивает MViT; 2–4 обычно стабильнее.
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val intra = minOf(4, maxOf(2, cores / 2))
            sessionOptions.setIntraOpNumThreads(intra)
            sessionOptions.setInterOpNumThreads(1)
            Log.d(Constants.TAG, "ORT: CPU, intraOpThreads=$intra (ядер=$cores)")
            onLog?.invoke("ORT: CPU, потоки intra=$intra")
            
            // Загружаем модель
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(Constants.TAG, "Model file not found: $modelPath")
                return@withContext false
            }
            
            ortSession = ortEnvironment?.createSession(modelPath, sessionOptions)
            
            // ДЕТАЛЬНАЯ ИНФОРМАЦИЯ О МОДЕЛИ
            val inputInfo = ortSession?.inputInfo?.values?.firstOrNull()
            val inputTensorInfo = inputInfo?.info as? ai.onnxruntime.TensorInfo
            val shape = inputTensorInfo?.shape
            
            Log.d(Constants.TAG, "=== ИНФОРМАЦИЯ О МОДЕЛИ ===")
            Log.d(Constants.TAG, "Input name: ${ortSession?.inputNames?.firstOrNull()}")
            Log.d(Constants.TAG, "Input shape: ${shape?.contentToString()}")
            Log.d(Constants.TAG, "Input type: ${inputTensorInfo?.type}")
            
            onLog?.invoke("=== МОДЕЛЬ ===")
            onLog?.invoke("Shape: ${shape?.contentToString()}")
            
            val outputInfo = ortSession?.outputInfo?.values?.firstOrNull()
            val outputTensorInfo = outputInfo?.info as? ai.onnxruntime.TensorInfo
            val outputShape = outputTensorInfo?.shape
            
            Log.d(Constants.TAG, "Output name: ${ortSession?.outputNames?.firstOrNull()}")
            Log.d(Constants.TAG, "Output shape: ${outputShape?.contentToString()}")
            Log.d(Constants.TAG, "Output type: ${outputTensorInfo?.type}")
            
            onLog?.invoke("Output: ${outputShape?.contentToString()}")
            
            // Etalon Slovo: window_size из входа ONNX (input_shape[3]); имя файла — запасной вариант.
            val modelName = config.modelName
            val fromShape = shape?.getOrNull(3)?.takeIf { it > 0 }?.toInt()
            val regex = """mvit(\d+)-(\d+)\.onnx""".toRegex()
            val matchResult = regex.find(modelName)
            val fromName = matchResult?.groupValues?.getOrNull(1)?.toIntOrNull()
            
            windowSize = when {
                fromShape != null -> fromShape
                fromName != null -> fromName
                else -> {
                    Log.w(Constants.TAG, "Не удалось определить размер окна из ONNX/имени, используем 32")
                    onLog?.invoke("Размер окна: 32 (fallback)")
                    32
                }
            }
            Log.d(Constants.TAG, "Размер окна: $windowSize (shape[3]=$fromShape, имя=$fromName)")
            onLog?.invoke("Размер окна: $windowSize кадров (${if (fromShape != null) "ONNX" else "имя/fallback"})")
            
            Log.d(Constants.TAG, "Model initialized successfully")
            Log.d(Constants.TAG, "Window size: $windowSize кадров")
            
            runWarmupInference()
            
            true
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to initialize model", e)
            onLog?.invoke("ОШИБКА инициализации: ${e.message}")
            false
        }
    }
    
    /** Прогон нулями: разогрев графа на CPU, первый реальный forward бывает заметно короче. */
    private fun runWarmupInference() {
        val session = ortSession ?: return
        val env = ortEnvironment ?: return
        try {
            val channelSize = config.inputSize * config.inputSize
            val inputData = FloatArray(3 * windowSize * channelSize)
            val shape = longArrayOf(1, 1, 3, windowSize.toLong(), config.inputSize.toLong(), config.inputSize.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape)
            val inputName = session.inputNames.iterator().next()
            val results = session.run(mapOf(inputName to inputTensor))
            inputTensor.close()
            results.close()
            Log.d(Constants.TAG, "ORT: warmup inference выполнен")
            onLog?.invoke("ORT: прогрев (warmup) выполнен")
        } catch (e: Exception) {
            Log.w(Constants.TAG, "ORT warmup пропущен: ${e.message}")
        }
    }
    
    /**
     * Сброс неполного окна: руки пропали из кадра — не смешиваем фон и жест.
     */
    fun discardPartialWindow() {
        if (isInferencing.get()) {
            return
        }
        synchronized(frameWindow) {
            if (frameWindow.isEmpty()) {
                return
            }
            frameWindow.clear()
            frameCounter = 0
            Log.d(Constants.TAG, "MViT буфер сброшен (нет рук в зоне жеста)")
        }
    }
    
    /** Один кадр → тензор препроцессинга (для конвейера сегментов). */
    fun preprocessBitmap(bitmap: Bitmap): FloatArray = preprocessor.preprocess(bitmap)
    
    /**
     * Приводит произвольное число кадров к [windowSize] для фиксированного входа ONNX:
     * длиннее — равномерная выборка, короче — паддинг копиями краёв (как при укороченном жесте).
     */
    fun normalizeFramesToWindow(frames: List<FloatArray>): List<FloatArray> {
        if (frames.isEmpty()) return emptyList()
        val n = frames.size
        val w = windowSize
        if (n == w) return frames.map { it.clone() }
        if (n > w) {
            return (0 until w).map { wi ->
                val idx = if (w == 1) 0 else (wi * (n - 1).toDouble() / (w - 1)).roundToInt().coerceIn(0, n - 1)
                frames[idx].clone()
            }
        }
        val out = frames.map { it.clone() }.toMutableList()
        var flip = 0
        while (out.size < w) {
            if (flip++ % 2 == 0) out.add(0, out.first().clone())
            else out.add(out.last().clone())
        }
        return out.take(w)
    }
    
    /**
     * Инференс по уже собранному сегменту (очередь в фоне). Не трогает скользящий [frameWindow].
     */
    suspend fun recognizeFromFrames(rawFrames: List<FloatArray>): RecognitionResult? = withContext(Dispatchers.Default) {
        if (rawFrames.isEmpty()) {
            return@withContext null
        }
        val session = ortSession ?: run {
            onLog?.invoke("ОШИБКА: OrtSession is null")
            return@withContext null
        }
        val env = ortEnvironment ?: run {
            onLog?.invoke("ОШИБКА: OrtEnvironment is null")
            return@withContext null
        }
        val frames = normalizeFramesToWindow(rawFrames)
        if (!isInferencing.compareAndSet(false, true)) {
            onLog?.invoke("MViT: пропуск сегмента — предыдущий инференс ещё идёт")
            return@withContext null
        }
        try {
            onLog?.invoke("Inference (сегмент): ${rawFrames.size} кадров → $windowSize, запуск ONNX...")
            val t0 = System.currentTimeMillis()
            val channelSize = config.inputSize * config.inputSize
            val inputData = FloatArray(3 * windowSize * channelSize)
            for (c in 0 until 3) {
                for (frameIdx in 0 until windowSize) {
                    val frame = frames[frameIdx]
                    val srcOffset = c * channelSize
                    val dstOffset = c * windowSize * channelSize + frameIdx * channelSize
                    System.arraycopy(frame, srcOffset, inputData, dstOffset, channelSize)
                }
            }
            val tPacked = System.currentTimeMillis()
            val shape = longArrayOf(1, 1, 3, windowSize.toLong(), config.inputSize.toLong(), config.inputSize.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape)
            onLog?.invoke("Выполнение inference (сегмент)...")
            val inputName = session.inputNames.iterator().next()
            val tRunStart = System.currentTimeMillis()
            val results = session.run(mapOf(inputName to inputTensor))
            val runMs = System.currentTimeMillis() - tRunStart
            onLog?.invoke("ORT: упаковка ${tPacked - t0}ms, session.run=${runMs}ms")
            onLog?.invoke("Обработка результата...")
            val output = results[0].value as Array<*>
            val probs = output[0] as FloatArray
            var maxIdx = 0
            var maxVal = probs[0]
            for (i in 1 until probs.size) {
                if (probs[i] > maxVal) {
                    maxVal = probs[i]
                    maxIdx = i
                }
            }
            val confidence = probs[maxIdx]
            val word = classes[maxIdx.toString()] ?: Constants.EMPTY_GESTURE
            val top5 = probs.indices.sortedByDescending { probs[it] }.take(5)
            val top5String = top5.joinToString(", ") { idx ->
                val wrd = classes[idx.toString()] ?: "?"
                "$wrd(${"%.3f".format(probs[idx])})"
            }
            onLog?.invoke("Топ-5: $top5String")
            val inferenceTime = System.currentTimeMillis() - t0
            inputTensor.close()
            results.close()
            RecognitionResult(word, confidence, inferenceTime)
        } catch (e: Exception) {
            Log.e(Constants.TAG, "recognizeFromFrames: ${e.message}", e)
            onLog?.invoke("ОШИБКА сегмента: ${e.message}")
            null
        } finally {
            isInferencing.set(false)
        }
    }
    
    /**
     * Загрузка словаря классов из assets
     */
    private fun loadClasses(): Map<String, String> {
        return try {
            val json = context.assets.open("classes.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, String>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Failed to load classes", e)
            emptyMap()
        }
    }
    
    /**
     * Добавление кадра в окно (ТОЧНО КАК В SLOVO demo.py!)
     */
    fun addFrame(bitmap: Bitmap) {
        if (isInferencing.get()) {
            return
        }
        
        // SLOVO: frame_counter == frame_interval (не >=)
        frameCounter++
        
        if (frameCounter == config.frameInterval) {
            frameCounter = 0
            
            try {
                if (isInferencing.get()) {
                    return
                }
                val tensor = preprocessor.preprocess(bitmap)
                
                synchronized(frameWindow) {
                    if (isInferencing.get()) {
                        return
                    }
                    frameWindow.add(tensor)
                }
                
            } catch (e: Exception) {
                Log.e(Constants.TAG, "GestureRecognizer: ОШИБКА препроцессинга", e)
            }
        }
    }
    
    /**
     * Проверка готовности к распознаванию (SLOVO APPROACH)
     */
    fun isReadyForInference(): Boolean {
        return synchronized(frameWindow) {
            frameWindow.size >= windowSize
        }
    }
    
    /**
     * SLOVO: Полная очистка буфера после inference
     */
    /** Вызывать только под synchronized(frameWindow). */
    private fun clearTensorsUnlocked() {
        repeat(windowSize) {
            if (frameWindow.isNotEmpty()) {
                frameWindow.removeAt(0)
            }
        }
        Log.d(Constants.TAG, "GestureRecognizer: Буфер очищен ($windowSize кадров)")
        onLog?.invoke("Буфер очищен, готов к новому жесту")
    }
    
    /**
     * Выполнение инференса (SLOVO APPROACH - когда буфер заполнен)
     */
    suspend fun recognize(): RecognitionResult? = withContext(Dispatchers.Default) {
        val session = ortSession ?: run {
            Log.e(Constants.TAG, "OrtSession is null!")
            onLog?.invoke("ОШИБКА: OrtSession is null")
            return@withContext null
        }
        val env = ortEnvironment ?: run {
            Log.e(Constants.TAG, "OrtEnvironment is null!")
            onLog?.invoke("ОШИБКА: OrtEnvironment is null")
            return@withContext null
        }
        
        synchronized(frameWindow) {
            if (frameWindow.size < windowSize) {
                return@withContext null
            }
            if (!isInferencing.compareAndSet(false, true)) {
                return@withContext null
            }
        }
        
        try {
            Log.d(Constants.TAG, "Начинаем inference (буфер ≥ $windowSize)")
            onLog?.invoke("Inference: формирование тензора...")
            
            val t0 = System.currentTimeMillis()
            
            val channelSize = config.inputSize * config.inputSize
            val inputData = FloatArray(3 * windowSize * channelSize)
            
            synchronized(frameWindow) {
                for (c in 0 until 3) {
                    for (frameIdx in 0 until windowSize) {
                        val frame = frameWindow[frameIdx]
                        val srcOffset = c * channelSize
                        val dstOffset = c * windowSize * channelSize + frameIdx * channelSize
                        System.arraycopy(frame, srcOffset, inputData, dstOffset, channelSize)
                    }
                }
            }
            
            val tPacked = System.currentTimeMillis()
            
            Log.d(Constants.TAG, "Входной тензор: [1,1,3,$windowSize,${config.inputSize},${config.inputSize}]")
            onLog?.invoke("Тензор готов, запуск ONNX...")
            
            // Создаем тензор правильной формы [1, 1, 3, windowSize, 224, 224]
            val shape = longArrayOf(1, 1, 3, windowSize.toLong(), config.inputSize.toLong(), config.inputSize.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape)
            
            onLog?.invoke("Выполнение inference...")
            
            val inputName = session.inputNames.iterator().next()
            val tRunStart = System.currentTimeMillis()
            val results = session.run(mapOf(inputName to inputTensor))
            val runMs = System.currentTimeMillis() - tRunStart
            
            Log.d(Constants.TAG, "ORT: pack=${tPacked - t0}ms, session.run=${runMs}ms")
            onLog?.invoke("ORT: упаковка ${tPacked - t0}ms, session.run=${runMs}ms")
            
            Log.d(Constants.TAG, "Inference завершен, обрабатываем результат...")
            onLog?.invoke("Обработка результата...")
            
            // Получаем выход (модель уже выдает вероятности, не логиты!)
            val output = results[0].value as Array<*>
            val probs = output[0] as FloatArray
            
            Log.d(Constants.TAG, "Выходной тензор: size=${probs.size}")
            
            // Находим argmax
            var maxIdx = 0
            var maxVal = probs[0]
            for (i in 1 until probs.size) {
                if (probs[i] > maxVal) {
                    maxVal = probs[i]
                    maxIdx = i
                }
            }
            
            val confidence = probs[maxIdx]
            val word = classes[maxIdx.toString()] ?: Constants.EMPTY_GESTURE
            
            // Логируем топ-5 предсказаний
            val top5 = probs.indices.sortedByDescending { probs[it] }.take(5)
            val top5String = top5.joinToString(", ") { idx ->
                val w = classes[idx.toString()] ?: "?"
                "$w(${"%.3f".format(probs[idx])})"
            }
            Log.d(Constants.TAG, "Топ-5: $top5String")
            onLog?.invoke("Топ-5: $top5String")
            
            val inferenceTime = System.currentTimeMillis() - t0
            
            inputTensor.close()
            results.close()
            
            synchronized(frameWindow) {
                clearTensorsUnlocked()
            }
            
            Log.d(Constants.TAG, "✓ Результат: класс=$maxIdx, слово='$word', conf=${"%.3f".format(confidence)}, время=${inferenceTime}ms")
            
            RecognitionResult(word, confidence, inferenceTime)
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "ОШИБКА inference: ${e.javaClass.simpleName}: ${e.message}", e)
            onLog?.invoke("ОШИБКА: ${e.message}")
            e.printStackTrace()
            null
        } finally {
            isInferencing.set(false)
        }
    }
    
    /**
     * Постпроцессинг с фильтрацией (SLOVO APPROACH)
     */
    fun processResult(result: RecognitionResult?, lastWord: String?): String? {
        if (result == null) {
            return null
        }
        
        val word = result.word
        
        Log.d(Constants.TAG, "processResult: word='$word', confidence=${result.confidence}, lastWord='$lastWord'")
        
        // SLOVO: Фильтруем пустые жесты "---"
        if (word == Constants.EMPTY_GESTURE || word == "---") {
            Log.d(Constants.TAG, "  Отфильтровано: пустой жест")
            return null
        }
        
        // SLOVO demo.py: не показывать повтор того же глосса подряд
        if (word == lastWord) {
            Log.d(Constants.TAG, "  Отфильтровано: дубликат")
            return null
        }
        
        Log.d(Constants.TAG, "  ✓ Слово принято")
        return word
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        try {
            ortSession?.close()
            ortSession = null
            frameWindow.clear()
            frameCounter = 0
            isInferencing.set(false)
            Log.d(Constants.TAG, "GestureRecognizer released")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Error releasing resources", e)
        }
    }
    
    fun getWindowSize(): Int = windowSize
    fun getCurrentFrameCount(): Int = synchronized(frameWindow) { frameWindow.size }
    
    /** Пока идёт session.run — addFrame в GestureRecognizer не принимает кадры (счётчик интервала не крутится). */
    fun isInferenceRunning(): Boolean = isInferencing.get()
}

data class RecognitionResult(
    val word: String,
    val confidence: Float,
    val inferenceTimeMs: Long
)

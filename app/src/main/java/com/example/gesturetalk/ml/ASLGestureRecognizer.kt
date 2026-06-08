package com.example.gesturetalk.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.gesturetalk.mediapipe.HandDetector
import com.example.gesturetalk.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ai.onnxruntime.*
import java.nio.FloatBuffer
import java.util.*
import kotlin.math.exp

/**
 * Распознаватель ASL жестов на базе TGCN моделей
 * Использует MediaPipe landmarks вместо видео кадров
 */
class ASLGestureRecognizer(
    private val context: Context,
    private val modelName: String,
    private val onLog: ((String) -> Unit)? = null
) {
    private val handDetector = HandDetector(context, onLog)
    private var ortSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    private var classLabels: List<String> = emptyList()
    
    // Параметры модели
    private val numKeypoints = 55  // TGCN ожидает 55 точек (full body)
    private val numSamples = 50    // Количество временных сэмплов
    private val windowSize = numSamples
    
    // Буфер landmarks
    private val landmarksBuffer = LinkedList<FloatArray>()
    
    // Последний результат детекции (для визуализации)
    private var lastDetectionResult: HandDetector.HandDetectionResult? = null
    
    // Детекция движения
    private var lastLandmarks: FloatArray? = null
    private var lastMovementTime = 0L
    private val movementThreshold = 0.01f  // Порог движения (1% от размера кадра)
    private val noMovementTimeout = 500L   // 500ms без движения = жест закончился
    private val minFramesForGesture = 0    // Убрали минимум - распознаем как только движение остановилось
    
    // Стабилизация результатов
    private val resultHistory = LinkedList<Pair<String, Float>>()
    private val historySize = 1  // Для movement-based детекции не нужна история (каждый жест = 1 распознавание)
    private var lastRecognizedGesture: String? = null
    private var lastRecognitionTime = 0L
    private val cooldownMs = 2000L  // Увеличен cooldown между жестами
    
    /**
     * Инициализация
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(Constants.TAG, "ASLGestureRecognizer: Инициализация $modelName...")
            onLog?.invoke("=== ASL RECOGNIZER ===")
            onLog?.invoke("Модель: $modelName")
            
            // 1. Инициализируем MediaPipe Hand
            val mpHandSuccess = handDetector.initialize()
            if (!mpHandSuccess) {
                onLog?.invoke("❌ MediaPipe Hand не загружен")
                return@withContext false
            }
            
            // 2. Инициализируем MediaPipe Pose для full body landmarks
            val mpPoseSuccess = handDetector.initializePose()
            if (!mpPoseSuccess) {
                onLog?.invoke("⚠️ MediaPipe Pose не загружен (будем использовать только руки)")
            }
            
            // 3. Копируем модель и .data файл в internal storage
            val modelPath = prepareASLModel()
            if (modelPath == null) {
                onLog?.invoke("❌ Не удалось подготовить модель")
                return@withContext false
            }
            
            // 4. Загружаем ONNX модель из файла
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(4)
            sessionOptions.setInterOpNumThreads(4)
            
            ortSession = ortEnv!!.createSession(modelPath, sessionOptions)
            onLog?.invoke("✓ ONNX модель загружена")
            
            // 5. Загружаем метки классов
            val classesFileName = "${modelName.replace(".onnx", "")}_classes.json"
            val classesJson = context.assets.open(classesFileName).bufferedReader().use { it.readText() }
            classLabels = Gson().fromJson(classesJson, object : TypeToken<List<String>>() {}.type)
            onLog?.invoke("✓ Классов: ${classLabels.size}")
            
            onLog?.invoke("✓ ASL Recognizer готов")
            onLog?.invoke("")
            
            true
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "ASLGestureRecognizer: Ошибка инициализации", e)
            onLog?.invoke("❌ Ошибка: ${e.message}")
            false
        }
    }
    
    /**
     * Подготовка ASL модели (копирование .onnx и .onnx.data в internal storage)
     */
    private suspend fun prepareASLModel(): String? = withContext(Dispatchers.IO) {
        try {
            val modelDir = java.io.File(context.filesDir, "models")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            
            val modelFile = java.io.File(modelDir, modelName)
            val dataFileName = "$modelName.data"
            val dataFile = java.io.File(modelDir, dataFileName)
            
            // Копируем .onnx файл
            if (!modelFile.exists()) {
                val modelResourceName = modelName.replace(".onnx", "").replace("-", "")
                val resourceId = context.resources.getIdentifier(modelResourceName, "raw", context.packageName)
                
                if (resourceId == 0) {
                    Log.e(Constants.TAG, "ASLGestureRecognizer: Model resource not found: $modelResourceName")
                    onLog?.invoke("❌ Ресурс модели не найден: $modelResourceName")
                    return@withContext null
                }
                
                onLog?.invoke("Копирование $modelName...")
                context.resources.openRawResource(resourceId).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                onLog?.invoke("✓ $modelName скопирован")
            }
            
            // Копируем .onnx.data файл (в res/raw он называется aslXXX_onnx_data)
            if (!dataFile.exists()) {
                val dataResourceName = modelName.replace(".onnx", "").replace("-", "") + "_onnx_data"
                val dataResourceId = context.resources.getIdentifier(dataResourceName, "raw", context.packageName)
                
                if (dataResourceId == 0) {
                    Log.e(Constants.TAG, "ASLGestureRecognizer: Data resource not found: $dataResourceName")
                    onLog?.invoke("❌ Ресурс .data не найден: $dataResourceName")
                    return@withContext null
                }
                
                onLog?.invoke("Копирование $dataFileName...")
                context.resources.openRawResource(dataResourceId).use { input ->
                    dataFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                onLog?.invoke("✓ $dataFileName скопирован")
            }
            
            modelFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "ASLGestureRecognizer: Ошибка prepareASLModel", e)
            onLog?.invoke("❌ Ошибка подготовки модели: ${e.message}")
            null
        }
    }
    
    /**
     * Добавление кадра
     */
    fun addFrame(bitmap: Bitmap) {
        try {
            // Детектируем руки И тело для получения 55 keypoints
            val detection = handDetector.detectHandsAndPose(bitmap)
            lastDetectionResult = detection // Сохраняем для визуализации
            
            // КРИТИЧНО: Для ASL моделей нужны И руки И тело!
            // Если нет хотя бы одного - пропускаем кадр
            if (!detection.hasHands) {
                // Нет рук - пропускаем кадр полностью
                if (landmarksBuffer.isEmpty()) {
                    onLog?.invoke("⚠️ Кадр пропущен: Руки НЕ обнаружены")
                }
                return
            }
            
            if (detection.poseLandmarks == null) {
                // Нет pose - пропускаем кадр
                if (landmarksBuffer.isEmpty()) {
                    onLog?.invoke("⚠️ Кадр пропущен: Pose НЕ обнаружен")
                }
                return
            }
            
            // Продолжаем обработку...
            processLandmarks(detection)
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "ASLGestureRecognizer: Ошибка addFrame", e)
            onLog?.invoke("❌ Ошибка addFrame: ${e.message}")
        }
    }
    
    /**
     * Только детекция landmarks без добавления в модель (для плавной визуализации)
     */
    fun detectOnly(bitmap: Bitmap) {
        try {
            val detection = handDetector.detectHandsAndPose(bitmap)
            lastDetectionResult = detection
        } catch (e: Exception) {
            Log.e(Constants.TAG, "ASLGestureRecognizer: Ошибка detectOnly", e)
        }
    }
    
    /**
     * Обработка landmarks (выделено в отдельный метод)
     */
    private fun processLandmarks(detection: HandDetector.HandDetectionResult) {
        // Есть И руки И тело - извлекаем landmarks
        val landmarks = extractLandmarksFrom55Points(detection)
            
            // Вычисляем движение относительно предыдущего кадра
            val currentTime = System.currentTimeMillis()
            if (lastLandmarks != null) {
                val movement = calculateMovement(lastLandmarks!!, landmarks)
                
                if (movement > movementThreshold) {
                    // Есть движение - обновляем время последнего движения
                    lastMovementTime = currentTime
                    
                    // Логируем движение (редко)
                    if (landmarksBuffer.size % 10 == 0) {
                        onLog?.invoke("Движение: ${"%.4f".format(movement)} (порог: $movementThreshold)")
                    }
                }
            } else {
                // Первый кадр - считаем что есть движение
                lastMovementTime = currentTime
            }
            
            // Сохраняем текущие landmarks для следующего сравнения
            lastLandmarks = landmarks.copyOf()
            
            // Добавляем кадр в буфер
            landmarksBuffer.add(landmarks)
            
            // Логируем первый ВАЛИДНЫЙ кадр
            if (landmarksBuffer.size == 1) {
                Log.d(Constants.TAG, "ASLGestureRecognizer: Первый валидный кадр")
                Log.d(Constants.TAG, "  Hand landmarks: ${detection.handLandmarks?.size ?: 0} рук")
                Log.d(Constants.TAG, "  Pose landmarks: есть")
                Log.d(Constants.TAG, "  Landmarks (первые 10): ${landmarks.take(10).joinToString(", ") { "%.3f".format(it) }}")
                
                onLog?.invoke("✓ Кадр #1: Полные данные")
                onLog?.invoke("  Рук: ${detection.handLandmarks?.size ?: 0}")
                onLog?.invoke("  Pose: ✓")
                
                // Детальная информация о landmarks
                val nonZeroCount = landmarks.count { it != 0f }
                onLog?.invoke("  Ненулевых координат: $nonZeroCount/${landmarks.size}")
                
                onLog?.invoke("=== ДЕТАЛИ LANDMARKS ===")
                // Показываем первые значения pose
                onLog?.invoke("  Pose точка 0 (нос): (${landmarks[0]}, ${landmarks[1]})")
                onLog?.invoke("  Pose точка 11 (л.плечо): (${landmarks[22]}, ${landmarks[23]})")
                // Показываем первые значения hand
                onLog?.invoke("  Hand точка 0 (запястье): (${landmarks[66]}, ${landmarks[67]})")
                onLog?.invoke("  Hand точка 8 (указ.палец): (${landmarks[82]}, ${landmarks[83]})")
                onLog?.invoke("========================")
            }
            
            // Логируем каждый 10-й кадр
            if (landmarksBuffer.size % 10 == 0) {
                val nonZeroCount = landmarks.count { it != 0f }
                val poseOk = detection.poseLandmarks != null
                val handsCount = detection.handLandmarks?.size ?: 0
                onLog?.invoke("Кадр #${landmarksBuffer.size}: ${if (poseOk) "✓pose" else "✗pose"} + ${handsCount}hands, ненулевых: $nonZeroCount/${landmarks.size}")
            }
            
            // Ограничиваем размер буфера
            while (landmarksBuffer.size > windowSize) {
                landmarksBuffer.removeFirst()
            }
    }
    
    /**
     * Вычисление движения между двумя кадрами
     * Возвращает среднее расстояние между соответствующими точками
     */
    private fun calculateMovement(prev: FloatArray, current: FloatArray): Float {
        if (prev.size != current.size) {
            return 0f
        }
        
        var totalDistance = 0f
        var pointCount = 0
        
        // Вычисляем расстояние для каждой точки (x, y пары)
        for (i in 0 until prev.size step 2) {
            val dx = current[i] - prev[i]
            val dy = current[i + 1] - prev[i + 1]
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            
            totalDistance += distance
            pointCount++
        }
        
        return if (pointCount > 0) totalDistance / pointCount else 0f
    }
    
    /**
     * Проверка завершения жеста (движение остановилось)
     */
    fun isGestureComplete(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastMovement = currentTime - lastMovementTime
        
        // Жест завершен если:
        // 1. Есть хотя бы 1 кадр (minFramesForGesture = 0, но нужен хотя бы 1 кадр)
        // 2. Движение остановилось на noMovementTimeout миллисекунд (500ms)
        val hasFrames = landmarksBuffer.size > 0
        val movementStopped = timeSinceLastMovement >= noMovementTimeout
        
        // Улучшенное логирование
        if (landmarksBuffer.size > 0) {
            val timeLeft = noMovementTimeout - timeSinceLastMovement
            
            if (!movementStopped) {
                // Движение еще не остановилось
                if (landmarksBuffer.size % 5 == 0 && timeLeft > 0) {
                    onLog?.invoke("Жду завершения жеста (${landmarksBuffer.size} кадров, осталось ${timeLeft}ms)")
                }
            } else {
                // Жест завершен!
                onLog?.invoke("✓ Жест завершен: ${landmarksBuffer.size} кадров собрано")
            }
        }
        
        return hasFrames && movementStopped
    }
    
    /**
     * Дополнение буфера до windowSize повторением последнего кадра
     */
    fun padBufferToWindowSize() {
        if (landmarksBuffer.isEmpty()) {
            return
        }
        
        val lastFrame = landmarksBuffer.last()
        val framesToAdd = windowSize - landmarksBuffer.size
        
        if (framesToAdd > 0) {
            onLog?.invoke("Дополняю буфер: ${landmarksBuffer.size} -> $windowSize (повтор последнего кадра)")
            
            repeat(framesToAdd) {
                landmarksBuffer.add(lastFrame.copyOf())
            }
        }
    }
    
    /**
     * Очистка буфера после распознавания
     */
    fun clearBuffer() {
        landmarksBuffer.clear()
        lastLandmarks = null
        lastMovementTime = 0L
        onLog?.invoke("Буфер очищен, готов к новому жесту")
    }
    
    /**
     * Извлечение 55 keypoints из MediaPipe результата
     * 
     * WLASL TGCN использует 55 точек:
     * - 33 точки тела (MediaPipe Pose)
     * - 21 точка правой руки (MediaPipe Hand)
     * - 1 дополнительная точка (центр тела)
     * 
     * Если pose недоступен - заполняем нулями
     */
    private fun extractLandmarksFrom55Points(detection: HandDetector.HandDetectionResult): FloatArray {
        val result = FloatArray(numKeypoints * 2) { 0f }
        
        var logMessage = "Извлечение landmarks: "
        var detailedLog = ""
        
        // 1. Заполняем pose landmarks (33 точки = 0-32)
        val poseLandmarks = detection.poseLandmarks
        if (poseLandmarks != null && poseLandmarks.size >= 33 * 2) {
            // Копируем 33 точки тела (x, y)
            System.arraycopy(poseLandmarks, 0, result, 0, 33 * 2)
            logMessage += "pose=33 "
            
            // Проверяем сколько ненулевых точек
            val nonZeroPose = poseLandmarks.count { it != 0f }
            detailedLog += "  Pose: 33 точки, ненулевых координат: $nonZeroPose/66\n"
            
            // Показываем первые 3 точки тела (нос, левый глаз, правый глаз)
            detailedLog += "    Точка 0 (нос): (${poseLandmarks[0]}, ${poseLandmarks[1]})\n"
            detailedLog += "    Точка 1 (л.глаз): (${poseLandmarks[2]}, ${poseLandmarks[3]})\n"
            detailedLog += "    Точка 2 (п.глаз): (${poseLandmarks[4]}, ${poseLandmarks[5]})\n"
        } else {
            // Если pose нет - заполняем нулями (первые 33 точки)
            logMessage += "pose=0 "
            detailedLog += "  Pose: НЕТ (заполнено нулями)\n"
            Log.w(Constants.TAG, "ASLGestureRecognizer: Pose landmarks недоступны, заполняем нулями")
        }
        
        // 2. Заполняем hand landmarks (21 точка = 33-53)
        val handLandmarks = detection.handLandmarks
        if (handLandmarks != null && handLandmarks.isNotEmpty()) {
            // Берем первую руку (обычно правая или доминантная)
            val hand = handLandmarks[0]
            if (hand.size >= 21 * 2) {
                // Копируем 21 точку руки начиная с индекса 33
                System.arraycopy(hand, 0, result, 33 * 2, 21 * 2)
                logMessage += "hand=21 "
                
                val nonZeroHand = hand.count { it != 0f }
                detailedLog += "  Hand: 21 точка, ненулевых координат: $nonZeroHand/42\n"
                
                // Показываем ключевые точки руки (запястье, указательный, большой)
                detailedLog += "    Точка 0 (запястье): (${hand[0]}, ${hand[1]})\n"
                detailedLog += "    Точка 8 (указ.палец): (${hand[16]}, ${hand[17]})\n"
                detailedLog += "    Точка 4 (больш.палец): (${hand[8]}, ${hand[9]})\n"
            }
        } else {
            logMessage += "hand=0 "
            detailedLog += "  Hand: НЕТ (заполнено нулями)\n"
            Log.w(Constants.TAG, "ASLGestureRecognizer: Hand landmarks недоступны")
        }
        
        // 3. Последняя точка (54) - центр тела (среднее между плечами)
        // Если есть pose - вычисляем, иначе оставляем 0
        if (poseLandmarks != null && poseLandmarks.size >= 24) {
            // Точки 11 и 12 - левое и правое плечо
            val leftShoulderX = poseLandmarks[11 * 2]
            val leftShoulderY = poseLandmarks[11 * 2 + 1]
            val rightShoulderX = poseLandmarks[12 * 2]
            val rightShoulderY = poseLandmarks[12 * 2 + 1]
            
            result[54 * 2] = (leftShoulderX + rightShoulderX) / 2f
            result[54 * 2 + 1] = (leftShoulderY + rightShoulderY) / 2f
            logMessage += "center=1"
            detailedLog += "  Center: (${result[54 * 2]}, ${result[54 * 2 + 1]})\n"
        } else {
            logMessage += "center=0"
            detailedLog += "  Center: НЕТ (заполнено нулями)\n"
        }
        
        // Логируем только первый раз
        if (landmarksBuffer.isEmpty()) {
            Log.d(Constants.TAG, "ASLGestureRecognizer: $logMessage")
            onLog?.invoke("=== ДЕТАЛИ LANDMARKS ===")
            onLog?.invoke(detailedLog.trim())
            onLog?.invoke("========================")
        }
        
        // НОРМАЛИЗАЦИЯ как в обучении WLASL TGCN:
        // x = 2 * ((x / 256.0) - 0.5)  =>  x = 2 * (x - 0.5)  (т.к. MediaPipe уже в [0,1])
        // Это дает диапазон [-1, 1]
        for (i in 0 until result.size) {
            result[i] = 2f * (result[i] - 0.5f)
        }
        
        if (landmarksBuffer.isEmpty()) {
            onLog?.invoke("✓ Landmarks нормализованы в диапазон [-1, 1] (как в обучении)")
        }
        
        return result
    }
    
    /**
     * Распознавание жеста
     */
    suspend fun recognize(): RecognitionResult? = withContext(Dispatchers.IO) {
        try {
            // Проверяем cooldown
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRecognitionTime < cooldownMs) {
                onLog?.invoke("⏸ Cooldown активен")
                return@withContext null
            }
            
            // Проверяем что буфер заполнен
            if (landmarksBuffer.size < windowSize) {
                onLog?.invoke("⏸ Буфер не заполнен: ${landmarksBuffer.size}/$windowSize")
                return@withContext null
            }
            
            val startTime = System.currentTimeMillis()
            
            // Подготавливаем input tensor
            val inputData = prepareInputTensor()
            onLog?.invoke("✓ Input tensor подготовлен: [1, $numKeypoints, ${numSamples * 2}]")
            
            // Запускаем inference
            val inputName = ortSession!!.inputNames.iterator().next()
            onLog?.invoke("Input name: $inputName")
            
            val inputTensor = OnnxTensor.createTensor(ortEnv!!, inputData)
            val inferenceStart = System.currentTimeMillis()
            val outputs = ortSession!!.run(mapOf(inputName to inputTensor))
            val inferenceTime = System.currentTimeMillis() - inferenceStart
            
            onLog?.invoke("✓ Inference завершен за ${inferenceTime}ms")
            
            // Получаем результат
            val output = outputs[0].value as Array<FloatArray>
            val logits = output[0]
            
            onLog?.invoke("Output shape: [${output.size}, ${logits.size}]")
            onLog?.invoke("Logits (первые 5): ${logits.take(5).joinToString(", ") { "%.2f".format(it) }}")
            
            // Применяем softmax для получения вероятностей
            val probabilities = softmax(logits)
            
            onLog?.invoke("Probabilities (первые 5): ${probabilities.take(5).joinToString(", ") { "%.4f".format(it) }}")
            
            // Находим лучший класс
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            val bestClass = classLabels[maxIndex]
            val bestScore = probabilities[maxIndex]
            
            onLog?.invoke("Лучший класс: $bestClass (индекс $maxIndex, вероятность ${bestScore * 100}%)")
            
            // Очищаем ресурсы
            inputTensor.close()
            outputs.close()
            
            // Обновляем историю
            resultHistory.add(Pair(bestClass, bestScore))
            if (resultHistory.size > historySize) {
                resultHistory.removeFirst()
            }
            
            lastRecognitionTime = currentTime
            
            val totalTime = System.currentTimeMillis() - startTime
            
            RecognitionResult(
                word = bestClass,
                confidence = bestScore,
                inferenceTimeMs = totalTime
            )
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "ASLGestureRecognizer: Ошибка recognize", e)
            onLog?.invoke("❌ ОШИБКА recognize: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Применение softmax для преобразования logits в вероятности
     */
    private fun softmax(logits: FloatArray): FloatArray {
        // Находим максимум для численной стабильности
        val maxLogit = logits.maxOrNull() ?: 0f
        
        // Вычисляем exp(x - max) для каждого элемента
        val exps = FloatArray(logits.size) { i ->
            exp((logits[i] - maxLogit).toDouble()).toFloat()
        }
        
        // Сумма всех exp
        val sumExps = exps.sum()
        
        // Нормализуем
        return FloatArray(logits.size) { i ->
            exps[i] / sumExps
        }
    }
    
    /**
     * Подготовка input tensor для ONNX
     * Формат: (batch=1, num_keypoints=55, num_samples*2=100)
     */
    private fun prepareInputTensor(): Array<Array<FloatArray>> {
        val batch = Array(1) { Array(numKeypoints) { FloatArray(numSamples * 2) } }
        
        // Заполняем данными из буфера
        for (keypointIdx in 0 until numKeypoints) {
            for (sampleIdx in 0 until numSamples) {
                if (sampleIdx < landmarksBuffer.size) {
                    val landmarks = landmarksBuffer[sampleIdx]
                    batch[0][keypointIdx][sampleIdx * 2] = landmarks[keypointIdx * 2]      // x
                    batch[0][keypointIdx][sampleIdx * 2 + 1] = landmarks[keypointIdx * 2 + 1]  // y
                } else {
                    batch[0][keypointIdx][sampleIdx * 2] = 0f
                    batch[0][keypointIdx][sampleIdx * 2 + 1] = 0f
                }
            }
        }
        
        // Логируем статистику первого тензора
        val allValues = batch[0].flatMap { it.toList() }
        val nonZeroCount = allValues.count { it != 0f }
        val minVal = allValues.minOrNull() ?: 0f
        val maxVal = allValues.maxOrNull() ?: 0f
        val avgVal = if (nonZeroCount > 0) allValues.filter { it != 0f }.average().toFloat() else 0f
        
        onLog?.invoke("Тензор статистика:")
        onLog?.invoke("  Ненулевых: $nonZeroCount/${allValues.size}")
        onLog?.invoke("  Диапазон: [${"%.3f".format(minVal)}, ${"%.3f".format(maxVal)}]")
        onLog?.invoke("  Среднее (ненулевых): ${"%.3f".format(avgVal)}")
        
        // Показываем первые значения первой точки (нос)
        val firstKeypoint = batch[0][0]
        onLog?.invoke("  Точка 0 (первые 10 значений): ${firstKeypoint.take(10).joinToString(", ") { "%.3f".format(it) }}")
        
        return batch
    }
    
    /**
     * Обработка результата с фильтрацией
     */
    fun processResult(result: RecognitionResult?): String? {
        if (result == null) {
            onLog?.invoke("  ✗ Результат null")
            return null
        }
        
        // Проверяем минимальную уверенность (20% для тестирования)
        if (result.confidence < 0.2f) {
            onLog?.invoke("  ✗ Низкая уверенность: ${(result.confidence * 100).toInt()}% < 20%")
            return null
        }
        
        // Для movement-based детекции не нужна история
        // Каждый жест распознается 1 раз после остановки движения
        
        // Проверяем что это не повтор (cooldown)
        if (result.word == lastRecognizedGesture) {
            onLog?.invoke("  ✗ Повтор предыдущего жеста (cooldown активен)")
            return null
        }
        
        lastRecognizedGesture = result.word
        onLog?.invoke("  ✓ Новый жест принят: ${result.word} (${(result.confidence * 100).toInt()}%)")
        return result.word
    }
    
    /**
     * Получить размер окна
     */
    fun getWindowSize(): Int = windowSize
    
    /**
     * Получить текущее количество кадров
     */
    fun getCurrentFrameCount(): Int = landmarksBuffer.size
    
    /**
     * Получить последний результат детекции (для визуализации)
     */
    fun getLastDetectionResult(): HandDetector.HandDetectionResult? = lastDetectionResult
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        try {
            ortSession?.close()
            ortEnv?.close()
            handDetector.release()
            landmarksBuffer.clear()
            resultHistory.clear()
            Log.d(Constants.TAG, "ASLGestureRecognizer: released")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "ASLGestureRecognizer: Ошибка release", e)
        }
    }
}

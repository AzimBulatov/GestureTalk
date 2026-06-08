package com.example.gesturetalk.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.example.gesturetalk.utils.Constants
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Детектор рук и тела на базе MediaPipe
 * Быстро определяет наличие рук и их положение в кадре
 * Также извлекает full body pose для ASL моделей
 */
class HandDetector(
    private val context: Context,
    private val onLog: ((String) -> Unit)? = null
) {
    private var handLandmarker: HandLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null
    private var isInitialized = false
    private var isPoseInitialized = false
    
    /**
     * Результат детекции
     */
    data class HandDetectionResult(
        val hasHands: Boolean,
        val handsCount: Int,
        val boundingBox: RectF?,
        val isInGestureZone: Boolean,
        val confidence: Float,
        val handLandmarks: List<FloatArray>? = null,  // 21 точек руки
        val poseLandmarks: FloatArray? = null,         // 33 точки тела
        val centerPoint: Pair<Float, Float>? = null    // Центр тела (для визуализации)
    )
    
    /**
     * Инициализация MediaPipe Hand Landmarker
     */
    fun initialize(): Boolean {
        return try {
            Log.d(Constants.TAG, "HandDetector: Инициализация MediaPipe Hand...")
            onLog?.invoke("MediaPipe Hand: инициализация...")
            
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.45f)
                .setMinHandPresenceConfidence(0.42f)
                .setMinTrackingConfidence(0.45f)
                .build()
            
            handLandmarker = HandLandmarker.createFromOptions(context, options)
            isInitialized = true
            
            Log.d(Constants.TAG, "HandDetector: ✓ MediaPipe Hand инициализирован")
            onLog?.invoke("✓ MediaPipe Hand готов")
            true
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HandDetector: Ошибка инициализации MediaPipe Hand", e)
            onLog?.invoke("⚠️ MediaPipe Hand недоступен: ${e.message}")
            isInitialized = false
            false
        }
    }
    
    /**
     * Инициализация MediaPipe Pose Landmarker для ASL моделей
     */
    fun initializePose(): Boolean {
        return try {
            Log.d(Constants.TAG, "HandDetector: Инициализация MediaPipe Pose...")
            onLog?.invoke("MediaPipe Pose: инициализация...")
            
            // Пробуем загрузить pose_landmarker.task из assets
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()
            
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            isPoseInitialized = true
            
            Log.d(Constants.TAG, "HandDetector: ✓ MediaPipe Pose инициализирован")
            onLog?.invoke("✓ MediaPipe Pose готов")
            true
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HandDetector: Ошибка инициализации MediaPipe Pose", e)
            onLog?.invoke("⚠️ MediaPipe Pose недоступен: ${e.message}")
            isPoseInitialized = false
            false
        }
    }
    
    /**
     * Детекция рук в кадре
     */
    fun detectHands(bitmap: Bitmap): HandDetectionResult {
        if (!isInitialized || handLandmarker == null) {
            return HandDetectionResult(
                hasHands = false,
                handsCount = 0,
                boundingBox = null,
                isInGestureZone = false,
                confidence = 0f
            )
        }
        
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = handLandmarker!!.detect(mpImage)
            val base = processResult(result, bitmap.width, bitmap.height)
            if (!base.hasHands) {
                return base
            }
            base.copy(handLandmarks = extractHandLandmarksList(result))
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HandDetector: Ошибка детекции", e)
            HandDetectionResult(
                hasHands = false,
                handsCount = 0,
                boundingBox = null,
                isInGestureZone = false,
                confidence = 0f
            )
        }
    }
    
    /**
     * Детекция рук И тела для ASL моделей (55 keypoints)
     */
    fun detectHandsAndPose(bitmap: Bitmap): HandDetectionResult {
        if (!isInitialized || handLandmarker == null) {
            return HandDetectionResult(
                hasHands = false,
                handsCount = 0,
                boundingBox = null,
                isInGestureZone = false,
                confidence = 0f
            )
        }
        
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            // Детекция рук
            val handResult = handLandmarker!!.detect(mpImage)
            
            // Детекция pose (если инициализирован)
            val poseResult = if (isPoseInitialized && poseLandmarker != null) {
                try {
                    poseLandmarker!!.detect(mpImage)
                } catch (e: Exception) {
                    Log.w(Constants.TAG, "HandDetector: Ошибка pose detection", e)
                    null
                }
            } else {
                null
            }
            
            processResultWithPose(handResult, poseResult, bitmap.width, bitmap.height)
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HandDetector: Ошибка детекции", e)
            HandDetectionResult(
                hasHands = false,
                handsCount = 0,
                boundingBox = null,
                isInGestureZone = false,
                confidence = 0f
            )
        }
    }
    
    private fun extractHandLandmarksList(handResult: HandLandmarkerResult): List<FloatArray> {
        val out = mutableListOf<FloatArray>()
        for (hand in handResult.landmarks()) {
            val landmarks = FloatArray(21 * 2)
            for (i in 0 until minOf(21, hand.size)) {
                landmarks[i * 2] = hand[i].x()
                landmarks[i * 2 + 1] = hand[i].y()
            }
            out.add(landmarks)
        }
        return out
    }
    
    /**
     * Обработка результата MediaPipe с Pose
     */
    private fun processResultWithPose(
        handResult: HandLandmarkerResult,
        poseResult: PoseLandmarkerResult?,
        width: Int,
        height: Int
    ): HandDetectionResult {
        val handLandmarks = handResult.landmarks()
        
        if (handLandmarks.isEmpty()) {
            return HandDetectionResult(
                hasHands = false,
                handsCount = 0,
                boundingBox = null,
                isInGestureZone = false,
                confidence = 0f
            )
        }
        
        val handLandmarksList = extractHandLandmarksList(handResult)
        
        // Извлекаем pose landmarks (33 точки тела)
        val poseLandmarksArray = if (poseResult != null && poseResult.landmarks().isNotEmpty()) {
            val poseLandmarks = poseResult.landmarks()[0]
            FloatArray(33 * 2).apply {
                for (i in 0 until minOf(33, poseLandmarks.size)) {
                    this[i * 2] = poseLandmarks[i].x()
                    this[i * 2 + 1] = poseLandmarks[i].y()
                }
            }
        } else {
            null
        }
        
        // Вычисляем центральную точку (среднее между плечами)
        val centerPoint = if (poseLandmarksArray != null && poseLandmarksArray.size >= 24) {
            val leftShoulderX = poseLandmarksArray[11 * 2]
            val leftShoulderY = poseLandmarksArray[11 * 2 + 1]
            val rightShoulderX = poseLandmarksArray[12 * 2]
            val rightShoulderY = poseLandmarksArray[12 * 2 + 1]
            
            Pair(
                (leftShoulderX + rightShoulderX) / 2f,
                (leftShoulderY + rightShoulderY) / 2f
            )
        } else {
            null
        }
        
        // Вычисляем bounding box и остальное как раньше
        val result = processResult(handResult, width, height)
        
        return result.copy(
            handLandmarks = handLandmarksList,
            poseLandmarks = poseLandmarksArray,
            centerPoint = centerPoint
        )
    }
    
    /**
     * Обработка результата MediaPipe
     */
    private fun processResult(
        result: HandLandmarkerResult,
        width: Int,
        height: Int
    ): HandDetectionResult {
        val landmarks = result.landmarks()
        
        if (landmarks.isEmpty()) {
            return HandDetectionResult(
                hasHands = false,
                handsCount = 0,
                boundingBox = null,
                isInGestureZone = false,
                confidence = 0f
            )
        }
        
        // Вычисляем bounding box вокруг всех рук
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        for (handLandmarks in landmarks) {
            for (landmark in handLandmarks) {
                val x = landmark.x() * width
                val y = landmark.y() * height
                
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        
        // Добавляем padding (20% от размера руки)
        val handWidth = maxX - minX
        val handHeight = maxY - minY
        val padding = maxOf(handWidth, handHeight) * 0.2f
        
        minX = maxOf(0f, minX - padding)
        minY = maxOf(0f, minY - padding)
        maxX = minOf(width.toFloat(), maxX + padding)
        maxY = minOf(height.toFloat(), maxY + padding)
        
        val boundingBox = RectF(minX, minY, maxX, maxY)
        
        // Зона жеста Slovo: верхняя часть кадра (не слишком строго — на телефоне руки часто ниже центра)
        val centerY = (minY + maxY) / 2
        val isInGestureZone = centerY < height * 0.78f
        
        // Средняя уверенность (если есть handedness scores)
        val confidence = if (result.handednesses().isNotEmpty()) {
            result.handednesses().flatten().map { it.score() }.average().toFloat()
        } else {
            0.8f
        }
        
        return HandDetectionResult(
            hasHands = true,
            handsCount = landmarks.size,
            boundingBox = boundingBox,
            isInGestureZone = isInGestureZone,
            confidence = confidence
        )
    }
    
    /**
     * Обрезка кадра по bounding box рук
     */
    fun cropToHands(bitmap: Bitmap, bbox: RectF): Bitmap {
        return try {
            // Проверяем границы
            val left = bbox.left.toInt().coerceIn(0, bitmap.width - 1)
            val top = bbox.top.toInt().coerceIn(0, bitmap.height - 1)
            val width = (bbox.width().toInt()).coerceIn(1, bitmap.width - left)
            val height = (bbox.height().toInt()).coerceIn(1, bitmap.height - top)
            
            Bitmap.createBitmap(bitmap, left, top, width, height)
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HandDetector: Ошибка crop", e)
            bitmap
        }
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        try {
            handLandmarker?.close()
            handLandmarker = null
            isInitialized = false
            
            poseLandmarker?.close()
            poseLandmarker = null
            isPoseInitialized = false
            
            Log.d(Constants.TAG, "HandDetector: released")
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HandDetector: Ошибка release", e)
        }
    }
}

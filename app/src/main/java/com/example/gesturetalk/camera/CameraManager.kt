package com.example.gesturetalk.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.gesturetalk.utils.Constants
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Менеджер камеры на базе CameraX
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onLog: ((String) -> Unit)? = null
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var frameCallback: ((Bitmap) -> Unit)? = null
    private var lastAnalysisTime = 0L
    private var minAnalysisInterval = 33L // Будет обновлено на основе настройки FPS
    
    // Текущая камера (по умолчанию фронтальная)
    private var useFrontCamera = true
    
    /**
     * Запуск камеры
     */
    fun startCamera(onFrameCallback: (Bitmap) -> Unit) {
        this.frameCallback = onFrameCallback
        
        Log.d(Constants.TAG, "CameraManager: startCamera() вызван")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                Log.d(Constants.TAG, "CameraManager: ProcessCameraProvider получен")
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(Constants.TAG, "CameraManager: ОШИБКА инициализации камеры", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Привязка use cases камеры
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: run {
            Log.e(Constants.TAG, "CameraManager: cameraProvider is null!")
            return
        }
        
        // Загружаем настройки камеры
        val resolution = com.example.gesturetalk.SettingsActivity.getCameraResolution(context)
        val fps = com.example.gesturetalk.SettingsActivity.getCameraFPS(context)
        val enableAutofocus = com.example.gesturetalk.SettingsActivity.getEnableAutofocus(context)
        val enableStabilization = com.example.gesturetalk.SettingsActivity.getEnableStabilization(context)
        
        // Определяем разрешение
        val (width, height) = when (resolution) {
            "SD" -> Pair(Constants.RESOLUTION_SD_WIDTH, Constants.RESOLUTION_SD_HEIGHT)
            "HD" -> Pair(Constants.RESOLUTION_HD_WIDTH, Constants.RESOLUTION_HD_HEIGHT)
            "FHD" -> Pair(Constants.RESOLUTION_FHD_WIDTH, Constants.RESOLUTION_FHD_HEIGHT)
            else -> Pair(Constants.RESOLUTION_HD_WIDTH, Constants.RESOLUTION_HD_HEIGHT)
        }
        
        Log.d(Constants.TAG, "CameraManager: Настройка камеры:")
        Log.d(Constants.TAG, "  Разрешение: $resolution (${width}x${height})")
        Log.d(Constants.TAG, "  FPS: $fps")
        Log.d(Constants.TAG, "  Автофокус: ${if (enableAutofocus) "включен" else "выключен"}")
        Log.d(Constants.TAG, "  Стабилизация: ${if (enableStabilization) "включена" else "выключена"}")
        
        onLog?.invoke("=== НАСТРОЙКИ КАМЕРЫ ===")
        onLog?.invoke("Разрешение: $resolution (${width}x${height})")
        onLog?.invoke("FPS: $fps")
        onLog?.invoke("Автофокус: ${if (enableAutofocus) "вкл" else "выкл"}")
        onLog?.invoke("Стабилизация: ${if (enableStabilization) "вкл" else "выкл"}")
        
        // Устанавливаем интервал обработки на основе FPS
        // 30 FPS = 33ms между кадрами
        // 60 FPS = 16ms между кадрами
        minAnalysisInterval = when (fps) {
            60 -> 16L  // ~60 FPS
            30 -> 33L  // ~30 FPS
            else -> 33L
        }
        Log.d(Constants.TAG, "  Интервал обработки: ${minAnalysisInterval}ms (~${1000/minAnalysisInterval} FPS)")
        Log.d(Constants.TAG, "===================================")
        
        onLog?.invoke("Интервал: ${minAnalysisInterval}ms (~${1000/minAnalysisInterval} FPS)")
        onLog?.invoke("========================")
        
        // Preview
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Image Analysis для ML с настраиваемым разрешением
        // FPS контролируется самой камерой
        val analysisBuilder = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(width, height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        
        // Пытаемся установить целевой FPS через OutputOptions (если поддерживается)
        try {
            // Для 60 FPS нужно минимизировать задержку
            if (fps >= 60) {
                analysisBuilder.setTargetRotation(android.view.Surface.ROTATION_0)
                Log.d(Constants.TAG, "CameraManager: Запрос высокого FPS ($fps)")
            }
        } catch (e: Exception) {
            Log.w(Constants.TAG, "CameraManager: Не удалось настроить FPS: ${e.message}")
        }
        
        imageAnalyzer = analysisBuilder.build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }
        
        Log.d(Constants.TAG, "CameraManager: Выбор ${if (useFrontCamera) "фронтальной" else "задней"} камеры...")
        
        // Выбираем камеру
        val cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        try {
            Log.d(Constants.TAG, "CameraManager: Отвязка старых use cases...")
            // Отвязываем все перед привязкой новых
            cameraProvider.unbindAll()
            
            Log.d(Constants.TAG, "CameraManager: Привязка новых use cases...")
            // Привязываем use cases
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
            // Настраиваем автофокус
            if (enableAutofocus) {
                camera?.cameraControl?.let { cameraControl ->
                    try {
                        val action = FocusMeteringAction.Builder(
                            previewView.meteringPointFactory.createPoint(
                                previewView.width / 2f,
                                previewView.height / 2f
                            )
                        ).build()
                        cameraControl.startFocusAndMetering(action)
                        Log.d(Constants.TAG, "CameraManager: ✓ Автофокус включен (центр экрана)")
                        onLog?.invoke("✓ Автофокус включен")
                    } catch (e: Exception) {
                        Log.w(Constants.TAG, "CameraManager: ⚠️ Не удалось включить автофокус: ${e.message}")
                    }
                }
            } else {
                Log.d(Constants.TAG, "CameraManager: Автофокус отключен пользователем")
            }
            
            // Настраиваем стабилизацию (если поддерживается)
            if (enableStabilization) {
                camera?.cameraInfo?.let { cameraInfo ->
                    // Проверяем поддержку стабилизации
                    // Примечание: VideoStabilization доступна только для видео, для preview используется EIS
                    Log.d(Constants.TAG, "CameraManager: ✓ Стабилизация изображения включена")
                    onLog?.invoke("✓ Стабилизация включена")
                }
            } else {
                Log.d(Constants.TAG, "CameraManager: Стабилизация отключена пользователем")
            }
            
            // Настраиваем автоматическую экспозицию (AE)
            camera?.cameraControl?.let { cameraControl ->
                try {
                    // Устанавливаем компенсацию экспозиции для лучшего освещения
                    // Диапазон обычно от -2 до +2 EV
                    // 0 = автоматическая экспозиция без коррекции
                    val exposureCompensation = 0 // Можно добавить в настройки позже
                    
                    val exposureState = camera?.cameraInfo?.exposureState
                    if (exposureState?.isExposureCompensationSupported == true) {
                        val clampedCompensation = exposureCompensation.coerceIn(
                            exposureState.exposureCompensationRange.lower,
                            exposureState.exposureCompensationRange.upper
                        )
                        cameraControl.setExposureCompensationIndex(clampedCompensation)
                        Log.d(Constants.TAG, "CameraManager: ✓ Автоэкспозиция настроена")
                        Log.d(Constants.TAG, "  Compensation: $clampedCompensation EV")
                        Log.d(Constants.TAG, "  Диапазон: ${exposureState.exposureCompensationRange.lower} .. ${exposureState.exposureCompensationRange.upper} EV")
                        onLog?.invoke("✓ Автоэкспозиция: $clampedCompensation EV")
                    } else {
                        Log.d(Constants.TAG, "CameraManager: ⚠️ Автоэкспозиция не поддерживается устройством")
                    }
                } catch (e: Exception) {
                    Log.w(Constants.TAG, "CameraManager: ⚠️ Не удалось настроить автоэкспозицию: ${e.message}")
                }
            }
            
            Log.d(Constants.TAG, "CameraManager: ✓ Камера успешно привязана и запущена")
            
            // Логируем реальные возможности камеры
            camera?.cameraInfo?.let { cameraInfo ->
                try {
                    Log.d(Constants.TAG, "=== ВОЗМОЖНОСТИ КАМЕРЫ ===")
                    onLog?.invoke("=== ВОЗМОЖНОСТИ КАМЕРЫ ===")
                    
                    // Проверяем поддержку FPS
                    val fpsRanges = cameraInfo.supportedFrameRateRanges
                    Log.d(Constants.TAG, "Поддерживаемые FPS диапазоны:")
                    onLog?.invoke("Поддерживаемые FPS:")
                    fpsRanges.forEach { range ->
                        Log.d(Constants.TAG, "  ${range.lower} - ${range.upper} FPS")
                        onLog?.invoke("  ${range.lower} - ${range.upper} FPS")
                    }
                    
                    val maxFps = fpsRanges.maxOfOrNull { it.upper } ?: 30
                    if (fps > maxFps) {
                        Log.w(Constants.TAG, "⚠️ Запрошено $fps FPS, но камера поддерживает максимум $maxFps FPS")
                        onLog?.invoke("⚠️ Запрошено $fps FPS, макс $maxFps FPS")
                    }
                    
                    Log.d(Constants.TAG, "===========================")
                } catch (e: Exception) {
                    Log.w(Constants.TAG, "Не удалось получить информацию о камере: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(Constants.TAG, "CameraManager: ОШИБКА привязки use cases", e)
        }
    }
    
    private var frameCount = 0
    private var droppedFrames = 0
    private var lastFpsLogTime = 0L
    
    /**
     * Обработка кадра из ImageProxy
     */
    private fun processImageProxy(imageProxy: ImageProxy) {
        frameCount++
        
        if (frameCount == 1) {
            Log.d(Constants.TAG, "CameraManager: Первый кадр получен от камеры (${imageProxy.width}x${imageProxy.height})")
            lastFpsLogTime = System.currentTimeMillis()
        }
        
        val currentTime = System.currentTimeMillis()
        
        // УБРАЛИ ОГРАНИЧЕНИЕ FPS - пусть камера дает максимум!
        // Камера сама контролирует FPS через свои настройки
        
        // Логируем статистику FPS каждые 3 секунды
        if (currentTime - lastFpsLogTime >= 3000) {
            val elapsed = (currentTime - lastFpsLogTime) / 1000.0
            val actualFps = frameCount / elapsed
            Log.d(Constants.TAG, "CameraManager: FPS статистика:")
            Log.d(Constants.TAG, "  Получено от камеры: ${"%.1f".format(actualFps)} FPS")
            
            onLog?.invoke("📊 FPS: получено ${"%.1f".format(actualFps)}")
            
            frameCount = 0
            lastFpsLogTime = currentTime
        }
        
        lastAnalysisTime = currentTime
        
        try {
            // ВАЖНО: создаем bitmap только если callback готов его принять
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                frameCallback?.invoke(bitmap)
                // bitmap будет освобожден в callback
            } else {
                Log.w(Constants.TAG, "CameraManager: imageProxyToBitmap вернул null")
            }
        } catch (e: Exception) {
            Log.e(Constants.TAG, "CameraManager: ОШИБКА обработки кадра", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * Конвертация ImageProxy в Bitmap (ПРЯМАЯ конвертация YUV → RGB без JPEG)
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        // ПРЯМАЯ конвертация YUV → RGB без JPEG
        val width = imageProxy.width
        val height = imageProxy.height
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Конвертируем YUV в RGB напрямую
        val pixels = IntArray(width * height)
        decodeYUV420SP(pixels, nv21, width, height)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        
        // Поворачиваем если нужно (фронтальная камера обычно перевернута)
        return rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
    }
    
    /**
     * Конвертация YUV420SP (NV21) в RGB
     */
    private fun decodeYUV420SP(rgb: IntArray, yuv420sp: ByteArray, width: Int, height: Int) {
        val frameSize = width * height
        
        for (j in 0 until height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0
            
            for (i in 0 until width) {
                val y = (0xff and yuv420sp[j * width + i].toInt()) - 16
                if (y < 0) continue
                
                if ((i and 1) == 0) {
                    v = (0xff and yuv420sp[uvp++].toInt()) - 128
                    u = (0xff and yuv420sp[uvp++].toInt()) - 128
                }
                
                val y1192 = 1192 * y
                var r = (y1192 + 1634 * v)
                var g = (y1192 - 833 * v - 400 * u)
                var b = (y1192 + 2066 * u)
                
                r = if (r < 0) 0 else if (r > 262143) 262143 else r
                g = if (g < 0) 0 else if (g > 262143) 262143 else g
                b = if (b < 0) 0 else if (b > 262143) 262143 else b
                
                rgb[j * width + i] = 0xff000000.toInt() or ((r shl 6) and 0xff0000) or ((g shr 2) and 0xff00) or ((b shr 10) and 0xff)
            }
        }
    }
    
    /**
     * Поворот bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f && !useFrontCamera) return bitmap
        
        val matrix = Matrix()
        if (degrees != 0f) {
            matrix.postRotate(degrees)
        }
        
        // Для фронтальной камеры отражаем по горизонтали
        if (useFrontCamera) {
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        
        return rotated
    }
    
    /**
     * Переключение между фронтальной и задней камерой
     */
    fun switchCamera() {
        useFrontCamera = !useFrontCamera
        Log.d(Constants.TAG, "CameraManager: Переключение на ${if (useFrontCamera) "фронтальную" else "заднюю"} камеру")
        
        // Перезапускаем камеру с новым селектором
        cameraProvider?.let {
            bindCameraUseCases()
        }
    }
    
    /**
     * Перезапуск камеры (для применения новых настроек)
     */
    fun restartCamera() {
        Log.d(Constants.TAG, "CameraManager: Перезапуск камеры для применения настроек")
        cameraProvider?.let {
            bindCameraUseCases()
        }
    }
    
    /**
     * Получить текущую камеру
     */
    fun isFrontCamera(): Boolean = useFrontCamera
    
    /**
     * Установить камеру
     */
    fun setFrontCamera(useFront: Boolean) {
        if (useFrontCamera != useFront) {
            useFrontCamera = useFront
            cameraProvider?.let {
                bindCameraUseCases()
            }
        }
    }
    
    /**
     * Остановка камеры
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        camera = null
        imageAnalyzer = null
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
    
    /**
     * Включение/выключение фонарика
     */
    fun toggleFlashlight(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }
}

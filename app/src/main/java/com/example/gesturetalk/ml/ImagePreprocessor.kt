package com.example.gesturetalk.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Препроцессор изображений ТОЧНО как в Slovo demo.py:
 * 1. BGR -> RGB (уже в Bitmap)
 * 2. Letterbox resize до 224x224 с сохранением пропорций
 * 3. Нормализация: (pixel - mean) / std
 * 4. Перестановка осей: HWC -> CHW
 */
class ImagePreprocessor(
    private val mean: FloatArray,
    private val std: FloatArray,
    private val targetSize: Int = 224,
    private val onLog: ((String) -> Unit)? = null
) {
    /**
     * Resize изображения с сохранением пропорций и padding (letterbox)
     * ТОЧНО как в demo.py
     */
    private fun resizeWithLetterbox(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Вычисляем масштаб
        val scale = min(targetSize.toFloat() / width, targetSize.toFloat() / height)
        
        // Новые размеры без padding
        val newWidth = (width * scale).roundToInt()
        val newHeight = (height * scale).roundToInt()
        
        // Resize
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        val resized = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        
        // Создаем финальное изображение с padding
        val result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        
        // Заполняем серым цветом (114, 114, 114) как в demo.py
        canvas.drawColor(android.graphics.Color.rgb(114, 114, 114))
        
        // Вычисляем padding
        val dx = (targetSize - newWidth) / 2f
        val dy = (targetSize - newHeight) / 2f
        
        // Рисуем resized изображение по центру
        canvas.drawBitmap(resized, dx, dy, null)
        
        if (resized != bitmap) {
            resized.recycle()
        }
        
        return result
    }
    
    /**
     * Преобразование Bitmap в нормализованный тензор [C, H, W]
     * ТОЧНО как в demo.py
     */
    private fun bitmapToNormalizedTensor(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(targetSize * targetSize)
        bitmap.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
        
        // Создаем массив в формате CHW (3, 224, 224)
        val tensor = FloatArray(3 * targetSize * targetSize)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            
            // Извлекаем RGB компоненты
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            
            // Нормализация: (pixel - mean) / std (как в demo.py)
            val rNorm = (r - mean[0]) / std[0]
            val gNorm = (g - mean[1]) / std[1]
            val bNorm = (b - mean[2]) / std[2]
            
            // Заполняем в формате CHW
            tensor[i] = rNorm
            tensor[targetSize * targetSize + i] = gNorm
            tensor[2 * targetSize * targetSize + i] = bNorm
        }
        
        return tensor
    }
    
    /**
     * Полный пайплайн препроцессинга ТОЧНО как в demo.py
     */
    fun preprocess(bitmap: Bitmap): FloatArray {
        // 1. Resize с letterbox
        val resized = resizeWithLetterbox(bitmap)
        
        // 2. Нормализация и конвертация в тензор
        val tensor = bitmapToNormalizedTensor(resized)
        
        // Освобождаем временный bitmap
        if (resized != bitmap) {
            resized.recycle()
        }
        
        return tensor
    }
}

package com.example.gesturetalk.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Overlay для визуализации 55 landmarks (33 pose + 21 hand + 1 center)
 * Отображает точки и соединения между ними
 */
class LandmarksOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    init {
        // Включаем аппаратное ускорение для быстрой отрисовки
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    
    // Данные для отрисовки (volatile для thread-safety)
    @Volatile private var poseLandmarks: FloatArray? = null
    @Volatile private var handLandmarks: List<FloatArray>? = null
    @Volatile private var centerPoint: Pair<Float, Float>? = null
    @Volatile private var boundingBox: RectF? = null
    
    // Paint для отрисовки
    private val posePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 3f
        isAntiAlias = true
    }
    
    private val poseLinePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        alpha = 180
    }
    
    private val handPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        strokeWidth = 3f
        isAntiAlias = true
    }
    
    private val handLinePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        alpha = 180
    }
    
    private val centerPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        strokeWidth = 5f
        isAntiAlias = true
    }
    
    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    
    // MediaPipe Pose соединения (скелет тела)
    private val poseConnections = listOf(
        // Лицо
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7),
        Pair(0, 4), Pair(4, 5), Pair(5, 6), Pair(6, 8),
        // Торс
        Pair(9, 10),
        Pair(11, 12), Pair(11, 13), Pair(13, 15),
        Pair(12, 14), Pair(14, 16),
        Pair(11, 23), Pair(12, 24), Pair(23, 24),
        // Ноги
        Pair(23, 25), Pair(25, 27), Pair(27, 29), Pair(29, 31),
        Pair(24, 26), Pair(26, 28), Pair(28, 30), Pair(30, 32)
    )
    
    // MediaPipe Hand соединения (скелет руки)
    private val handConnections = listOf(
        // Большой палец
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
        // Указательный
        Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
        // Средний
        Pair(0, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
        // Безымянный
        Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
        // Мизинец
        Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
        // Ладонь
        Pair(5, 9), Pair(9, 13), Pair(13, 17)
    )
    
    /**
     * Обновление данных для отрисовки
     */
    fun updateLandmarks(
        pose: FloatArray?,
        hands: List<FloatArray>?,
        center: Pair<Float, Float>?,
        box: RectF?
    ) {
        poseLandmarks = pose
        handLandmarks = hands
        centerPoint = center
        boundingBox = box
        postInvalidate() // Быстрее чем invalidate() - вызывается из любого потока
    }
    
    /**
     * Очистка overlay
     */
    fun clear() {
        poseLandmarks = null
        handLandmarks = null
        centerPoint = null
        boundingBox = null
        postInvalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (viewWidth == 0f || viewHeight == 0f) return
        
        // 1. Рисуем bounding box
        boundingBox?.let { box ->
            canvas.drawRect(
                box.left * viewWidth,
                box.top * viewHeight,
                box.right * viewWidth,
                box.bottom * viewHeight,
                boxPaint
            )
        }
        
        // 2. Рисуем pose landmarks (33 точки тела)
        poseLandmarks?.let { pose ->
            // Сначала рисуем соединения
            for (connection in poseConnections) {
                val idx1 = connection.first * 2
                val idx2 = connection.second * 2
                
                if (idx1 + 1 < pose.size && idx2 + 1 < pose.size) {
                    val x1 = pose[idx1]
                    val y1 = pose[idx1 + 1]
                    val x2 = pose[idx2]
                    val y2 = pose[idx2 + 1]
                    
                    // Рисуем только если обе точки не нулевые
                    if (x1 > 0f && y1 > 0f && x2 > 0f && y2 > 0f) {
                        canvas.drawLine(
                            x1 * viewWidth, y1 * viewHeight,
                            x2 * viewWidth, y2 * viewHeight,
                            poseLinePaint
                        )
                    }
                }
            }
            
            // Потом рисуем точки
            for (i in 0 until 33) {
                val idx = i * 2
                if (idx + 1 < pose.size) {
                    val x = pose[idx]
                    val y = pose[idx + 1]
                    
                    if (x > 0f && y > 0f) {
                        canvas.drawCircle(x * viewWidth, y * viewHeight, 5f, posePaint)
                    }
                }
            }
        }
        
        // 3. Рисуем hand landmarks (21 точка на каждую руку)
        handLandmarks?.forEach { hand ->
            // Сначала рисуем соединения
            for (connection in handConnections) {
                val idx1 = connection.first * 2
                val idx2 = connection.second * 2
                
                if (idx1 + 1 < hand.size && idx2 + 1 < hand.size) {
                    val x1 = hand[idx1]
                    val y1 = hand[idx1 + 1]
                    val x2 = hand[idx2]
                    val y2 = hand[idx2 + 1]
                    
                    if (x1 > 0f && y1 > 0f && x2 > 0f && y2 > 0f) {
                        canvas.drawLine(
                            x1 * viewWidth, y1 * viewHeight,
                            x2 * viewWidth, y2 * viewHeight,
                            handLinePaint
                        )
                    }
                }
            }
            
            // Потом рисуем точки
            for (i in 0 until 21) {
                val idx = i * 2
                if (idx + 1 < hand.size) {
                    val x = hand[idx]
                    val y = hand[idx + 1]
                    
                    if (x > 0f && y > 0f) {
                        canvas.drawCircle(x * viewWidth, y * viewHeight, 6f, handPaint)
                    }
                }
            }
        }
        
        // 4. Рисуем центральную точку (центр тела)
        centerPoint?.let { (cx, cy) ->
            if (cx > 0f && cy > 0f) {
                canvas.drawCircle(cx * viewWidth, cy * viewHeight, 8f, centerPaint)
            }
        }
    }
}

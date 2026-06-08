package com.example.gesturetalk.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.gesturetalk.mediapipe.HandDetector
import com.example.gesturetalk.utils.Constants
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Конвейер Slovo MViT:
 *
 * - Камера непрерывно шлёт кадры; по [ModelConfig.frameInterval] считаем «шаги» модели (как demo.py).
 * - MediaPipe каждый кадр: движение рук → **сегмент жеста** (начало/конец по порогам + хвост).
 * - **Преролл** [PRE_ROLL]: последние субдискретные кадры до движения — контекст начала.
 * - **Постролл** [POST_ROLL]: после окончания движения ещё несколько шагов — конец позы.
 * - Сегмент произвольной длины **нормализуется до T ONNX** внутри [GestureRecognizer.normalizeFramesToWindow].
 * - Сегменты ставятся в **очередь**; пока идёт `session.run`, камера продолжает собирать следующий жест.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HybridGestureRecognizer(
    private val context: Context,
    private val config: ModelConfig,
    private val onLog: ((String) -> Unit)? = null
) {
    private val handDetector = HandDetector(context, onLog)
    private val gestureRecognizer = GestureRecognizer(context, config, onLog)

    private val released = AtomicBoolean(false)
    private val supervisor = SupervisorJob()
    private val pipelineScope = CoroutineScope(supervisor + Dispatchers.Default)
    private val segmentChannel = Channel<List<FloatArray>>(capacity = PIPELINE_QUEUE_CAPACITY)
    private val resultChannel = Channel<RecognitionResult>(capacity = RESULT_CHANNEL_CAPACITY)
    private var consumerJob: Job? = null

    /** Результаты инференса по завершённым сегментам (подписываться из lifecycleScope). */
    val pipelineResults: Flow<RecognitionResult> = resultChannel.receiveAsFlow()

    private val pendingSegments = AtomicInteger(0)

    private var lastDetectionResult: HandDetector.HandDetectionResult? = null

    private var totalFrames = 0
    private var framesWithHands = 0
    private var framesInGestureZone = 0
    private var lastStatsLogTime = 0L

    private var pipelineFrameCounter = 0

    private enum class SegmentPhase { IDLE, ACTIVE, POSTROLL }

    private var phase = SegmentPhase.IDLE
    private val ring = ArrayDeque<FloatArray>(PRE_ROLL + 1)
    private val segment = mutableListOf<FloatArray>()
    private var postRemaining = 0

    private var prevSortedHands: List<FloatArray>? = null
    private var motionEma = 0f
    private var tailRemaining = 0
    private var noHandsStreak = 0
    private var lastSegmentHintLogTime = 0L
    @Volatile
    private var lastResultAtMs = 0L

    suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(Constants.TAG, "HybridGestureRecognizer: Инициализация...")
        onLog?.invoke("=== ГИБРИДНАЯ СИСТЕМА (конвейер сегментов) ===")

        val mpSuccess = handDetector.initialize()
        if (!mpSuccess) {
            onLog?.invoke("⚠️ MediaPipe Hand не загружен")
            return@withContext false
        }

        val poseSuccess = handDetector.initializePose()
        if (!poseSuccess) {
            onLog?.invoke("⚠️ MediaPipe Pose не загружен — оверлей тела может быть пустым")
        }

        val slovoSuccess = gestureRecognizer.initialize(modelPath)
        if (!slovoSuccess) {
            onLog?.invoke("❌ Slovo не загружен")
            return@withContext false
        }

        startPipelineConsumer()
        onLog?.invoke("✓ Преролл $PRE_ROLL + постролл $POST_ROLL шагов, очередь до $PIPELINE_QUEUE_CAPACITY сегментов")
        onLog?.invoke("✓ Пока модель считает — камера собирает следующий жест")
        onLog?.invoke("")

        true
    }

    private fun startPipelineConsumer() {
        consumerJob = pipelineScope.launch {
            for (segs in segmentChannel) {
                try {
                    val r = gestureRecognizer.recognizeFromFrames(segs)
                    if (r != null) {
                        lastResultAtMs = System.currentTimeMillis()
                        resultChannel.trySend(r)
                    }
                } catch (e: Exception) {
                    Log.e(Constants.TAG, "Pipeline consumer", e)
                } finally {
                    pendingSegments.decrementAndGet()
                }
            }
        }
    }

    fun addFrame(bitmap: Bitmap) {
        if (released.get()) return

        totalFrames++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStatsLogTime > 3000) {
            logStats()
            lastStatsLogTime = currentTime
        }

        val detection = try {
            handDetector.detectHandsAndPose(bitmap)
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HybridGestureRecognizer: MediaPipe Hand", e)
            lastDetectionResult
        }

        if (detection != null) {
            lastDetectionResult = detection
            if (detection.hasHands) {
                framesWithHands++
                if (detection.isInGestureZone) {
                    framesInGestureZone++
                }
            }
        }

        val det = detection ?: return
        val shouldFeed = stepMotionShouldFeed(det)

        pipelineFrameCounter++
        if (pipelineFrameCounter < config.frameInterval) {
            return
        }
        pipelineFrameCounter = 0

        val tensor = try {
            gestureRecognizer.preprocessBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HybridGestureRecognizer: preprocess", e)
            return
        }

        onSubsampleTensor(shouldFeed, tensor)
    }

    /**
     * Обновляет EMA движения и «хвост» после движения; возвращает, идёт ли сейчас подача в сегмент (как раньше).
     */
    private fun stepMotionShouldFeed(det: HandDetector.HandDetectionResult): Boolean {
        val landmarks = det.handLandmarks
        if (!det.hasHands || landmarks.isNullOrEmpty()) {
            prevSortedHands = null
            motionEma *= NO_HAND_DECAY
            tailRemaining = 0
            noHandsStreak++
            if (phase != SegmentPhase.IDLE && noHandsStreak >= NO_HANDS_ABORT_IN_SEGMENT) {
                abortSegment("нет рук (${noHandsStreak} кадров подряд)")
                noHandsStreak = 0
            }
            maybeFlushIdleHands()
            return false
        }

        noHandsStreak = 0
        val sorted = sortHandsByWristX(landmarks)
        val delta = when {
            prevSortedHands == null -> 0f
            prevSortedHands!!.size != sorted.size -> MOTION_START
            else -> maxLandmarkDisplacement(prevSortedHands!!, sorted)
        }

        prevSortedHands = sorted.map { it.clone() }

        motionEma = MOTION_EMA_ALPHA * delta + (1f - MOTION_EMA_ALPHA) * motionEma

        // Хвост пополняем только при заметном сдвиге за кадр; иначе шум + EMA вечно держали tail=TAIL и сегмент не уходил в POSTROLL.
        val motionBurst = delta >= MOTION_START
        if (motionBurst) {
            tailRemaining = TAIL_FRAMES
        }

        var shouldFeed = (tailRemaining > 0) || motionBurst
        if (shouldFeed && tailRemaining > 0 && !motionBurst && motionEma < MOTION_STOP) {
            tailRemaining--
        }

        return shouldFeed
    }

    private fun onSubsampleTensor(shouldFeed: Boolean, t: FloatArray) {
        val tCopy = t.clone()
        when (phase) {
            SegmentPhase.IDLE -> {
                if (shouldFeed) {
                    phase = SegmentPhase.ACTIVE
                    segment.clear()
                    for (x in ring) {
                        segment.add(x.clone())
                    }
                    segment.add(tCopy)
                }
            }
            SegmentPhase.ACTIVE -> {
                if (shouldFeed) {
                    segment.add(tCopy)
                } else {
                    phase = SegmentPhase.POSTROLL
                    postRemaining = POST_ROLL
                    segment.add(tCopy)
                    postRemaining--
                    if (postRemaining <= 0) {
                        finalizeSegment()
                    }
                }
            }
            SegmentPhase.POSTROLL -> {
                segment.add(tCopy)
                postRemaining--
                if (postRemaining <= 0) {
                    finalizeSegment()
                }
            }
        }
        pushRing(tCopy)
        if (phase != SegmentPhase.IDLE && segment.size >= MAX_SEGMENT_STEPS) {
            onLog?.invoke("MViT: сегмент обрезан по длине ($MAX_SEGMENT_STEPS шагов) → в очередь")
            finalizeSegment()
        }
    }

    private fun pushRing(t: FloatArray) {
        ring.addLast(t.clone())
        while (ring.size > PRE_ROLL) {
            ring.removeFirst()
        }
    }

    private fun finalizeSegment() {
        phase = SegmentPhase.IDLE
        postRemaining = 0
        val snap = segment.map { it.clone() }
        segment.clear()
        if (snap.size < MIN_SEGMENT_TENSORS) {
            throttledSegmentHint("MViT: сегмент слишком короткий (${snap.size} шагов), отброшен")
            return
        }
        val sent = segmentChannel.trySend(snap)
        if (sent.isSuccess) {
            pendingSegments.incrementAndGet()
            onLog?.invoke("Сегмент в очередь: ${snap.size} шагов → ONNX(${gestureRecognizer.getWindowSize()}), в очереди ~${pendingSegments.get()}")
        } else {
            onLog?.invoke("MViT: очередь сегментов полна — отброшено ${snap.size} шагов")
        }
    }

    private fun abortSegment(reason: String) {
        if (phase == SegmentPhase.IDLE && segment.isEmpty()) return
        phase = SegmentPhase.IDLE
        segment.clear()
        postRemaining = 0
        throttledSegmentHint("MViT: сегмент прерван ($reason)")
    }

    private fun maybeFlushIdleHands() {
        if (noHandsStreak < NO_HANDS_STREAK) return
        if (phase != SegmentPhase.IDLE) return
        noHandsStreak = 0
    }

    private fun throttledSegmentHint(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastSegmentHintLogTime < 4500L) return
        lastSegmentHintLogTime = now
        onLog?.invoke(msg)
    }

    fun detectOnly(bitmap: Bitmap) {
        try {
            val detection = handDetector.detectHandsAndPose(bitmap)
            lastDetectionResult = detection
        } catch (e: Exception) {
            Log.e(Constants.TAG, "HybridGestureRecognizer: Ошибка detectOnly", e)
        }
    }

    /** @deprecated Используйте [pipelineResults]. */
    suspend fun recognize(): RecognitionResult? = gestureRecognizer.recognize()

    fun processResult(result: RecognitionResult?, lastWord: String?): String? {
        return gestureRecognizer.processResult(result, lastWord)
    }

    /** Скользящий буфер не используется — всегда false; готовность по завершённым сегментам в [pipelineResults]. */
    fun isReadyForInference(): Boolean = false

    /** Для UI: сколько тензоров уже в текущем (незавершённом) сегменте. */
    fun getCurrentFrameCount(): Int = segment.size

    fun getWindowSize(): Int = gestureRecognizer.getWindowSize()

    /** Сколько сегментов ждёт или обрабатывается воркером (после успешной постановки в очередь). */
    fun getPendingInferenceCount(): Int = pendingSegments.get()

    fun isInferenceRunning(): Boolean = gestureRecognizer.isInferenceRunning()

    fun getUiStatusText(): String {
        val now = System.currentTimeMillis()
        if (now - lastResultAtMs <= RESULT_STATUS_HOLD_MS) return "Ответ получен"
        if (isInferenceRunning()) return "Модель обрабатывает кадры"
        if (pendingSegments.get() > 0) return "Обработка набора кадров"
        return when (phase) {
            SegmentPhase.ACTIVE -> "Модель собирает кадры"
            SegmentPhase.POSTROLL -> "Обработка набора кадров"
            SegmentPhase.IDLE -> "Ожидание движения рук"
        }
    }

    fun getLastDetectionResult(): HandDetector.HandDetectionResult? = lastDetectionResult

    fun release() {
        if (!released.compareAndSet(false, true)) return
        supervisor.cancel()
        consumerJob?.cancel()
        segmentChannel.close()
        resultChannel.close()
        handDetector.release()
        gestureRecognizer.release()
        Log.d(Constants.TAG, "HybridGestureRecognizer: released")
    }

    private fun logStats() {
        if (totalFrames == 0) return
        val handsPercent = (framesWithHands * 100) / totalFrames
        val gesturePercent = (framesInGestureZone * 100) / totalFrames
        onLog?.invoke("📊 Камера: руки ~${handsPercent}%, в зоне ~${gesturePercent}%")
    }

    private companion object {
        val MOTION_START = 0.0075f
        val MOTION_STOP = 0.0035f
        val MOTION_EMA_ALPHA = 0.45f
        val NO_HAND_DECAY = 0.88f
        /** После реального движения — сколько шагов модели «доигрывать» тишину (не обновлять на каждый шумовой кадр). */
        const val TAIL_FRAMES = 28
        const val NO_HANDS_STREAK = 14
        /** Сколько кадров камеры подряд без рук, чтобы сбросить активный сегмент (MediaPipe моргает). */
        const val NO_HANDS_ABORT_IN_SEGMENT = 20

        const val PRE_ROLL = 5
        const val POST_ROLL = 5
        const val MIN_SEGMENT_TENSORS = 8
        /** Страховка: иначе при дрожании хвоста сегмент мог расти бесконечно без POSTROLL. */
        const val MAX_SEGMENT_STEPS = 80
        const val PIPELINE_QUEUE_CAPACITY = 2
        const val RESULT_CHANNEL_CAPACITY = 16
        const val RESULT_STATUS_HOLD_MS = 1200L
    }
}

private fun sortHandsByWristX(hands: List<FloatArray>): List<FloatArray> {
    return hands.sortedBy { it[0] }
}

private fun maxLandmarkDisplacement(a: List<FloatArray>, b: List<FloatArray>): Float {
    var maxD = 0f
    for (i in a.indices) {
        val pa = a[i]
        val pb = b[i]
        val limit = min(pa.size, pb.size)
        var j = 0
        while (j < limit) {
            val dx = pb[j] - pa[j]
            val dy = pb[j + 1] - pa[j + 1]
            maxD = max(maxD, hypot(dx, dy))
            j += 2
        }
    }
    return maxD
}

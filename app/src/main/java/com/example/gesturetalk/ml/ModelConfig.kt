package com.example.gesturetalk.ml

data class ModelConfig(
    val modelName: String,
    val frameInterval: Int,
    val mean: FloatArray,
    val std: FloatArray,
    val inputSize: Int,
    val inferenceIntervalMs: Long,
    val stableRepeats: Int,
    val minConfidence: Float,
    val repeatCooldownMs: Long,
    val maxWordsDisplay: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModelConfig

        if (modelName != other.modelName) return false
        if (frameInterval != other.frameInterval) return false
        if (!mean.contentEquals(other.mean)) return false
        if (!std.contentEquals(other.std)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = modelName.hashCode()
        result = 31 * result + frameInterval
        result = 31 * result + mean.contentHashCode()
        result = 31 * result + std.contentHashCode()
        return result
    }
}

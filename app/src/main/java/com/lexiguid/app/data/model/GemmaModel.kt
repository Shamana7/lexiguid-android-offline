package com.lexiguid.app.data.model

/**
 * Supported on-device Gemma models.
 * User can switch between these from Model Manager.
 */
enum class GemmaModel(
    val displayName: String,
    val huggingFaceId: String,
    val fileName: String,
    val sizeBytes: Long,
    val minRamGb: Int,
    val supportsVision: Boolean,
    val supportsAudio: Boolean,
    val description: String
) {
    GEMMA3_4B(
        displayName = "Gemma 3 4B",
        huggingFaceId = "litert-community/Gemma3-4B-IT",
        fileName = "gemma3-4b-it-int4.litertlm",
        sizeBytes = 2_300_000_000L,
        minRamGb = 8,
        supportsVision = false,
        supportsAudio = false,
        description = "Fast text-only model. Best for academic Q&A."
    ),
    GEMMA3N_E2B(
        displayName = "Gemma 3n E2B",
        huggingFaceId = "google/gemma-3n-E2B-it-litert-lm",
        fileName = "gemma-3n-E2B-it-int4.litertlm",
        sizeBytes = 3_655_827_456L,
        minRamGb = 8,
        supportsVision = true,
        supportsAudio = true,
        description = "Multimodal model. Supports text, image, and audio input."
    );

    companion object {
        val DEFAULT = GEMMA3_4B

        fun fromFileName(name: String): GemmaModel? =
            entries.find { it.fileName == name }
    }
}

/**
 * EmbeddingGemma model info — always required for RAG search.
 */
object EmbeddingGemmaInfo {
    const val DISPLAY_NAME = "EmbeddingGemma"
    const val FILE_NAME = "embedding_gemma_384.tflite"
    const val SIZE_BYTES = 200_000_000L
    const val EMBEDDING_DIMENSIONS = 384
    const val MAX_TOKENS = 2048
}

/**
 * Download/initialization state for a model.
 */
sealed class ModelState {
    data object NotDownloaded : ModelState()
    data class Downloading(val progress: Float) : ModelState()
    data object Downloaded : ModelState()
    data object Initializing : ModelState()
    data object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}

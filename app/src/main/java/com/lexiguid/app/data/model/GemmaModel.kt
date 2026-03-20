package com.lexiguid.app.data.model

/**
 * Supported on-device Gemma models.
 * We are using ONLY the 270M .task model for now (fast + works on most devices)
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

    GEMMA_270M(
        displayName = "Gemma 1B (Better Quality)",
        huggingFaceId = "",
        fileName = "gemma3-1b-it-int4.litertlm",
        sizeBytes = 600_000_000L,
        minRamGb = 4,
        supportsVision = false,
        supportsAudio = false,
        description = "1B model for better quality answers"
    );
    companion object {
        val DEFAULT = GEMMA_270M

        fun fromFileName(name: String): GemmaModel? =
            entries.find { it.fileName == name }
    }
}

/**
 * EmbeddingGemma model info — REQUIRED for RAG search
 * (for now we reuse same model just to make system work)
 */
object EmbeddingGemmaInfo {
    const val DISPLAY_NAME = "EmbeddingGemma"
    const val FILE_NAME = "gemma3-1b-it-int4.litertlm"
    const val SIZE_BYTES = 600_000_000L
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
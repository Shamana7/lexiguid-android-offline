package com.lexiguid.app.domain.engine

import android.content.Context
import android.util.Log
import com.lexiguid.app.data.model.EmbeddingGemmaInfo
import com.lexiguid.app.data.model.ModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

private const val TAG = "EmbeddingGemmaEngine"

/**
 * On-device EmbeddingGemma model for generating query embeddings.
 * 308M parameter model, <200MB RAM, outputs 768-dim vectors (MRL-truncated to 384).
 *
 * Only used at query time — the knowledge base is pre-embedded.
 */
@Singleton
class EmbeddingGemmaEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /**
     * Initialize the TFLite interpreter for EmbeddingGemma.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        _state.value = ModelState.Initializing

        try {
            val modelFile = File(context.filesDir, "models/${EmbeddingGemmaInfo.FILE_NAME}")
            if (!modelFile.exists()) {
                _state.value = ModelState.NotDownloaded
                return@withContext
            }

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                try {
                    addDelegate(GpuDelegate())
                    Log.d(TAG, "GPU delegate enabled for EmbeddingGemma")
                } catch (e: Exception) {
                    Log.w(TAG, "GPU delegate not available, using CPU", e)
                }
            }

            interpreter = Interpreter(modelFile, options)
            _state.value = ModelState.Ready
            Log.d(TAG, "EmbeddingGemma initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EmbeddingGemma", e)
            _state.value = ModelState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Generate embedding for a query string.
     * Returns FloatArray of [dimensions] size (default 384 via MRL truncation).
     */
    suspend fun embed(
        text: String,
        dimensions: Int = EmbeddingGemmaInfo.EMBEDDING_DIMENSIONS
    ): FloatArray = withContext(Dispatchers.Default) {
        val interp = interpreter
            ?: throw IllegalStateException("EmbeddingGemma not initialized")

        // Tokenize and prepare input buffer
        // EmbeddingGemma uses SentencePiece tokenizer (Gemma 3 based)
        val inputText = text.take(EmbeddingGemmaInfo.MAX_TOKENS * 4) // rough char limit
        val inputBytes = inputText.toByteArray(Charsets.UTF_8)
        val inputBuffer = ByteBuffer.allocateDirect(inputBytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(inputBytes)
            rewind()
        }

        // Output: full 768-dim embedding
        val fullDimensions = 768
        val outputBuffer = Array(1) { FloatArray(fullDimensions) }

        // Run inference
        interp.run(inputBuffer, outputBuffer)

        // MRL truncation: take first [dimensions] values
        val fullEmbedding = outputBuffer[0]
        val truncated = if (dimensions < fullDimensions) {
            fullEmbedding.copyOfRange(0, dimensions)
        } else {
            fullEmbedding
        }

        // L2 normalize for cosine similarity
        l2Normalize(truncated)
    }

    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.filesDir, "models/${EmbeddingGemmaInfo.FILE_NAME}")
        return modelFile.exists() && modelFile.length() > 0
    }

    fun release() {
        interpreter?.close()
        interpreter = null
        _state.value = ModelState.NotDownloaded
    }

    companion object {
        /**
         * L2 normalize a vector in place and return it.
         */
        fun l2Normalize(vector: FloatArray): FloatArray {
            var sumSquares = 0f
            for (v in vector) sumSquares += v * v
            val norm = sqrt(sumSquares)
            if (norm > 0f) {
                for (i in vector.indices) vector[i] /= norm
            }
            return vector
        }
    }
}

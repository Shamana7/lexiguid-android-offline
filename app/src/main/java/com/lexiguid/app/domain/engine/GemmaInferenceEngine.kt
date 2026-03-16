package com.lexiguid.app.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.lexiguid.app.data.model.GemmaModel
import com.lexiguid.app.data.model.InferenceConfig
import com.lexiguid.app.data.model.ModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GemmaInferenceEngine"

/**
 * Wraps LiteRT-LM Engine for on-device Gemma inference.
 * Supports model switching between Gemma 3 4B (text) and Gemma-3n-E2B (vision+audio).
 */
@Singleton
class GemmaInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModel: GemmaModel? = null
    private var currentConfig: InferenceConfig? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val activeModel: GemmaModel? get() = currentModel
    val supportsVision: Boolean get() = currentModel?.supportsVision == true

    /**
     * Initialize the engine with a specific Gemma model.
     * Call this when user selects a model or on app startup.
     */
    suspend fun initialize(
        model: GemmaModel,
        config: InferenceConfig
    ) = withContext(Dispatchers.IO) {
        // Release previous engine if switching models
        if (currentModel != null && currentModel != model) {
            release()
        }

        _state.value = ModelState.Initializing
        currentConfig = config

        try {
            val modelPath = File(context.filesDir, "models/${model.fileName}").absolutePath

            if (!File(modelPath).exists()) {
                _state.value = ModelState.NotDownloaded
                return@withContext
            }

            Log.d(TAG, "Initializing ${model.displayName} from $modelPath")

            val backend = try {
                Backend.GPU()
            } catch (e: Exception) {
                Log.w(TAG, "GPU not available, falling back to CPU", e)
                Backend.CPU()
            }

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = if (model.supportsVision) Backend.GPU() else null,
                audioBackend = if (model.supportsAudio) Backend.CPU() else null
            )

            engine = Engine(engineConfig)
            currentModel = model

            // Create initial conversation with system instruction
            resetConversation(config.systemInstruction)

            _state.value = ModelState.Ready
            Log.d(TAG, "${model.displayName} initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ${model.displayName}", e)
            _state.value = ModelState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Reset the conversation with a new system instruction.
     * Used when switching agent modes (greeting/rag/knowledge).
     */
    fun resetConversation(systemInstruction: String) {
        conversation?.close()

        val config = currentConfig ?: return
        val samplerConfig = SamplerConfig(
            topK = config.topK,
            topP = config.topP,
            temperature = config.temperature
        )

        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            maxTokens = config.maxTokens,
            systemInstruction = if (systemInstruction.isNotBlank()) {
                Contents(listOf(Message.text(systemInstruction)))
            } else null
        )

        conversation = engine?.startConversation(conversationConfig)
    }

    /**
     * Send a text message and stream the response token by token.
     */
    fun streamChat(text: String, image: Bitmap? = null): Flow<StreamToken> = callbackFlow {
        val conv = conversation
            ?: throw IllegalStateException("Engine not initialized. Call initialize() first.")

        val content = if (image != null && supportsVision) {
            // Vision model: send image + text
            Content.imageAndText(image, text)
        } else {
            Content.text(text)
        }

        conv.sendMessage(content, object : MessageCallback {
            override fun onToken(token: String) {
                trySend(StreamToken.Delta(token))
            }

            override fun onComplete(fullResponse: String) {
                trySend(StreamToken.Done(fullResponse))
                close()
            }

            override fun onError(error: Exception) {
                if (error is CancellationException) {
                    trySend(StreamToken.Done(""))
                    close()
                } else {
                    trySend(StreamToken.Error(error.message ?: "Inference error"))
                    close(error)
                }
            }
        })

        // Keep the flow alive until callback completes
        kotlinx.coroutines.awaitCancellation()
    }

    /**
     * Check if a model file exists on device.
     */
    fun isModelDownloaded(model: GemmaModel): Boolean {
        val modelFile = File(context.filesDir, "models/${model.fileName}")
        return modelFile.exists() && modelFile.length() > 0
    }

    fun release() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        currentModel = null
        currentConfig = null
        _state.value = ModelState.NotDownloaded
    }
}

sealed class StreamToken {
    data class Delta(val text: String) : StreamToken()
    data class Done(val fullText: String) : StreamToken()
    data class Error(val message: String) : StreamToken()
}

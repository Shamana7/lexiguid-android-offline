package com.lexiguid.app.domain.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GemmaInferenceEngine"

@Singleton
class GemmaInferenceEngine @Inject constructor(
    @param:ApplicationContext private val context: Context  // @param fixes the annotation warning
) {
    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var currentModel: GemmaModel? = null
    private var currentConfig: InferenceConfig? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val activeModel: GemmaModel? get() = currentModel

    private fun gpuBackend(): Backend = Backend.GPU()
    private fun cpuBackend(): Backend = Backend.CPU()

    suspend fun initialize(
        model: GemmaModel,
        config: InferenceConfig
    ) = withContext(Dispatchers.IO) {
        if (currentModel != null && currentModel != model) release()

        _state.value = ModelState.Initializing
        currentConfig = config

        try {
            val modelPath = File(context.filesDir, "models/${model.fileName}").absolutePath
            if (!File(modelPath).exists()) {
                _state.value = ModelState.NotDownloaded
                return@withContext
            }

            Log.d(TAG, "Initializing ${model.displayName} from $modelPath")

            // Try GPU first, fall back to CPU
            val newEngine = try {
                Engine(EngineConfig(modelPath = modelPath, backend = gpuBackend()))
                    .also { it.initialize() }
            } catch (gpuEx: Exception) {
                Log.w(TAG, "GPU failed, falling back to CPU", gpuEx)
                Engine(EngineConfig(modelPath = modelPath, backend = cpuBackend()))
                    .also { it.initialize() }
            }

            engine = newEngine
            currentModel = model
            resetConversation(config.systemInstruction)
            _state.value = ModelState.Ready
            Log.d(TAG, "${model.displayName} ready")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ${model.displayName}", e)
            _state.value = ModelState.Error(e.message ?: "Unknown error")
        }
    }

    fun resetConversation(systemInstruction: String) {
        conversation?.close()
        val config = currentConfig ?: return
        val eng = engine ?: return

        val conversationConfig = ConversationConfig(
            systemInstruction = if (systemInstruction.isNotBlank())
                Contents.of(systemInstruction) else null,
            samplerConfig = SamplerConfig(
                topK = config.topK,
                topP = config.topP.toDouble(),
                temperature = config.temperature.toDouble()
            )
        )
        conversation = eng.createConversation(conversationConfig)
    }

    fun streamChat(text: String): Flow<StreamToken> = flow {
        val conv = conversation
            ?: throw IllegalStateException("Engine not initialized.")
        try {
            conv.sendMessageAsync(text)
                .catch { e -> emit(StreamToken.Error(e.message ?: "Inference error")) }
                .collect { message -> emit(StreamToken.Delta(message.toString())) }
            emit(StreamToken.Done(""))
        } catch (e: Exception) {
            emit(StreamToken.Error(e.message ?: "Inference error"))
        }
    }.flowOn(Dispatchers.IO)

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
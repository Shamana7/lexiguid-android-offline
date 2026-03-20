package com.lexiguid.app.domain.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.lexiguid.app.data.model.GemmaModel
import com.lexiguid.app.data.model.InferenceConfig
import com.lexiguid.app.data.model.ModelState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GemmaInferenceEngine"

@Singleton
class GemmaInferenceEngine @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var currentModel: GemmaModel? = null
    private var currentConfig: InferenceConfig? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val activeModel: GemmaModel? get() = currentModel

    private fun getModelFile(model: GemmaModel): File =
        File(context.getExternalFilesDir(null), "models/${model.fileName}")

    suspend fun initialize(
        model: GemmaModel,
        config: InferenceConfig
    ) = withContext(Dispatchers.IO) {
        if (currentModel != null && currentModel != model) release()

        _state.value = ModelState.Initializing
        currentConfig = config

        try {
            val modelFile = getModelFile(model)
            Log.i(TAG, "=== INIT START ===")
            Log.i(TAG, "Model path: ${modelFile.absolutePath}")
            Log.i(TAG, "Model exists: ${modelFile.exists()}")
            Log.i(TAG, "Model size: ${modelFile.length()} bytes")

            if (!modelFile.exists()) {
                Log.e(TAG, "MODEL FILE NOT FOUND")
                _state.value = ModelState.NotDownloaded
                return@withContext
            }

            val engineConfig = EngineConfig(modelPath = modelFile.absolutePath)
            val newEngine = Engine(engineConfig)
            newEngine.initialize()
            Log.i(TAG, "Engine initialized")

            engine = newEngine
            currentModel = model

            conversation = newEngine.createConversation()
            Log.i(TAG, "Conversation created")

            _state.value = ModelState.Ready
            Log.i(TAG, "=== INIT COMPLETE ===")

        } catch (e: Exception) {
            Log.e(TAG, "=== INIT FAILED: ${e.message} ===", e)
            _state.value = ModelState.Error(e.message ?: "Unknown error")
        }
    }

    fun resetConversation(systemInstruction: String) {
        conversation?.close()
        val eng = engine ?: return
        conversation = eng.createConversation()
        Log.i(TAG, "Conversation reset")
    }

    fun streamChat(text: String): Flow<StreamToken> = flow {
        Log.i(TAG, "=== STREAM START: $text ===")

        val conv = conversation
            ?: throw IllegalStateException("Engine not initialized.")

        try {
            var tokenCount = 0

            conv.sendMessageAsync(text)
                .catch { e ->
                    Log.e(TAG, "Flow error: ${e.message}", e)
                    emit(StreamToken.Error(e.message ?: "Inference error"))
                }
                .collect { message ->
                    val raw = message.toString()
                    tokenCount++
                    Log.d(TAG, "Token #$tokenCount: [$raw]")
                    if (raw.isNotEmpty()) {
                        emit(StreamToken.Delta(raw))
                    }
                }

            Log.i(TAG, "=== STREAM DONE: $tokenCount tokens ===")
            emit(StreamToken.Done(""))

        } catch (e: Exception) {
            Log.e(TAG, "=== STREAM EXCEPTION: ${e.message} ===", e)
            emit(StreamToken.Error(e.message ?: "Inference error"))
        }
    }.flowOn(Dispatchers.IO)

    fun isModelDownloaded(model: GemmaModel): Boolean {
        val modelFile = getModelFile(model)
        val exists = modelFile.exists() && modelFile.length() > 0
        Log.i(TAG, "isModelDownloaded: $exists — ${modelFile.absolutePath}")
        return exists
    }

    fun release() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        currentModel = null
        currentConfig = null
        _state.value = ModelState.NotDownloaded
        Log.i(TAG, "Engine released")
    }
}

sealed class StreamToken {
    data class Delta(val text: String) : StreamToken()
    data class Done(val fullText: String) : StreamToken()
    data class Error(val message: String) : StreamToken()
}
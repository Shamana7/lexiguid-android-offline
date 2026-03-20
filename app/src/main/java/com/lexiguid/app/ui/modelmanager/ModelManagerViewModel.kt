package com.lexiguid.app.ui.modelmanager

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexiguid.app.data.model.EmbeddingGemmaInfo
import com.lexiguid.app.data.model.GemmaModel
import com.lexiguid.app.data.model.InferenceConfig
import com.lexiguid.app.data.model.ModelState
import com.lexiguid.app.data.repository.KBInfo
import com.lexiguid.app.data.repository.KnowledgeBaseManager
import com.lexiguid.app.domain.engine.EmbeddingGemmaEngine
import com.lexiguid.app.domain.engine.GemmaInferenceEngine
import com.lexiguid.app.domain.pipeline.PromptManager
import com.lexiguid.app.domain.router.AgentModeRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.File

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gemmaEngine: GemmaInferenceEngine,
    private val embeddingEngine: EmbeddingGemmaEngine,
    private val kbManager: KnowledgeBaseManager,
    private val promptManager: PromptManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    private fun refreshState() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamGb = memInfo.totalMem / (1024L * 1024L * 1024L)
        val freeStorage = context.getExternalFilesDir(null)?.freeSpace?.let {
            it / (1024L * 1024L * 1024L)
        } ?: 0L

        val modelStates = GemmaModel.entries.associateWith { model ->
            val isDownloaded = gemmaEngine.isModelDownloaded(model)

            when {
                gemmaEngine.activeModel == model -> ModelState.Ready
                isDownloaded -> ModelState.Downloaded
                else -> ModelState.NotDownloaded
            }
        }

        val embeddingState = when {
            embeddingEngine.state.value is ModelState.Ready -> ModelState.Ready
            embeddingEngine.isModelDownloaded() -> ModelState.Downloaded
            else -> ModelState.NotDownloaded
        }

        _uiState.value = ModelManagerUiState(
            deviceRamGb = totalRamGb,
            freeStorageGb = freeStorage,
            modelStates = modelStates,
            activeModel = gemmaEngine.activeModel,
            embeddingModelState = embeddingState,
            knowledgeBases = kbManager.getAvailableKBs(),
            kbBasePath = kbManager.kbBaseDir.absolutePath
        )
    }

    fun downloadModel(model: GemmaModel) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(modelStates = it.modelStates + (model to ModelState.Downloading(0f)))
            }
            _uiState.update {
                it.copy(modelStates = it.modelStates + (model to ModelState.Downloading(0.5f)))
            }
        }
    }

    fun deleteModel(model: GemmaModel) {
        viewModelScope.launch {
            if (gemmaEngine.activeModel == model) {
                gemmaEngine.release()
            }

            val modelFile = File(
                context.getExternalFilesDir(null),
                "models/${model.fileName}"
            )
            modelFile.delete()

            refreshState()
        }
    }

    fun activateModel(model: GemmaModel) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(modelStates = it.modelStates + (model to ModelState.Initializing))
            }

            val systemPrompt = promptManager.getSystemPrompt(AgentModeRouter.Mode.RAG)

            gemmaEngine.initialize(
                model = model,
                config = InferenceConfig(
                    temperature = 0.3f,
                    topK = 64,
                    topP = 0.95f,
                    maxTokens = 4096,
                    systemInstruction = systemPrompt
                )
            )

            refreshState()
        }
    }

    fun downloadEmbeddingModel() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(embeddingModelState = ModelState.Downloading(0f))
            }
        }
    }

    fun deleteEmbeddingModel() {
        viewModelScope.launch {
            embeddingEngine.release()

            val modelFile = File(
                context.getExternalFilesDir(null),
                "models/${EmbeddingGemmaInfo.FILE_NAME}"
            )
            modelFile.delete()

            refreshState()
        }
    }

    fun forceRefresh() {
        refreshState()
    }

    fun deleteKnowledgeBase(subject: String, grade: String) {
        kbManager.deleteKB(subject, grade)
        refreshState()
    }

    fun isModelActuallyPresent(model: GemmaModel): Boolean {
        return gemmaEngine.isModelDownloaded(model)
    }
}

data class ModelManagerUiState(
    val deviceRamGb: Long = 0,
    val freeStorageGb: Long = 0,
    val modelStates: Map<GemmaModel, ModelState> = emptyMap(),
    val activeModel: GemmaModel? = null,
    val embeddingModelState: ModelState = ModelState.NotDownloaded,
    val knowledgeBases: List<KBInfo> = emptyList(),
    val kbBasePath: String = ""
)
# LexiGuid Android — Offline AI

> **Fully offline AI chat for students.**
> Ask questions about your textbook. Get answers. No internet required.

<br>

## What This Is

LexiGuid is an Android app that runs a real LLM (Gemma 3) directly on the device — no server, no API, no data connection needed. Students can ask questions about their textbooks and get streamed AI responses, even without WiFi.

This repo contains the **offline module** of LexiGuid. The online version (API-based) was built separately and is fully working. This module adds on-device inference on top of that foundation.

**Status:** Offline inference ✅ working (Gemma 1B). RAG pipeline ⚠️ structure ready, needs real knowledge base to complete.

<br>

## Demo

Tested on Samsung Galaxy S24 — fully offline, no WiFi, no SIM data.

| Question | Response | Latency |
|----------|----------|---------|
| "what is photosynthesis" | "Photosynthesis is the process by which plants convert light energy into chemical energy in the form of glucose." | ~500ms first token |
| "tq" | "You're right to ask!" | ~140ms |
| "what's life" | "That's a great question! It's a broad and multifaceted concept." | ~160ms |

~20ms per token. Gemma 1B int4. GPU backend auto-selected.

<br>

## Architecture

```
User Input
    │
    ▼
ChatViewModel
    │
    ▼
RAGPipeline
    ├── AgentModeRouter        → detect: greeting / rag / general
    ├── [if RAG] LocalSearchTool → ObjectBox HNSW vector search
    │           EmbeddingGemmaEngine → query → 384-dim vector
    └── GemmaInferenceEngine   → LiteRT-LM → stream tokens
```

### Layer by layer

| Layer | File | What it does |
|-------|------|-------------|
| UI | `ChatScreen.kt`, `ChatViewModel.kt` | Chat UI, streaming display |
| Inference | `GemmaInferenceEngine.kt` | Loads model, runs LiteRT-LM, streams tokens |
| Embedding | `EmbeddingGemmaEngine.kt` | Converts text → 384-dim vector for search |
| Search | `LocalSearchTool.kt` | ObjectBox HNSW nearest-neighbor search |
| Pipeline | `RAGPipeline.kt` | Orchestrates route → search → augment → generate |
| Router | `AgentModeRouter.kt` | Regex-based mode detection |
| Storage | `KnowledgeChunk.kt`, `KnowledgeBaseManager.kt` | ObjectBox vector DB for textbook chunks |
| Conversations | Room + SQLite | Chat history persistence |

<br>

## Tech Stack

| What | Library | Version |
|------|---------|---------|
| On-device LLM runtime | LiteRT-LM | 0.9.0-alpha06 |
| LLM model | Gemma 3 1B (int4) | `.litertlm` format |
| Vector database | ObjectBox | 4.0.3 |
| DI | Hilt | 2.57.2 |
| UI | Jetpack Compose | BOM 2026.02.00 |
| Local DB | Room | 2.7.0 |
| Kotlin | — | 2.1.10 |

<br>

## Project Structure

```
app/src/main/
├── assets/
│   ├── models/                        # Model file goes here (gitignored)
│   └── prompts/                       # System prompts (safety, RAG, greeting, knowledge)
└── java/com/lexiguid/app/
    ├── data/
    │   ├── model/                     # KnowledgeChunk, GemmaModel, ChatModels
    │   ├── local/                     # Room DB (conversations, messages, profile)
    │   └── repository/                # ConversationRepository, KnowledgeBaseManager
    ├── domain/
    │   ├── engine/                    # GemmaInferenceEngine, EmbeddingGemmaEngine
    │   ├── router/                    # AgentModeRouter
    │   └── pipeline/                  # RAGPipeline, LocalSearchTool, PromptManager
    └── ui/
        ├── chat/                      # ChatScreen, ChatViewModel, components
        ├── home/                      # HomeScreen, HomeViewModel
        ├── modelmanager/              # ModelManagerScreen, ModelManagerViewModel
        ├── onboarding/                # OnboardingScreen
        ├── profile/                   # ProfileScreen
        └── settings/                  # SettingsScreen
```

<br>

## Setup

### Prerequisites
- Android Studio Hedgehog or newer
- Android device with 4GB+ RAM (8GB recommended for larger models)
- Physical device only — emulators don't support GPU inference
- USB debugging enabled

### Build

```bash
# Clone the repo
git clone <repo-url>
cd android

# Open in Android Studio and sync Gradle
# ObjectBox will auto-generate MyObjectBox.java on first build
```

### Add the model file

The model is **not committed to git** (300MB–1GB+). You need to add it manually.

**Option A — Push via ADB (development)**
```bash
adb push gemma3-1b-it-int4.litertlm \
  /sdcard/Android/data/com.lexiguid.app/files/models/gemma3-1b-it-int4.litertlm
```

**Option B — Copy into assets (for APK distribution)**
```
app/src/main/assets/models/gemma3-1b-it-int4.litertlm
```
The app copies it to internal storage on first launch automatically.
Uncomment the `getModelFile` assets block in `GemmaInferenceEngine.kt` to enable this.

### Download the model

| Model | Format | Size | Link |
|-------|--------|------|------|
| Gemma 3 270M q8 | `.litertlm` | ~290MB | [litert-community on HuggingFace](https://huggingface.co/litert-community) |
| Gemma 3 1B int4 | `.litertlm` | ~600MB | [litert-community on HuggingFace](https://huggingface.co/litert-community) |

> ⚠️ You must accept Google's license on HuggingFace before downloading.
> ⚠️ Only `.litertlm` format works. `.task` and TFLite `.litertlm` files produce `<pad>` output.

<br>

## ⚠️ The One Critical Thing to Know

> **Do NOT set a backend in `EngineConfig`.**

```kotlin
// ✅ CORRECT — auto-selects GPU/CPU
Engine(EngineConfig(modelPath = path))

// ❌ WRONG — causes <pad> token output, model runs but never replies
Engine(EngineConfig(modelPath = path, backend = Backend.GPU()))
```

This took a long time to figure out. LiteRT-LM selects the best available backend automatically. Explicitly passing `Backend.GPU()` or `Backend.CPU()` causes degenerate `<pad>` output. Just leave it out.

Same rule for `createConversation()` — call it with no arguments:

```kotlin
// ✅ CORRECT
val conversation = engine.createConversation()

// ❌ WRONG — SamplerConfig breaks small models
val conversation = engine.createConversation(ConversationConfig(
    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0)
))
```

<br>

## RAG Pipeline — How to Complete It

The RAG pipeline code is fully written and wired up. What's missing is a real pre-built ObjectBox knowledge base with textbook content.

**How it works when complete:**

```
1. Student asks "what is mitosis"
2. EmbeddingGemmaEngine converts query → 384-dim float vector
3. LocalSearchTool does HNSW nearest-neighbor search on ObjectBox
4. Top 8 most relevant textbook chunks retrieved
5. Prompt is built: [textbook excerpts] + [student question]
6. GemmaInferenceEngine streams the answer
```

**To complete RAG:**
1. Get the pre-built ObjectBox store from the backend/ML engineer (they have the Python pipeline that processes textbooks → embeddings → ObjectBox)
2. Push the store directory to the device:
   ```bash
   adb push science_grade8/ \
     /sdcard/Android/data/com.lexiguid.app/files/knowledge_base/science_grade8/
   ```
3. The app auto-detects it via `KnowledgeBaseManager` — no code changes needed
4. Select a subject in the chat screen (book icon, bottom left) to activate RAG mode

The knowledge base entity format:

```kotlin
@Entity
class KnowledgeChunk {
    @Id var id: Long
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray?   // pre-computed, 384 floats per chunk
    var pageContent: String?     // the actual textbook paragraph
    var subject: String?
    var chapter: String?
    var classLevel: String?
}
```

<br>

## What's Left to Build

| Task | Effort | Notes |
|------|--------|-------|
| RAG with real knowledge base | Medium | Pipeline fully coded. Need ObjectBox KB files from backend/ML team. See RAG section above. |
| In-app model download | Medium | `ModelManagerViewModel.kt` has TODO markers. Needs WorkManager + HuggingFace download logic. |
| Dedicated embedding model | Low | Replace EmbeddingGemmaEngine with nomic-embed or all-MiniLM for better retrieval accuracy. |

<br>

## .gitignore

Make sure these are in `.gitignore` — model files must never be committed:

```
*.litertlm
*.tflite
*.task
app/src/main/assets/models/
```

<br>

## APK Releases

| APK | Model | Status |
|-----|-------|--------|
| `lexiguid-offline-v0.1-gemma270m.apk` | Gemma 270M | ✅ Released |
| `lexiguid-offline-v0.2-gemma1b.apk` | Gemma 1B | ✅ Released |
| `lexiguid-offline-v1.0-rag.apk` | Gemma 1B + RAG | 🔜 Pending |

<br>

## What Was Fixed During Development

The AI-generated boilerplate had hallucinated APIs across LiteRT-LM, ObjectBox vector search, and TFLite. Every engine file had to be rewritten against actual SDK documentation.

Key fixes made:

| Problem | Fix |
|---------|-----|
| ObjectBox plugin not found in Gradle | Add `resolutionStrategy` mapping in `pluginManagement` |
| `@HnswIndex` / `VectorDistanceType` unresolved | ObjectBox must be 4.0.x, not 3.x |
| `readOnly(true)` error in BoxStoreBuilder | `readOnly()` takes no arguments in 4.x |
| `<pad>` tokens only, no real output | Remove `backend` param from `EngineConfig` entirely |
| `sendMessageAsync()` type error | Takes plain `String`, not `Contents.of()` |
| `createConversation()` with SamplerConfig | No-arg version works; SamplerConfig breaks 270M |
| ObjectBox query `.and()` error | Chain all conditions inside `box.query()`, not on `QueryBuilder` |
| Manifest `tools:node` not bound | Add `xmlns:tools` to `<manifest>` tag |
| `<uses-native-library>` AAPT error | Must be inside `<application>`, not `<manifest>` |
| KSP/Kotlin version mismatch warnings | KSP prefix must exactly match Kotlin version |
| `MyObjectBox` unresolved reference | Build once — ObjectBox annotation processor auto-generates it |

<br>

## Handover Notes

Built by the Android engineer at Kruthak — March 2026.

- The **online version** is separate and complete. APIs were provided by backend.
- This **offline module** was designed and debugged from scratch.
- For RAG knowledge base format and the Python embedding pipeline, speak to the backend/ML engineer.
- The architecture is clean and ready to extend — RAG just needs the KB files dropped in.
- Full technical knowledge transfer document uploaded in the work drive: `LexiGuid_KnowledgeTransfer.docx`

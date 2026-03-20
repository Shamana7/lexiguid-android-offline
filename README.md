# LexiGuid Android — Offline AI

> **Fully offline AI chat for students.**
> Ask questions about your textbook. Get answers. No internet required.

<br>

## What This Is

LexiGuid is an Android app that runs a real LLM (Gemma 3) directly on the device — no server, no API, no data connection needed. Students can ask questions about their textbooks and get streamed AI responses, even without WiFi.

This repo contains the **offline module** of LexiGuid. The online version (API-based) was built separately and is fully working. This module adds on-device inference on top of that foundation.

**Status:** Offline inference ✅ working. RAG pipeline ⚠️ structure ready, needs real knowledge base to complete.

<br>

## Demo

```
Student: what is photosynthesis
LexiGuid: Photosynthesis is the process by which plants and other organisms
          convert light energy into chemical energy in the form of glucose.

Student: tq
LexiGuid: You're right to ask!
```

Streaming token by token. ~20ms per token. Samsung S24. Fully offline.

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
| LLM model | Gemma 3 270M (q8) | `.litertlm` format |
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

The model is **not committed to git** (300MB+). You need to add it manually.

**Option A — Push via ADB (development)**
```bash
adb push gemma3-270m-it-q8.litertlm \
  /storage/emulated/0/Android/data/com.lexiguid.app/files/models/gemma3-270m-it-q8.litertlm
```

**Option B — Copy into assets (for APK distribution)**
```
app/src/main/assets/models/gemma3-270m-it-q8.litertlm
```
The app copies it to internal storage on first launch automatically.

### Download the model

| Model | Format | Size | Link |
|-------|--------|------|------|
| Gemma 3 270M q8 | `.litertlm` | ~290MB | [litert-community/gemma-3-270m-it](https://huggingface.co/litert-community) |
| Gemma 3 1B q8 | `.litertlm` | ~1GB | [litert-community](https://huggingface.co/litert-community) |

> ⚠️ You must accept Google's license on HuggingFace before downloading.

<br>

## ⚠️ The One Critical Thing to Know

> **Do NOT set a backend in `EngineConfig`.**

```kotlin
// ✅ CORRECT — auto-selects GPU/CPU
Engine(EngineConfig(modelPath = path))

// ❌ WRONG — causes <pad> token output, model runs but never replies
Engine(EngineConfig(modelPath = path, backend = Backend.GPU()))
```

This took a long time to figure out. LiteRT-LM selects the best available backend automatically. Explicitly passing `Backend.GPU()` or `Backend.CPU()` causes degenerate `<pad>` output on the 270M model. Just leave it out.

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
1. Build an ObjectBox store with textbook chunks + embeddings (Python side)
2. Push the store directory to:
   ```
   /sdcard/Android/data/com.lexiguid.app/files/knowledge_base/<subject>_<grade>/
   ```
3. The app auto-detects it via `KnowledgeBaseManager`

The knowledge base entity format:

```kotlin
@Entity
class KnowledgeChunk {
    @Id var id: Long
    @HnswIndex(dimensions = 384, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray?
    var pageContent: String?
    var subject: String?
    var chapter: String?
    var classLevel: String?
}
```

<br>

## Known Issues

| Issue | Notes |
|-------|-------|
| 270M model gives short answers | Expected — upgrade to 1B/2B |
| RAG not connected to real KB | Pipeline ready, needs KB files |
| Model download in-app not built | Currently manual push via ADB or assets |
| EmbeddingGemmaEngine reuses inference model | Needs dedicated embedding model for accuracy |

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
| `lexiguid-offline-v0.2-gemma2b.apk` | Gemma 2B | 🔜 Pending |
| `lexiguid-offline-v1.0-rag.apk` | Gemma 2B + RAG | 🔜 Pending |

<br>

## What Was Fixed During Development

The AI-generated boilerplate had hallucinated APIs across LiteRT-LM, ObjectBox vector search, and TFLite. Every engine file had to be rewritten against actual SDK documentation.

Key fixes made:

| Problem | Fix |
|---------|-----|
| ObjectBox plugin not found in Gradle | Add `resolutionStrategy` mapping in `pluginManagement` |
| `@HnswIndex` unresolved | ObjectBox must be 4.0.x, not 3.x |
| `readOnly(true)` error | `readOnly()` takes no arguments in 4.x |
| `<pad>` tokens only, no real output | Remove `backend` param from `EngineConfig` entirely |
| `sendMessageAsync()` type error | Takes plain `String`, not `Contents.of()` |
| `createConversation()` with SamplerConfig | No-arg version works; config breaks 270M |
| ObjectBox query `.and()` error | Chain all conditions inside `box.query()`, not on `QueryBuilder` |
| Manifest `tools:node` not bound | Add `xmlns:tools` to `<manifest>` tag |
| `<uses-native-library>` in wrong place | Must be inside `<application>`, not `<manifest>` |

<br>

## Handover Notes

Built by the Android engineer at Kruthak — March 2026.

- The **online version** is separate and complete. APIs were provided by backend.
- This **offline module** was designed and debugged from scratch.
- For RAG knowledge base format and the Python embedding pipeline, speak to the backend/ML engineer.
- The architecture is clean and ready to extend — RAG just needs the KB files.

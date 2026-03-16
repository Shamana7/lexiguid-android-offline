# LexiGuid ProGuard Rules

# Keep ObjectBox entities and generated code
-keep class com.lexiguid.app.data.model.KnowledgeChunk { *; }
-keep class com.lexiguid.app.data.model.KnowledgeChunk_ { *; }
-keep class io.objectbox.** { *; }
-dontwarn io.objectbox.**

# Keep LiteRT-LM classes
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# Keep TFLite classes (for EmbeddingGemma)
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep Room entities
-keep class com.lexiguid.app.data.local.** { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Gson
-keep class com.lexiguid.app.data.model.ToolCallInfo { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Firebase
-keep class com.google.firebase.** { *; }

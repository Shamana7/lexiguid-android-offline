package com.lexiguid.app.data.repository

import android.content.Context
import com.lexiguid.app.data.model.KnowledgeChunk
import com.lexiguid.app.data.model.MyObjectBox
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.BoxStore
import io.objectbox.Box
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeBaseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val stores: MutableMap<String, BoxStore> = mutableMapOf()

    val kbBaseDir: File
        get() = File(context.getExternalFilesDir(null), "knowledge_base")

    fun loadStore(subject: String, grade: String): BoxStore? {
        val key = buildKey(subject, grade)
        stores[key]?.let { return it }

        val dbDir = File(kbBaseDir, key)
        if (!dbDir.exists() || !File(dbDir, "data.mdb").exists()) return null

        return try {
            val store = MyObjectBox.builder()
                .directory(dbDir)
                .readOnly()          // ← no argument, just call readOnly()
                .maxReaders(4)
                .build()
            stores[key] = store
            store
        } catch (e: Exception) {
            null
        }
    }

    fun getBox(subject: String, grade: String): Box<KnowledgeChunk>? =
        loadStore(subject, grade)?.boxFor(KnowledgeChunk::class.java)

    fun getAvailableKBs(): List<KBInfo> {
        if (!kbBaseDir.exists()) return emptyList()
        return kbBaseDir.listFiles()
            ?.filter { it.isDirectory && File(it, "data.mdb").exists() }
            ?.map { dir ->
                val parts = dir.name.split("_", limit = 2)
                KBInfo(
                    subject = parts.getOrElse(0) { "" }.replace("_", " "),
                    grade = parts.getOrElse(1) { "" }.replace("_", " "),
                    sizeBytes = dir.walkTopDown().sumOf { it.length() },
                    path = dir.absolutePath
                )
            }
            ?.sortedBy { it.subject }
            ?: emptyList()
    }

    fun deleteKB(subject: String, grade: String): Boolean {
        val key = buildKey(subject, grade)
        stores[key]?.close()
        stores.remove(key)
        return File(kbBaseDir, key).deleteRecursively()
    }

    fun closeAll() {
        stores.values.forEach { it.close() }
        stores.clear()
    }

    private fun buildKey(subject: String, grade: String): String =
        "${subject.replace(" ", "_")}_${grade.replace(" ", "_")}"
}

data class KBInfo(
    val subject: String,
    val grade: String,
    val sizeBytes: Long,
    val path: String
)
package com.lexiguid.app.domain.pipeline

import com.lexiguid.app.data.model.KnowledgeChunk
import com.lexiguid.app.data.model.KnowledgeChunk_
import com.lexiguid.app.data.model.SearchResult
import com.lexiguid.app.data.repository.KnowledgeBaseManager
import com.lexiguid.app.domain.engine.EmbeddingGemmaEngine
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device vector search tool.
 * Replaces the backend's Qdrant hybrid search (search_tool.py).
 *
 * Flow: query → EmbeddingGemma → ObjectBox HNSW nearestNeighbors + metadata filters
 */
@Singleton
class LocalSearchTool @Inject constructor(
    private val kbManager: KnowledgeBaseManager,
    private val embeddingEngine: EmbeddingGemmaEngine
) {

    /**
     * Semantic search with metadata filtering.
     * Mirrors backend's search_kb() tool.
     */
    suspend fun searchKb(
        query: String,
        subject: String,
        grade: String,
        chapter: String? = null,
        k: Int = 8
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val box = kbManager.getBox(
            subject = subject.replace(" ", "_"),
            grade = grade.replace(" ", "_")
        ) ?: return@withContext emptyList()

        // Step 1: Embed the query using EmbeddingGemma
        val queryVector = embeddingEngine.embed(query)

        // Step 2: Build ObjectBox query — HNSW nearest neighbors + metadata filters
        // Overfetch to account for post-filter reduction
        val overfetchK = k * 3

        val queryBuilder = box.query(
            KnowledgeChunk_.embedding.nearestNeighbors(queryVector, overfetchK)
                .and(KnowledgeChunk_.subject.equal(
                    subject.replace(" ", "_"), io.objectbox.query.QueryBuilder.StringOrder.CASE_INSENSITIVE
                ))
                .and(KnowledgeChunk_.classLevel.equal(
                    grade.replace(" ", "_"), io.objectbox.query.QueryBuilder.StringOrder.CASE_INSENSITIVE
                ))
        )

        // Optional chapter filter
        if (!chapter.isNullOrBlank()) {
            queryBuilder.and(
                KnowledgeChunk_.chapter.equal(
                    chapter.replace(" ", "_"), io.objectbox.query.QueryBuilder.StringOrder.CASE_INSENSITIVE
                )
            )
        }

        val objectBoxQuery = queryBuilder.build()
        val results = objectBoxQuery.findWithScores()
        objectBoxQuery.close()

        // Step 3: Map to SearchResult, take top-k
        results.take(k).map { scored ->
            val chunk = scored.get()
            SearchResult(
                pageContent = chunk.pageContent ?: "",
                subject = chunk.subject ?: "",
                chapter = chunk.chapter ?: "",
                classLevel = chunk.classLevel ?: "",
                score = scored.score.toFloat(),
                contentType = chunk.contentType ?: ""
            )
        }
    }

    /**
     * Get unique subjects from loaded knowledge bases.
     * Replaces backend's get_available_subjects().
     */
    fun getAvailableSubjects(): List<String> {
        return kbManager.getAvailableKBs()
            .map { it.subject }
            .distinct()
            .sorted()
    }

    /**
     * Get chapters for a subject from the ObjectBox store.
     * Replaces backend's get_available_chapters().
     */
    fun getAvailableChapters(subject: String, grade: String): List<String> {
        val box = kbManager.getBox(
            subject = subject.replace(" ", "_"),
            grade = grade.replace(" ", "_")
        ) ?: return emptyList()

        return box.query(
            KnowledgeChunk_.subject.equal(
                subject.replace(" ", "_"),
                io.objectbox.query.QueryBuilder.StringOrder.CASE_INSENSITIVE
            )
        ).build().use { query ->
            query.property(KnowledgeChunk_.chapter)
                .distinct()
                .findStrings()
                .toList()
                .map { it.replace("_", " ") }
                .sorted()
        }
    }

    /**
     * Get content types for a subject.
     */
    fun getAvailableContentTypes(subject: String, grade: String): List<String> {
        val box = kbManager.getBox(
            subject = subject.replace(" ", "_"),
            grade = grade.replace(" ", "_")
        ) ?: return emptyList()

        return box.query(
            KnowledgeChunk_.subject.equal(
                subject.replace(" ", "_"),
                io.objectbox.query.QueryBuilder.StringOrder.CASE_INSENSITIVE
            )
        ).build().use { query ->
            query.property(KnowledgeChunk_.contentType)
                .distinct()
                .findStrings()
                .toList()
                .sorted()
        }
    }
}

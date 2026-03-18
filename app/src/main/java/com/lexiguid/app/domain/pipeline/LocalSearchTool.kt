package com.lexiguid.app.domain.pipeline

import com.lexiguid.app.data.model.KnowledgeChunk
import com.lexiguid.app.data.model.KnowledgeChunk_
import com.lexiguid.app.data.model.SearchResult
import com.lexiguid.app.data.repository.KnowledgeBaseManager
import com.lexiguid.app.domain.engine.EmbeddingGemmaEngine
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSearchTool @Inject constructor(
    private val kbManager: KnowledgeBaseManager,
    private val embeddingEngine: EmbeddingGemmaEngine
) {
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

        val queryVector = embeddingEngine.embed(query)
        val overfetchK = k * 3

        // All conditions must be chained inline — .and() on the condition,
        // NOT on the QueryBuilder separately
        var condition = KnowledgeChunk_.embedding.nearestNeighbors(queryVector, overfetchK)
            .and(KnowledgeChunk_.subject.equal(
                subject.replace(" ", "_"),
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            ))
            .and(KnowledgeChunk_.classLevel.equal(
                grade.replace(" ", "_"),
                QueryBuilder.StringOrder.CASE_INSENSITIVE
            ))

        if (!chapter.isNullOrBlank()) {
            condition = condition.and(
                KnowledgeChunk_.chapter.equal(
                    chapter.replace(" ", "_"),
                    QueryBuilder.StringOrder.CASE_INSENSITIVE
                )
            )
        }

        val results = box.query(condition)
            .build()
            .use { it.findWithScores() }

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

    fun getAvailableSubjects(): List<String> =
        kbManager.getAvailableKBs().map { it.subject }.distinct().sorted()

    fun getAvailableChapters(subject: String, grade: String): List<String> {
        val box = kbManager.getBox(
            subject = subject.replace(" ", "_"),
            grade = grade.replace(" ", "_")
        ) ?: return emptyList()

        return box.query(
            KnowledgeChunk_.subject.equal(
                subject.replace(" ", "_"),
                QueryBuilder.StringOrder.CASE_INSENSITIVE
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

    fun getAvailableContentTypes(subject: String, grade: String): List<String> {
        val box = kbManager.getBox(
            subject = subject.replace(" ", "_"),
            grade = grade.replace(" ", "_")
        ) ?: return emptyList()

        return box.query(
            KnowledgeChunk_.subject.equal(
                subject.replace(" ", "_"),
                QueryBuilder.StringOrder.CASE_INSENSITIVE
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
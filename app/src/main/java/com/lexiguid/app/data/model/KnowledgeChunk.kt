package com.lexiguid.app.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

@Entity
class KnowledgeChunk(
    @Id var id: Long = 0
) {
    @HnswIndex(
        dimensions = 384,
        distanceType = VectorDistanceType.COSINE,
        neighborsPerNode = 30,
        indexingSearchCount = 100
    )
    var embedding: FloatArray? = null

    var pageContent: String? = null

    @Index var subject: String? = null
    @Index var chapter: String? = null
    @Index var classLevel: String? = null

    var country: String? = null
    var state: String? = null
    var board: String? = null
    var medium: String? = null
    var contentType: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeChunk) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
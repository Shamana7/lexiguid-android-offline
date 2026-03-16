package com.lexiguid.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val grade: String = "",
    val country: String = "",
    val state: String = "",
    val board: String = "",
    val medium: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

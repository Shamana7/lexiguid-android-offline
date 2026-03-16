package com.lexiguid.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.lexiguid.app.data.local.ConversationDao
import com.lexiguid.app.data.local.LexiGuidDatabase
import com.lexiguid.app.data.local.MessageDao
import com.lexiguid.app.data.local.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LexiGuidDatabase =
        Room.databaseBuilder(
            context,
            LexiGuidDatabase::class.java,
            "lexiguid.db"
        ).build()

    @Provides
    fun provideConversationDao(db: LexiGuidDatabase): ConversationDao =
        db.conversationDao()

    @Provides
    fun provideMessageDao(db: LexiGuidDatabase): MessageDao =
        db.messageDao()

    @Provides
    fun provideProfileDao(db: LexiGuidDatabase): UserProfileDao =
        db.profileDao()
}

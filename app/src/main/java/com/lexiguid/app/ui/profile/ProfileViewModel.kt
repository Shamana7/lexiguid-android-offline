package com.lexiguid.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexiguid.app.data.local.UserProfileDao
import com.lexiguid.app.data.local.UserProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileDao: UserProfileDao
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> =
        profileDao.getProfile()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveProfile(
        firstName: String,
        grade: String,
        country: String,
        state: String,
        board: String,
        medium: String
    ) {
        viewModelScope.launch {
            val existing = profileDao.getProfileOnce()
            val entity = UserProfileEntity(
                uid = existing?.uid ?: "local_user",
                firstName = firstName,
                grade = grade,
                country = country,
                state = state,
                board = board,
                medium = medium,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            profileDao.upsert(entity)
        }
    }
}

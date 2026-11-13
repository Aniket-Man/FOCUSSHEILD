package com.example.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.StudyChannelEntity
import com.example.data.repository.StudyChannelRepository
import com.example.feature.youtube.ai.YouTubeAiStudyClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StudyChannelTab {
    ALLOWED,
    BLOCKED
}

data class StudyChannelsUiState(
    val selectedTab: StudyChannelTab = StudyChannelTab.ALLOWED,
    val allowedChannels: List<StudyChannelEntity> = emptyList(),
    val blockedChannels: List<StudyChannelEntity> = emptyList(),
    val totalAllowedCount: Int = 0,
    val totalBlockedCount: Int = 0,
    val aiCallsSavedPercent: Int = 0,
    val totalChannelChecks: Int = 0,
    val searchQuery: String = "",
    val isAddDialogVisible: Boolean = false,
    val feedbackMessage: String? = null
) {
    val displayedChannels: List<StudyChannelEntity>
        get() = if (selectedTab == StudyChannelTab.ALLOWED) allowedChannels else blockedChannels
}

@HiltViewModel
class StudyChannelsViewModel @Inject constructor(
    private val studyChannelRepository: StudyChannelRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(StudyChannelTab.ALLOWED)
    private val _isAddDialogVisible = MutableStateFlow(false)
    private val _feedbackMessage = MutableStateFlow<String?>(null)

    init {
        YouTubeAiStudyClassifier.warmUpModel()
    }

    val uiState: StateFlow<StudyChannelsUiState> = combine(
        studyChannelRepository.allChannels,
        _selectedTab,
        _searchQuery,
        _isAddDialogVisible,
        combine(
            YouTubeAiStudyClassifier.aiGuardStats,
            _feedbackMessage
        ) { stats, feedback -> stats to feedback }
    ) { allChannels, selectedTab, query, isAddVisible, (guardStats, feedback) ->
        val sortedAll = allChannels.sortedByDescending { it.createdAt }
        val allAllowed = sortedAll.filter { it.isApproved }
        val allBlocked = sortedAll.filter { !it.isApproved }

        val trimmed = query.trim()
        fun matchesQuery(channel: StudyChannelEntity): Boolean {
            if (trimmed.isBlank()) return true
            return channel.channelName.contains(trimmed, ignoreCase = true) ||
                channel.channelId.contains(trimmed, ignoreCase = true) ||
                channel.channelUrl.contains(trimmed, ignoreCase = true) ||
                channel.category.contains(trimmed, ignoreCase = true)
        }

        StudyChannelsUiState(
            selectedTab = selectedTab,
            allowedChannels = allAllowed.filter(::matchesQuery),
            blockedChannels = allBlocked.filter(::matchesQuery),
            totalAllowedCount = allAllowed.size,
            totalBlockedCount = allBlocked.size,
            aiCallsSavedPercent = guardStats.savedPercent,
            totalChannelChecks = guardStats.totalChecks,
            searchQuery = query,
            isAddDialogVisible = isAddVisible,
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudyChannelsUiState()
    )

    fun onTabSelected(tab: StudyChannelTab) {
        _selectedTab.value = tab
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun showAddDialog() {
        _isAddDialogVisible.value = true
    }

    fun dismissAddDialog() {
        _isAddDialogVisible.value = false
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    fun toggleApproval(channel: StudyChannelEntity) {
        val newApproved = !channel.isApproved
        viewModelScope.launch {
            studyChannelRepository.toggleChannelApproval(channel.id, newApproved)
            _feedbackMessage.value = if (newApproved) {
                "Moved ${channel.channelName} to Allowed"
            } else {
                "Moved ${channel.channelName} to Blocked"
            }
        }
    }

    fun deleteChannel(channel: StudyChannelEntity) {
        viewModelScope.launch {
            studyChannelRepository.removeChannel(channel.id)
            _feedbackMessage.value = "Removed ${channel.channelName} — AI will re-check on next visit"
        }
    }
}

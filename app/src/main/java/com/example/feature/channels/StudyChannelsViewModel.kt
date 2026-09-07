package com.example.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.StudyChannelEntity
import com.example.data.repository.StudyChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyChannelsUiState(
    val channels: List<StudyChannelEntity> = emptyList(),
    val searchQuery: String = "",
    val isAddDialogVisible: Boolean = false,
    val addChannelNameInput: String = "",
    val addChannelUrlInput: String = "",
    val addErrorMessage: String? = null,
    val isAdding: Boolean = false
)

class StudyChannelsViewModel(
    private val studyChannelRepository: StudyChannelRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _dialogState = MutableStateFlow(
        DialogState(
            isVisible = false,
            nameInput = "",
            urlInput = "",
            errorMessage = null,
            isAdding = false
        )
    )

    private data class DialogState(
        val isVisible: Boolean,
        val nameInput: String,
        val urlInput: String,
        val errorMessage: String?,
        val isAdding: Boolean
    )

    val uiState: StateFlow<StudyChannelsUiState> = combine(
        studyChannelRepository.allChannels,
        _searchQuery,
        _dialogState
    ) { allChannels, query, dialog ->
        val filtered = if (query.isBlank()) {
            allChannels
        } else {
            allChannels.filter { channel ->
                channel.channelName.contains(query, ignoreCase = true) ||
                        channel.channelUrl.contains(query, ignoreCase = true) ||
                        channel.channelId.contains(query, ignoreCase = true)
            }
        }

        StudyChannelsUiState(
            channels = filtered,
            searchQuery = query,
            isAddDialogVisible = dialog.isVisible,
            addChannelNameInput = dialog.nameInput,
            addChannelUrlInput = dialog.urlInput,
            addErrorMessage = dialog.errorMessage,
            isAdding = dialog.isAdding
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudyChannelsUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun showAddDialog() {
        _dialogState.value = DialogState(
            isVisible = true,
            nameInput = "",
            urlInput = "",
            errorMessage = null,
            isAdding = false
        )
    }

    fun dismissAddDialog() {
        _dialogState.update { it.copy(isVisible = false, errorMessage = null) }
    }

    fun onAddChannelNameChanged(name: String) {
        _dialogState.update { it.copy(nameInput = name, errorMessage = null) }
    }

    fun onAddChannelUrlChanged(url: String) {
        _dialogState.update { it.copy(urlInput = url, errorMessage = null) }
    }

    fun toggleApproval(channel: StudyChannelEntity) {
        viewModelScope.launch {
            studyChannelRepository.toggleChannelApproval(channel.id, !channel.isApproved)
        }
    }

    fun deleteChannel(channel: StudyChannelEntity) {
        viewModelScope.launch {
            studyChannelRepository.removeChannel(channel.id)
        }
    }

    fun addChannel() {
        val current = _dialogState.value
        val name = current.nameInput.trim()
        val url = current.urlInput.trim()

        if (name.isBlank()) {
            _dialogState.update { it.copy(errorMessage = "Please enter a channel name") }
            return
        }

        if (url.isBlank()) {
            _dialogState.update { it.copy(errorMessage = "Please enter a channel URL or handle (e.g. @PhysicsWallah)") }
            return
        }

        viewModelScope.launch {
            _dialogState.update { it.copy(isAdding = true, errorMessage = null) }
            val result = studyChannelRepository.addChannel(channelName = name, channelUrl = url, isApproved = true)

            result.fold(
                onSuccess = {
                    dismissAddDialog()
                },
                onFailure = { error ->
                    _dialogState.update {
                        it.copy(
                            isAdding = false,
                            errorMessage = error.message ?: "Failed to add channel"
                        )
                    }
                }
            )
        }
    }
}

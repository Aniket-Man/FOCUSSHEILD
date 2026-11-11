package com.example.feature.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.R
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Stable group keys stay English-free so the UI can localise TODAY/YESTERDAY on every recomposition. */
enum class SessionDateGroup { TODAY, YESTERDAY, EARLIER }

data class DateGroupedSessions(
    val group: SessionDateGroup,
    val dateMillis: Long,
    val totalFocusTimeMillis: Long,
    val sessions: List<SessionRecordEntity>
)

data class HistoryUiState(
    val groupedSessions: List<DateGroupedSessions> = emptyList(),
    val totalStudyTimeMillis: Long = 0L,
    val totalCompletedCount: Int = 0,
    val selectedSession: SessionRecordEntity? = null,
    val isLoading: Boolean = false
)

class HistoryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val sessionRepository: SessionRepository =
        (application as FocusShieldApp).sessionRepository

    private val _selectedSession = MutableStateFlow<SessionRecordEntity?>(null)
    private val _deletingSessionId = MutableStateFlow<String?>(null)
    val deletingSessionId: StateFlow<String?> = _deletingSessionId.asStateFlow()
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()

    fun clearOperationError() {
        _operationError.value = null
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        sessionRepository.allSessions,
        _selectedSession
    ) { sessions, selected ->
        val totalStudyTime = sessions.sumOf { it.actualDurationMillis }
        val completedCount = sessions.count { it.completed }

        val grouped = groupSessionsByDate(sessions)

        HistoryUiState(
            groupedSessions = grouped,
            totalStudyTimeMillis = totalStudyTime,
            totalCompletedCount = completedCount,
            selectedSession = selected,
            isLoading = false
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun selectSession(session: SessionRecordEntity?) {
        _selectedSession.value = session
    }

    fun deleteSession(id: String) {
        if (_deletingSessionId.value != null) return
        viewModelScope.launch {
            _deletingSessionId.value = id
            try {
                // Finish the short database delete even if the screen is recreated mid-write.
                withContext(NonCancellable) { sessionRepository.deleteSession(id) }
                if (_selectedSession.value?.id == id) {
                    _selectedSession.value = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                android.util.Log.e("HistoryViewModel", "Unable to delete session record", exception)
                _operationError.value = getApplication<Application>().getString(R.string.history_delete_error)
            } finally {
                _deletingSessionId.value = null
            }
        }
    }

    /**
     * Groups by local calendar day and keeps a stable day key. Formatting "Today"/"Yesterday" or the
     * date itself belongs to the UI so it follows the active locale without re-querying history.
     */
    private fun groupSessionsByDate(sessions: List<SessionRecordEntity>): List<DateGroupedSessions> {
        val today = startOfDayMillis(System.currentTimeMillis())
        val yesterday = today - TimeUnit.DAYS.toMillis(1)

        val map = linkedMapOf<Long, MutableList<SessionRecordEntity>>()
        for (session in sessions) {
            map.getOrPut(startOfDayMillis(session.startTime)) { mutableListOf() }.add(session)
        }

        return map.map { (dayMillis, list) ->
            val group = when (dayMillis) {
                today -> SessionDateGroup.TODAY
                yesterday -> SessionDateGroup.YESTERDAY
                else -> SessionDateGroup.EARLIER
            }
            DateGroupedSessions(
                group = group,
                dateMillis = dayMillis,
                totalFocusTimeMillis = list.sumOf { it.actualDurationMillis },
                sessions = list
            )
        }
    }

    private fun startOfDayMillis(timeMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}

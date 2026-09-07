package com.example.feature.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DateGroupedSessions(
    val dateHeader: String,
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun selectSession(session: SessionRecordEntity?) {
        _selectedSession.value = session
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            sessionRepository.deleteSession(id)
            if (_selectedSession.value?.id == id) {
                _selectedSession.value = null
            }
        }
    }

    private fun groupSessionsByDate(sessions: List<SessionRecordEntity>): List<DateGroupedSessions> {
        val calendar = Calendar.getInstance()
        val todayCalendar = Calendar.getInstance()
        val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        val map = linkedMapOf<String, MutableList<SessionRecordEntity>>()

        for (session in sessions) {
            calendar.timeInMillis = session.startTime

            val dateKey = when {
                isSameDay(calendar, todayCalendar) -> "TODAY"
                isSameDay(calendar, yesterdayCalendar) -> "YESTERDAY"
                else -> dateFormat.format(Date(session.startTime))
            }

            map.getOrPut(dateKey) { mutableListOf() }.add(session)
        }

        return map.map { (header, list) ->
            val totalTime = list.sumOf { it.actualDurationMillis }
            DateGroupedSessions(
                dateHeader = header,
                totalFocusTimeMillis = totalTime,
                sessions = list
            )
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}

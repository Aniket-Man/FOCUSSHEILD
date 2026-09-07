package com.example.feature.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.data.preferences.FocusPreferences
import com.example.data.repository.AnalyticsRepository
import com.example.feature.analytics.domain.AnalyticsPeriod
import com.example.feature.analytics.domain.PeriodAnalyticsSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalyticsUiState(
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.LAST_7_DAYS,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null,
    val summary: PeriodAnalyticsSummary = PeriodAnalyticsSummary(),
    val dailyGoalMinutes: Int = 240,
    val streakThresholdMinutes: Int = 20,
    val isLoading: Boolean = false
)

private data class PeriodFilterState(
    val period: AnalyticsPeriod = AnalyticsPeriod.LAST_7_DAYS,
    val customStart: Long? = null,
    val customEnd: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app = application as FocusShieldApp
    private val analyticsRepository: AnalyticsRepository = app.analyticsRepository

    private val _filterState = MutableStateFlow(PeriodFilterState())

    val uiState: StateFlow<AnalyticsUiState> = combine(
        _filterState.flatMapLatest { filter ->
            analyticsRepository.getPeriodAnalyticsFlow(
                period = filter.period,
                customStart = filter.customStart,
                customEnd = filter.customEnd
            )
        },
        analyticsRepository.preferencesFlow,
        _filterState
    ) { summary, prefs: FocusPreferences, filter ->
        AnalyticsUiState(
            selectedPeriod = filter.period,
            customStartMillis = filter.customStart,
            customEndMillis = filter.customEnd,
            summary = summary,
            dailyGoalMinutes = prefs.dailyGoalMinutes,
            streakThresholdMinutes = prefs.minimumStreakThresholdMinutes,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(isLoading = true)
    )

    fun selectPeriod(period: AnalyticsPeriod) {
        _filterState.update {
            it.copy(period = period)
        }
    }

    fun setCustomRange(startMillis: Long, endMillis: Long) {
        _filterState.update {
            it.copy(
                period = AnalyticsPeriod.CUSTOM,
                customStart = startMillis,
                customEnd = endMillis
            )
        }
    }

    fun updateDailyGoal(hours: Int, minutes: Int) {
        val totalMinutes = (hours * 60 + minutes).coerceAtLeast(10)
        viewModelScope.launch {
            analyticsRepository.updateDailyGoalMinutes(totalMinutes)
        }
    }

    fun updateStreakThreshold(minutes: Int) {
        viewModelScope.launch {
            analyticsRepository.updateMinimumStreakThresholdMinutes(minutes.coerceAtLeast(5))
        }
    }
}

package com.example.feature.wellbeing

enum class UsagePeriodTab {
    DAILY,
    WEEKLY
}

enum class UsageCategoryFilter {
    ALL,
    DISTRACTING,
    PRODUCTIVE,
    OTHERS
}

enum class AppUsageCategory {
    DISTRACTING,
    PRODUCTIVE,
    OTHERS
}

data class UsageAppBreakdownItem(
    val packageName: String,
    val appName: String,
    val primaryCategory: AppUsageCategory,
    val distractingMillis: Long,
    val productiveMillis: Long,
    val othersMillis: Long,
    val totalMillis: Long,
    val averageDailyMillis: Long = totalMillis,
    val launchCount: Int = 0,
    val systemCategoryLabel: String = ""
) {
    fun millisForFilter(filter: UsageCategoryFilter): Long = when (filter) {
        UsageCategoryFilter.ALL -> totalMillis
        UsageCategoryFilter.DISTRACTING -> distractingMillis
        UsageCategoryFilter.PRODUCTIVE -> productiveMillis
        UsageCategoryFilter.OTHERS -> othersMillis
    }

    fun matchesFilter(filter: UsageCategoryFilter): Boolean = when (filter) {
        UsageCategoryFilter.ALL -> true
        UsageCategoryFilter.DISTRACTING -> distractingMillis > 0L || (totalMillis == 0L && primaryCategory == AppUsageCategory.DISTRACTING)
        UsageCategoryFilter.PRODUCTIVE -> productiveMillis > 0L || (totalMillis == 0L && primaryCategory == AppUsageCategory.PRODUCTIVE)
        UsageCategoryFilter.OTHERS -> othersMillis > 0L || (totalMillis == 0L && primaryCategory == AppUsageCategory.OTHERS)
    }
}

data class UsageDaySummary(
    val dayIndexInWeek: Int,
    val dayStartMillis: Long,
    val dayInitial: String,
    val shortDateFormatted: String,
    val isToday: Boolean,
    val isYesterday: Boolean,
    val isFuture: Boolean,
    val distractingMillis: Long,
    val productiveMillis: Long,
    val othersMillis: Long,
    val apps: List<UsageAppBreakdownItem>
) {
    val totalMillis: Long
        get() = distractingMillis + productiveMillis + othersMillis

    fun millisForFilter(filter: UsageCategoryFilter): Long = when (filter) {
        UsageCategoryFilter.ALL -> totalMillis
        UsageCategoryFilter.DISTRACTING -> distractingMillis
        UsageCategoryFilter.PRODUCTIVE -> productiveMillis
        UsageCategoryFilter.OTHERS -> othersMillis
    }
}

data class UsageWeekSummary(
    val weekOffset: Int,
    val weekStartMillis: Long,
    val rangeLabel: String,
    val days: List<UsageDaySummary>,
    val distractingMillis: Long,
    val productiveMillis: Long,
    val othersMillis: Long,
    val previousWeekTotalMillis: Long,
    val apps: List<UsageAppBreakdownItem>
) {
    val totalMillis: Long
        get() = distractingMillis + productiveMillis + othersMillis

    fun millisForFilter(filter: UsageCategoryFilter): Long = when (filter) {
        UsageCategoryFilter.ALL -> totalMillis
        UsageCategoryFilter.DISTRACTING -> distractingMillis
        UsageCategoryFilter.PRODUCTIVE -> productiveMillis
        UsageCategoryFilter.OTHERS -> othersMillis
    }

    fun deltaPercentVsLastWeek(): Int? {
        if (previousWeekTotalMillis <= 0L) return null
        val diff = totalMillis - previousWeekTotalMillis
        return kotlin.math.round((diff.toDouble() / previousWeekTotalMillis.toDouble()) * 100.0).toInt()
    }
}

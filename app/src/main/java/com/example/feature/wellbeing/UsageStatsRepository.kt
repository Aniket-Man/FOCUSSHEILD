package com.example.feature.wellbeing

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.FocusShieldApp
import com.example.core.util.DeviceUsageStatsHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageStatsRepository(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCategoryOverrides(): Map<String, AppUsageCategory> {
        val result = mutableMapOf<String, AppUsageCategory>()
        prefs.all.forEach { (key, value) ->
            val name = value as? String ?: return@forEach
            val category = runCatching { AppUsageCategory.valueOf(name) }.getOrNull()
            if (category != null) {
                result[key] = category
            }
        }
        return result
    }

    fun setAppCategoryOverride(packageName: String, category: AppUsageCategory?) {
        val editor = prefs.edit()
        if (category == null) {
            editor.remove(packageName)
        } else {
            editor.putString(packageName, category.name)
        }
        editor.apply()
    }

    suspend fun loadWeekSummary(
        weekOffset: Int,
        overrides: Map<String, AppUsageCategory> = getCategoryOverrides()
    ): Pair<Boolean, UsageWeekSummary> {
        val hasPermission = DeviceUsageStatsHelper.hasUsageStatsPermission(context)
        val todayStart = startOfDayMillis(System.currentTimeMillis())
        val currentWeekMonday = startOfWeekMondayMillis(todayStart)
        val targetWeekMonday = addDaysMillis(currentWeekMonday, weekOffset * 7)
        val now = System.currentTimeMillis()

        val pm = context.packageManager
        val app = FocusShieldApp.instance
        val blockedPackages = runCatching {
            app.blockedAppRepository.getEnabledBlockedAppsList().map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        val limitedPackages = runCatching {
            app.appLimitRepository.getAllLimits().map { it.packageName }.toSet()
        }.getOrDefault(emptySet())

        val dayInitials = listOf("M", "T", "W", "T", "F", "S", "S")
        val shortDateFormatter = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val rangeFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

        if (!hasPermission) {
            val emptyDays = (0 until 7).map { index ->
                val dayStart = addDaysMillis(targetWeekMonday, index)
                UsageDaySummary(
                    dayIndexInWeek = index,
                    dayStartMillis = dayStart,
                    dayInitial = dayInitials[index],
                    shortDateFormatted = shortDateFormatter.format(dayStart),
                    isToday = dayStart == todayStart,
                    isYesterday = dayStart == addDaysMillis(todayStart, -1),
                    isFuture = dayStart > todayStart,
                    distractingMillis = 0L,
                    productiveMillis = 0L,
                    othersMillis = 0L,
                    apps = emptyList()
                )
            }
            val rangeEnd = minOf(addDaysMillis(targetWeekMonday, 6), todayStart)
            val rangeLabel = "${rangeFormatter.format(targetWeekMonday)} - ${rangeFormatter.format(maxOf(targetWeekMonday, rangeEnd))}"
            return false to UsageWeekSummary(
                weekOffset = weekOffset,
                weekStartMillis = targetWeekMonday,
                rangeLabel = rangeLabel,
                days = emptyDays,
                distractingMillis = 0L,
                productiveMillis = 0L,
                othersMillis = 0L,
                previousWeekTotalMillis = 0L,
                apps = emptyList()
            )
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val installedLaunchers = queryInstalledLauncherPackages(pm)
        val weekEndExclusive = addDaysMillis(targetWeekMonday, 7)
        val sessions = runCatching {
            app.database.sessionDao().getSessionsBetween(targetWeekMonday, minOf(weekEndExclusive, now))
        }.getOrDefault(emptyList())
        val sessionWindows = sessions.mapNotNull { record ->
            val end = if (record.endTime > 0L) record.endTime else record.startTime + record.actualDurationMillis
            if (end > record.startTime) record.startTime to end else null
        }

        val appMetadataCache = mutableMapOf<String, Pair<String, ApplicationInfo?>>()
        fun getAppMeta(pkg: String): Pair<String, ApplicationInfo?> {
            return appMetadataCache.getOrPut(pkg) {
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val label = if (info != null) {
                    runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg.substringAfterLast('.'))
                } else {
                    pkg.substringAfterLast('.')
                }
                label to info
            }
        }

        val days = mutableListOf<UsageDaySummary>()
        val weeklyPkgDistracting = mutableMapOf<String, Long>()
        val weeklyPkgProductive = mutableMapOf<String, Long>()
        val weeklyPkgOthers = mutableMapOf<String, Long>()
        val weeklyPkgLaunches = mutableMapOf<String, Int>()
        val weeklyPkgActiveDays = mutableMapOf<String, Int>()

        var elapsedDaysInWeek = 0

        for (dayIdx in 0 until 7) {
            val dayStart = addDaysMillis(targetWeekMonday, dayIdx)
            val dayEnd = if (dayStart == todayStart) now else addDaysMillis(dayStart, 1) - 1L
            val isFuture = dayStart > todayStart
            val isToday = dayStart == todayStart
            val isYesterday = dayStart == addDaysMillis(todayStart, -1)

            if (isFuture) {
                days.add(
                    UsageDaySummary(
                        dayIndexInWeek = dayIdx,
                        dayStartMillis = dayStart,
                        dayInitial = dayInitials[dayIdx],
                        shortDateFormatted = shortDateFormatter.format(dayStart),
                        isToday = false,
                        isYesterday = false,
                        isFuture = true,
                        distractingMillis = 0L,
                        productiveMillis = 0L,
                        othersMillis = 0L,
                        apps = emptyList()
                    )
                )
                continue
            }

            elapsedDaysInWeek++
            val dayRaw = queryDayIntervalsAndStats(
                usm = usm,
                dayStart = dayStart,
                dayEnd = dayEnd,
                isToday = isToday
            )

            val dayApps = mutableListOf<UsageAppBreakdownItem>()
            var dayDistracting = 0L
            var dayProductive = 0L
            var dayOthers = 0L

            val allPkgsForDay = (dayRaw.packageIntervals.keys + dayRaw.fallbackTotals.keys + installedLaunchers)
            allPkgsForDay.forEach { pkg ->
                if (isNoiseSystemPackage(pkg)) return@forEach
                val (appName, appInfo) = getAppMeta(pkg)
                if (appInfo == null && (dayRaw.fallbackTotals[pkg] ?: 0L) <= 0L && dayRaw.packageIntervals[pkg].isNullOrEmpty()) {
                    return@forEach
                }

                val baseCategory = resolveAppCategory(
                    pkg = pkg,
                    appInfo = appInfo,
                    overrides = overrides,
                    blockedPackages = blockedPackages,
                    limitedPackages = limitedPackages
                )
                val systemCatLabel = resolveSystemCategoryLabel(pkg, appInfo)

                var dMillis = 0L
                var pMillis = 0L
                var oMillis = 0L

                val mergedIntervals = mergeIntervals(dayRaw.packageIntervals[pkg].orEmpty())
                if (mergedIntervals.isNotEmpty()) {
                    mergedIntervals.forEach { (s, e) ->
                        val totalSlice = (e - s).coerceAtLeast(0L)
                        val sessionOverlap = if (baseCategory == AppUsageCategory.DISTRACTING && canEarnStudyModeCredit(pkg, overrides)) {
                            computeOverlapMillis(s, e, sessionWindows)
                        } else {
                            0L
                        }
                        val baseSlice = (totalSlice - sessionOverlap).coerceAtLeast(0L)
                        pMillis += sessionOverlap
                        when (baseCategory) {
                            AppUsageCategory.DISTRACTING -> dMillis += baseSlice
                            AppUsageCategory.PRODUCTIVE -> pMillis += baseSlice
                            AppUsageCategory.OTHERS -> oMillis += baseSlice
                        }
                    }
                } else {
                    val fallback = (dayRaw.fallbackTotals[pkg] ?: 0L).coerceAtLeast(0L)
                    if (fallback > 0L) {
                        when (baseCategory) {
                            AppUsageCategory.DISTRACTING -> dMillis = fallback
                            AppUsageCategory.PRODUCTIVE -> pMillis = fallback
                            AppUsageCategory.OTHERS -> oMillis = fallback
                        }
                    }
                }

                val appTotal = dMillis + pMillis + oMillis
                val launches = dayRaw.launchCounts[pkg] ?: if (appTotal > 0L) 1 else 0

                if (appTotal > 0L || pkg in installedLaunchers) {
                    dayDistracting += dMillis
                    dayProductive += pMillis
                    dayOthers += oMillis

                    if (appTotal > 0L) {
                        weeklyPkgDistracting[pkg] = (weeklyPkgDistracting[pkg] ?: 0L) + dMillis
                        weeklyPkgProductive[pkg] = (weeklyPkgProductive[pkg] ?: 0L) + pMillis
                        weeklyPkgOthers[pkg] = (weeklyPkgOthers[pkg] ?: 0L) + oMillis
                        weeklyPkgLaunches[pkg] = (weeklyPkgLaunches[pkg] ?: 0) + launches
                        weeklyPkgActiveDays[pkg] = (weeklyPkgActiveDays[pkg] ?: 0) + 1
                    }

                    dayApps.add(
                        UsageAppBreakdownItem(
                            packageName = pkg,
                            appName = appName,
                            primaryCategory = baseCategory,
                            distractingMillis = dMillis,
                            productiveMillis = pMillis,
                            othersMillis = oMillis,
                            totalMillis = appTotal,
                            averageDailyMillis = appTotal,
                            launchCount = launches,
                            systemCategoryLabel = systemCatLabel
                        )
                    )
                }
            }

            val sortedDayApps = dayApps.sortedWith(
                compareByDescending<UsageAppBreakdownItem> { it.totalMillis }
                    .thenBy { it.appName.lowercase(Locale.getDefault()) }
            )

            days.add(
                UsageDaySummary(
                    dayIndexInWeek = dayIdx,
                    dayStartMillis = dayStart,
                    dayInitial = dayInitials[dayIdx],
                    shortDateFormatted = shortDateFormatter.format(dayStart),
                    isToday = isToday,
                    isYesterday = isYesterday,
                    isFuture = false,
                    distractingMillis = dayDistracting,
                    productiveMillis = dayProductive,
                    othersMillis = dayOthers,
                    apps = sortedDayApps
                )
            )
        }

        val activeDayDivisor = elapsedDaysInWeek.coerceAtLeast(1)
        val allWeeklyPkgs = (weeklyPkgDistracting.keys + weeklyPkgProductive.keys + weeklyPkgOthers.keys + installedLaunchers)
        val weeklyApps = allWeeklyPkgs.mapNotNull { pkg ->
            if (isNoiseSystemPackage(pkg)) return@mapNotNull null
            val (appName, appInfo) = getAppMeta(pkg)
            val dMillis = weeklyPkgDistracting[pkg] ?: 0L
            val pMillis = weeklyPkgProductive[pkg] ?: 0L
            val oMillis = weeklyPkgOthers[pkg] ?: 0L
            val total = dMillis + pMillis + oMillis
            if (appInfo == null && total <= 0L) return@mapNotNull null

            val baseCategory = resolveAppCategory(
                pkg = pkg,
                appInfo = appInfo,
                overrides = overrides,
                blockedPackages = blockedPackages,
                limitedPackages = limitedPackages
            )
            val daysActive = (weeklyPkgActiveDays[pkg] ?: activeDayDivisor).coerceAtLeast(1)

            UsageAppBreakdownItem(
                packageName = pkg,
                appName = appName,
                primaryCategory = baseCategory,
                distractingMillis = dMillis,
                productiveMillis = pMillis,
                othersMillis = oMillis,
                totalMillis = total,
                averageDailyMillis = if (total > 0L) total / daysActive else 0L,
                launchCount = weeklyPkgLaunches[pkg] ?: 0,
                systemCategoryLabel = resolveSystemCategoryLabel(pkg, appInfo)
            )
        }.sortedWith(
            compareByDescending<UsageAppBreakdownItem> { it.totalMillis }
                .thenBy { it.appName.lowercase(Locale.getDefault()) }
        )

        val previousWeekMonday = addDaysMillis(targetWeekMonday, -7)
        val previousWeekTotal = queryPreviousWeekTotalMillis(
            usm = usm,
            prevWeekMonday = previousWeekMonday,
            installedLaunchers = installedLaunchers
        )

        val rangeEnd = minOf(addDaysMillis(targetWeekMonday, 6), todayStart)
        val rangeLabel = "${rangeFormatter.format(targetWeekMonday)} - ${rangeFormatter.format(maxOf(targetWeekMonday, rangeEnd))}"

        val summary = UsageWeekSummary(
            weekOffset = weekOffset,
            weekStartMillis = targetWeekMonday,
            rangeLabel = rangeLabel,
            days = days,
            distractingMillis = days.sumOf { it.distractingMillis },
            productiveMillis = days.sumOf { it.productiveMillis },
            othersMillis = days.sumOf { it.othersMillis },
            previousWeekTotalMillis = previousWeekTotal,
            apps = weeklyApps
        )
        return true to summary
    }

    private data class DayRawStats(
        val packageIntervals: Map<String, List<Pair<Long, Long>>>,
        val launchCounts: Map<String, Int>,
        val fallbackTotals: Map<String, Long>
    )

    private fun queryDayIntervalsAndStats(
        usm: UsageStatsManager?,
        dayStart: Long,
        dayEnd: Long,
        isToday: Boolean
    ): DayRawStats {
        if (usm == null || dayEnd <= dayStart) {
            return DayRawStats(emptyMap(), emptyMap(), emptyMap())
        }

        val packageIntervals = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
        val launchCounts = mutableMapOf<String, Int>()
        val fallbackTotals = mutableMapOf<String, Long>()

        try {
            val events = usm.queryEvents(dayStart, dayEnd)
            val event = UsageEvents.Event()
            val foregroundStarts = mutableMapOf<String, Long>()
            var lastPkg: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (pkg.isNullOrBlank() || isNoiseSystemPackage(pkg)) continue

                val key = "$pkg/${event.className ?: pkg}"
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (!foregroundStarts.containsKey(key)) {
                            foregroundStarts[key] = event.timeStamp
                        }
                        if (lastPkg != pkg) {
                            launchCounts[pkg] = (launchCounts[pkg] ?: 0) + 1
                            lastPkg = pkg
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = foregroundStarts.remove(key)
                        if (start != null) {
                            val clampedStart = start.coerceAtLeast(dayStart)
                            val clampedEnd = event.timeStamp.coerceAtMost(dayEnd)
                            if (clampedEnd > clampedStart) {
                                packageIntervals.getOrPut(pkg) { mutableListOf() }.add(clampedStart to clampedEnd)
                            }
                        }
                    }
                }
            }

            foregroundStarts.forEach { (key, start) ->
                val pkg = key.substringBefore('/')
                val clampedStart = start.coerceAtLeast(dayStart)
                if (dayEnd > clampedStart) {
                    packageIntervals.getOrPut(pkg) { mutableListOf() }.add(clampedStart to dayEnd)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryEvents failed for $dayStart..$dayEnd: ${e.message}")
        }

        // Fill fallback totals from aggregate / daily stats when raw events aren't retained for a package or day
        try {
            if (isToday) {
                val aggregated = usm.queryAndAggregateUsageStats(dayStart, dayEnd)
                aggregated?.forEach { (pkg, stat) ->
                    if (!isNoiseSystemPackage(pkg) && !packageIntervals.containsKey(pkg) && stat.totalTimeInForeground > 0L) {
                        fallbackTotals[pkg] = stat.totalTimeInForeground
                    }
                }
            } else if (packageIntervals.isEmpty()) {
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
                stats?.forEach { stat ->
                    val pkg = stat.packageName ?: return@forEach
                    if (!isNoiseSystemPackage(pkg) && stat.totalTimeInForeground > 0L && stat.lastTimeUsed in dayStart..dayEnd) {
                        fallbackTotals[pkg] = maxOf(fallbackTotals[pkg] ?: 0L, stat.totalTimeInForeground)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return DayRawStats(packageIntervals, launchCounts, fallbackTotals)
    }

    private fun queryPreviousWeekTotalMillis(
        usm: UsageStatsManager?,
        prevWeekMonday: Long,
        installedLaunchers: Set<String>
    ): Long {
        if (usm == null) return 0L
        var total = 0L
        for (i in 0 until 7) {
            val dayStart = addDaysMillis(prevWeekMonday, i)
            val dayEnd = addDaysMillis(dayStart, 1) - 1L
            val raw = queryDayIntervalsAndStats(usm, dayStart, dayEnd, isToday = false)
            if (raw.packageIntervals.isNotEmpty()) {
                raw.packageIntervals.forEach { (pkg, intervals) ->
                    if (!isNoiseSystemPackage(pkg)) {
                        total += mergeIntervals(intervals).sumOf { (s, e) -> e - s }
                    }
                }
            } else {
                raw.fallbackTotals.forEach { (pkg, millis) ->
                    if (!isNoiseSystemPackage(pkg) && (pkg in installedLaunchers || millis > 0L)) {
                        total += millis
                    }
                }
            }
        }
        return total
    }

    private fun queryInstalledLauncherPackages(pm: PackageManager): Set<String> {
        return try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .filterNot { isNoiseSystemPackage(it) }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun resolveAppCategory(
        pkg: String,
        appInfo: ApplicationInfo?,
        overrides: Map<String, AppUsageCategory>,
        blockedPackages: Set<String>,
        limitedPackages: Set<String>
    ): AppUsageCategory {
        overrides[pkg]?.let { return it }
        if (pkg in blockedPackages || pkg in limitedPackages) {
            return AppUsageCategory.DISTRACTING
        }

        val lower = pkg.lowercase(Locale.ROOT)
        if (lower == context.packageName.lowercase(Locale.ROOT) ||
            lower.contains("focusshield")
        ) {
            return AppUsageCategory.OTHERS
        }

        if (PRODUCTIVE_KEYWORDS.any { lower.contains(it) }) {
            return AppUsageCategory.PRODUCTIVE
        }

        if (DISTRACTING_KEYWORDS.any { lower.contains(it) }) {
            return AppUsageCategory.DISTRACTING
        }

        if (OTHERS_KEYWORDS.any { lower.contains(it) }) {
            return AppUsageCategory.OTHERS
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
            return when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME,
                ApplicationInfo.CATEGORY_SOCIAL,
                ApplicationInfo.CATEGORY_VIDEO -> AppUsageCategory.DISTRACTING
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppUsageCategory.PRODUCTIVE
                else -> AppUsageCategory.OTHERS
            }
        }
        return AppUsageCategory.OTHERS
    }

    private fun canEarnStudyModeCredit(pkg: String, overrides: Map<String, AppUsageCategory>): Boolean {
        if (overrides.containsKey(pkg)) return false
        val lower = pkg.lowercase(Locale.ROOT)
        return lower.contains("youtube") ||
            lower.contains("chrome") ||
            lower.contains("opera") ||
            lower.contains("brave") ||
            lower.contains("firefox") ||
            lower.contains("browser")
    }

    private fun resolveSystemCategoryLabel(pkg: String, appInfo: ApplicationInfo?): String {
        val lower = pkg.lowercase(Locale.ROOT)
        return when {
            lower.contains("youtube") || lower.contains("netflix") || lower.contains("hotstar") -> "Video & Media"
            lower.contains("instagram") || lower.contains("whatsapp") || lower.contains("facebook") || lower.contains("reddit") -> "Social"
            lower.contains("chrome") || lower.contains("browser") || lower.contains("opera") || lower.contains("brave") -> "Web Browser"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null -> when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> "Games"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_VIDEO -> "Video & Media"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                ApplicationInfo.CATEGORY_MAPS -> "Maps & Travel"
                else -> "Apps & Tools"
            }
            else -> "Apps & Tools"
        }
    }

    private fun isNoiseSystemPackage(pkg: String): Boolean {
        if (pkg in SYSTEM_NOISE_PACKAGES) return true
        return pkg.startsWith("com.android.systemui") ||
            pkg.startsWith("com.google.android.inputmethod") ||
            pkg.startsWith("com.samsung.android.honeyboard") ||
            pkg.contains(".launcher")
    }

    private fun mergeIntervals(raw: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (raw.isEmpty()) return emptyList()
        val sorted = raw.sortedBy { it.first }
        val merged = mutableListOf<Pair<Long, Long>>()
        var curStart = sorted[0].first
        var curEnd = sorted[0].second
        for (i in 1 until sorted.size) {
            val (s, e) = sorted[i]
            if (s <= curEnd) {
                curEnd = maxOf(curEnd, e)
            } else {
                merged.add(curStart to curEnd)
                curStart = s
                curEnd = e
            }
        }
        merged.add(curStart to curEnd)
        return merged
    }

    private fun computeOverlapMillis(
        start: Long,
        end: Long,
        windows: List<Pair<Long, Long>>
    ): Long {
        if (windows.isEmpty() || end <= start) return 0L
        var overlap = 0L
        for ((wStart, wEnd) in windows) {
            val s = maxOf(start, wStart)
            val e = minOf(end, wEnd)
            if (e > s) {
                overlap += (e - s)
            }
        }
        return overlap.coerceAtMost(end - start)
    }

    private fun startOfDayMillis(timeMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun startOfWeekMondayMillis(dayStartMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > dayStartMillis) {
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        return cal.timeInMillis
    }

    private fun addDaysMillis(baseMillis: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = baseMillis
            add(Calendar.DAY_OF_YEAR, days)
        }
        return cal.timeInMillis
    }

    companion object {
        private const val TAG = "UsageStatsRepository"
        private const val PREFS_NAME = "focus_usage_category_prefs"

        private val SYSTEM_NOISE_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.android.launcher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.oneplus.launcher",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )

        private val PRODUCTIVE_KEYWORDS = listOf(
            "chatgpt", "openai", "claude", "perplexity", "gemini", "notion", "anki",
            "duolingo", "khan", "coursera", "udemy", "docs", "sheets", "slides",
            "classroom", "quizlet", "alarmy", "calendar", "keep", "obsidian",
            "todoist", "forest", "googlequicksearchbox", "microsoft.office"
        )

        private val DISTRACTING_KEYWORDS = listOf(
            "youtube", "instagram", "facebook", "tiktok", "musically", "snapchat",
            "reddit", "twitter", "chrome", "opera", "brave", "firefox", "netflix",
            "hotstar", "jiohotstar", "primevideo", "flipkart", "meesho", "amazon",
            "myntra", "freefire", "pubg", "bgmi", "clash", "roblox"
        )

        private val OTHERS_KEYWORDS = listOf(
            "settings", "camera", "gallery", "album", "photos", "whatsapp",
            "messaging", "mms", "contacts", "dialer", "vending", "bhim", "upi",
            "paytm", "phonepe", "gpay", "maps", "calculator", "deskclock", "clock",
            "documentsui", "files"
        )
    }
}

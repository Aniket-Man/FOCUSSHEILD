package com.example.feature.rewards.domain

import androidx.compose.ui.graphics.Color

enum class BadgeIconType {
    TROPHY,
    MEDAL,
    STAR,
    CROWN,
    DIAMOND,
    SHIELD,
    FLAME,
    LIGHTNING,
    ROCKET
}

enum class BadgeTier(val label: String, val tierColorHex: Long) {
    NOVICE("Novice Tier", 0xFF60A5FA),
    BRONZE("Bronze Tier", 0xFFCD7F32),
    SILVER("Silver Tier", 0xFF94A3B8),
    GOLD("Gold Master Tier", 0xFFF59E0B),
    EMERALD("Emerald Tier", 0xFF10B981),
    AMETHYST("Titan Tier", 0xFF8B5CF6),
    RUBY("Sovereign Tier", 0xFFF43F5E),
    DIAMOND("Grandmaster Tier", 0xFF06B6D4),
    SOLAR("Century Legend", 0xFFEAB308),
    CELESTIAL("Apex Celestial", 0xFF6366F1),
    MYTHIC("Mythic Immortal", 0xFFEC4899)
}

enum class BadgeShapeType {
    CIRCLE_RING,
    HEXAGON_SHIELD,
    OCTAGON_CREST,
    DIAMOND_PRISM,
    STAR_BURST,
    CROWN_BANNER
}

data class RewardBadge(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val requiredDurationMillis: Long,
    val iconType: BadgeIconType,
    val badgeCategory: String,
    val tier: BadgeTier,
    val shapeType: BadgeShapeType,
    val primaryColorHex: Long,
    val accentColorHex: Long,
    val secondaryGlowHex: Long = primaryColorHex,
    val quote: String = "Distraction conquered. Focus mastered."
) {
    val requiredHoursFloat: Float
        get() = requiredDurationMillis.toFloat() / (1000f * 60f * 60f)

    fun isUnlocked(totalLifetimeStudyMillis: Long): Boolean {
        return totalLifetimeStudyMillis >= requiredDurationMillis
    }

    fun getProgressRatio(totalLifetimeStudyMillis: Long): Float {
        if (requiredDurationMillis <= 0) return 1f
        return (totalLifetimeStudyMillis.toFloat() / requiredDurationMillis.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        val ID_2_HOURS_STUDY = "2_HOURS_STUDY"

        val ALL_BADGES = listOf(
            RewardBadge(
                id = "30_MINS_STUDY",
                title = "30 Mins Focus Spark",
                subtitle = "First Focus Step",
                description = "Ignited your study engine with 30 focused minutes. Every master starts here!",
                requiredDurationMillis = 30 * 60 * 1000L,
                iconType = BadgeIconType.STAR,
                badgeCategory = "Beginner Milestone",
                tier = BadgeTier.NOVICE,
                shapeType = BadgeShapeType.STAR_BURST,
                primaryColorHex = 0xFF0284C7,
                accentColorHex = 0xFF38BDF8,
                secondaryGlowHex = 0xFF7DD3FC,
                quote = "A journey of a thousand miles begins with a single focus session."
            ),
            RewardBadge(
                id = "1_HOUR_STUDY",
                title = "1 Hour Deep Diver",
                subtitle = "Bronze Focus Ignition",
                description = "Completed 1 hour of undisturbed study. Your flow state is established!",
                requiredDurationMillis = 60 * 60 * 1000L,
                iconType = BadgeIconType.ROCKET,
                badgeCategory = "Beginner Milestone",
                tier = BadgeTier.BRONZE,
                shapeType = BadgeShapeType.CIRCLE_RING,
                primaryColorHex = 0xFFD97706,
                accentColorHex = 0xFFFBBF24,
                secondaryGlowHex = 0xFFFDE68A,
                quote = "Small daily disciplined hours build unbreakable confidence."
            ),
            RewardBadge(
                id = ID_2_HOURS_STUDY,
                title = "2 Hours Study Champion",
                subtitle = "Gold Lifetime Trophy",
                description = "Unstoppable! Completed 2 hours of lifetime study. Dedication unlocked!",
                requiredDurationMillis = 2 * 60 * 60 * 1000L,
                iconType = BadgeIconType.TROPHY,
                badgeCategory = "Gold Lifetime Trophy",
                tier = BadgeTier.GOLD,
                shapeType = BadgeShapeType.CROWN_BANNER,
                primaryColorHex = 0xFFF59E0B,
                accentColorHex = 0xFFFCD34D,
                secondaryGlowHex = 0xFFFEF3C7,
                quote = "Champions are made in the quiet hours when no one is watching."
            ),
            RewardBadge(
                id = "5_HOURS_STUDY",
                title = "5 Hours Dedicated Scholar",
                subtitle = "Emerald Scholar Shield",
                description = "Outstanding perseverance! Completed 5 total hours of deep study and exam prep.",
                requiredDurationMillis = 5 * 60 * 60 * 1000L,
                iconType = BadgeIconType.SHIELD,
                badgeCategory = "Scholar Milestone",
                tier = BadgeTier.EMERALD,
                shapeType = BadgeShapeType.HEXAGON_SHIELD,
                primaryColorHex = 0xFF10B981,
                accentColorHex = 0xFF34D399,
                secondaryGlowHex = 0xFFA7F3D0,
                quote = "Deep focus turns effort into pure academic brilliance."
            ),
            RewardBadge(
                id = "10_HOURS_STUDY",
                title = "10 Hours Focus Titan",
                subtitle = "Amethyst Titan Crest",
                description = "10 hours of distraction-free mastery! You have built true cognitive stamina.",
                requiredDurationMillis = 10 * 60 * 60 * 1000L,
                iconType = BadgeIconType.LIGHTNING,
                badgeCategory = "Titan Milestone",
                tier = BadgeTier.AMETHYST,
                shapeType = BadgeShapeType.OCTAGON_CREST,
                primaryColorHex = 0xFF8B5CF6,
                accentColorHex = 0xFFC084FC,
                secondaryGlowHex = 0xFFE9D5FF,
                quote = "Cognitive stamina is the superpower of the modern scholar."
            ),
            RewardBadge(
                id = "20_HOURS_STUDY",
                title = "20 Hours Elite Strategist",
                subtitle = "Ruby Master Medal",
                description = "20 hours of focused dedication! You are performing at an elite academic level.",
                requiredDurationMillis = 20 * 60 * 60 * 1000L,
                iconType = BadgeIconType.MEDAL,
                badgeCategory = "Elite Master",
                tier = BadgeTier.RUBY,
                shapeType = BadgeShapeType.CIRCLE_RING,
                primaryColorHex = 0xFFF43F5E,
                accentColorHex = 0xFFFB7185,
                secondaryGlowHex = 0xFFFFE4E6,
                quote = "Discipline is choosing between what you want now and what you want most."
            ),
            RewardBadge(
                id = "50_HOURS_STUDY",
                title = "50 Hours Grandmaster Scholar",
                subtitle = "Cyan Quantum Diamond",
                description = "50 hours of lifetime focus! Top 1% study discipline and exam prep mastery achieved.",
                requiredDurationMillis = 50 * 60 * 60 * 1000L,
                iconType = BadgeIconType.DIAMOND,
                badgeCategory = "Grandmaster Diamond",
                tier = BadgeTier.DIAMOND,
                shapeType = BadgeShapeType.DIAMOND_PRISM,
                primaryColorHex = 0xFF06B6D4,
                accentColorHex = 0xFF22D3EE,
                secondaryGlowHex = 0xFFCFFAFE,
                quote = "Pressure transforms coal into unbreakable diamonds."
            ),
            RewardBadge(
                id = "100_HOURS_STUDY",
                title = "100 Hours Century Legend",
                subtitle = "Solar Century Crown",
                description = "100 hours of extraordinary focus! You have unlocked true mastery in study perseverance.",
                requiredDurationMillis = 100 * 60 * 60 * 1000L,
                iconType = BadgeIconType.CROWN,
                badgeCategory = "Century Legend",
                tier = BadgeTier.SOLAR,
                shapeType = BadgeShapeType.CROWN_BANNER,
                primaryColorHex = 0xFFEAB308,
                accentColorHex = 0xFFFDE047,
                secondaryGlowHex = 0xFFFEF9C3,
                quote = "One hundred hours of quiet mastery outshines a thousand words."
            ),
            RewardBadge(
                id = "200_HOURS_STUDY",
                title = "200 Hours Master of Discipline",
                subtitle = "Neon Cyber Aegis",
                description = "200 hours accumulated! Unmatched mental grit and academic dominance.",
                requiredDurationMillis = 200 * 60 * 60 * 1000L,
                iconType = BadgeIconType.SHIELD,
                badgeCategory = "Discipline Master",
                tier = BadgeTier.EMERALD,
                shapeType = BadgeShapeType.HEXAGON_SHIELD,
                primaryColorHex = 0xFF059669,
                accentColorHex = 0xFF6EE7B7,
                secondaryGlowHex = 0xFFD1FAE5,
                quote = "Unwavering focus turns ambition into inevitable success."
            ),
            RewardBadge(
                id = "500_HOURS_STUDY",
                title = "500 Hours Apex Mind",
                subtitle = "Prism Celestial Starlight",
                description = "500 hours of deep work! You belong to the rarest tier of focused achievers.",
                requiredDurationMillis = 500 * 60 * 60 * 1000L,
                iconType = BadgeIconType.DIAMOND,
                badgeCategory = "Apex Achiever",
                tier = BadgeTier.CELESTIAL,
                shapeType = BadgeShapeType.DIAMOND_PRISM,
                primaryColorHex = 0xFF6366F1,
                accentColorHex = 0xFFA5B4FC,
                secondaryGlowHex = 0xFFE0E7FF,
                quote = "The mind, once expanded to deep focus, never returns to distraction."
            ),
            RewardBadge(
                id = "1000_HOURS_STUDY",
                title = "1000 Hours Ultimate Immortal",
                subtitle = "Cosmic Mythic Crown",
                description = "1000 hours of focus mastery! The absolute pinnacle of academic dedication.",
                requiredDurationMillis = 1000 * 60 * 60 * 1000L,
                iconType = BadgeIconType.CROWN,
                badgeCategory = "Ultimate Immortal",
                tier = BadgeTier.MYTHIC,
                shapeType = BadgeShapeType.CROWN_BANNER,
                primaryColorHex = 0xFFEC4899,
                accentColorHex = 0xFFF472B6,
                secondaryGlowHex = 0xFFFCE7F3,
                quote = "Immortal dedication. Unstoppable mastery. Legacy achieved."
            )
        )
    }
}


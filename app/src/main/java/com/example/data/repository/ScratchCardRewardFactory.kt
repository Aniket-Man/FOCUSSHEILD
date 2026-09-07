package com.example.data.repository

import com.example.data.local.entity.ScratchCardEntity
import com.example.feature.rewards.domain.RewardBadge

/**
 * Generates the reward content revealed by a scratch card after an eligible
 * completed focus session. Prioritizes session milestones and active streaks,
 * then falls back to badge progress and motivational quotes.
 */
object ScratchCardRewardFactory {

    private data class RewardContent(
        val type: String,
        val emoji: String,
        val title: String,
        val message: String
    )

    private val QUOTES = listOf(
        "Small daily improvements are the key to staggering long-term results.",
        "The expert in anything was once a beginner. Keep going.",
        "Discipline is choosing between what you want now and what you want most.",
        "Your future self is watching you right now through memories.",
        "One session at a time. That's how toppers are built.",
        "Focus is the new IQ. And you're training it daily.",
        "The pain of discipline is temporary. The pain of regret lasts forever.",
        "Consistency beats intensity. Show up again tomorrow."
    )

    /**
     * Builds a scratch card reward based on the session and lifetime context.
     *
     * @param studyMinutes Pure study minutes of the just-completed session.
     * @param currentStreak Current discipline streak in days.
     * @param allTimeStudyMillis Lifetime pure study time for badge progress.
     */
    fun generate(
        sessionId: String,
        subject: String,
        studyMinutes: Int,
        currentStreak: Int,
        allTimeStudyMillis: Long
    ): ScratchCardEntity {
        val content = pickContent(studyMinutes, subject, currentStreak, allTimeStudyMillis)

        return ScratchCardEntity(
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            rewardType = content.type,
            rewardEmoji = content.emoji,
            rewardTitle = content.title,
            rewardMessage = content.message,
            studyMinutes = studyMinutes,
            isRevealed = false
        )
    }

    private fun pickContent(
        studyMinutes: Int,
        subject: String,
        currentStreak: Int,
        allTimeStudyMillis: Long
    ): RewardContent {
        return when {
            studyMinutes >= 60 -> RewardContent(
                type = ScratchCardRewardTypes.SESSION_MILESTONE,
                emoji = "🏆",
                title = "Power Session!",
                message = "You just deep-focused for $studyMinutes minutes of $subject. Elite consistency — keep this rhythm going!"
            )

            currentStreak >= 3 -> RewardContent(
                type = ScratchCardRewardTypes.STREAK_FIRE,
                emoji = "🔥",
                title = "$currentStreak-Day Streak!",
                message = if (currentStreak >= 21) {
                    "Your discipline is blazing — the habit is officially locked in!"
                } else {
                    "Your discipline is blazing. ${21 - currentStreak} more days and the habit is fully locked in!"
                }
            )

            else -> {
                val nextBadge = RewardBadge.ALL_BADGES
                    .filter { !it.isUnlocked(allTimeStudyMillis) }
                    .minByOrNull { it.requiredDurationMillis }

                if (nextBadge != null) {
                    val progressPercent = (nextBadge.getProgressRatio(allTimeStudyMillis) * 100).toInt()
                    val hoursRemaining = nextBadge.requiredHoursFloat - (allTimeStudyMillis / 3_600_000f)
                    RewardContent(
                        type = ScratchCardRewardTypes.BADGE_PROGRESS,
                        emoji = "🎖️",
                        title = "${progressPercent}% to a new badge!",
                        message = "You're closing in on \"${nextBadge.title}\" — about ${hoursRemaining.toInt()} more focused hours to unlock it. You've got this!"
                    )
                } else {
                    RewardContent(
                        type = ScratchCardRewardTypes.QUOTE,
                        emoji = "✨",
                        title = "Legend Status!",
                        message = "You've unlocked every badge in the collection. Today's $studyMinutes minutes of $subject were pure mastery. ${QUOTES.random()}"
                    )
                }
            }
        }
    }
}

object ScratchCardRewardTypes {
    const val STREAK_FIRE = "STREAK_FIRE"
    const val SESSION_MILESTONE = "SESSION_MILESTONE"
    const val BADGE_PROGRESS = "BADGE_PROGRESS"
    const val QUOTE = "QUOTE"
}

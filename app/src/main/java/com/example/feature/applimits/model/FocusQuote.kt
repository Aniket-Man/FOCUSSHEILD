package com.example.feature.applimits.model

/**
 * Category of inspirational/discipline quote.
 */
enum class QuoteCategory(val displayName: String) {
    FOCUS("Focus"),
    DISCIPLINE("Discipline"),
    TIME("Time Value"),
    EXAMS("Exam Mastery"),
    STUDY("Deep Study"),
    CONSISTENCY("Consistency"),
    MOTIVATION("Motivation")
}

/**
 * Local offline quote data model.
 */
data class FocusQuote(
    val id: Int,
    val text: String,
    val author: String,
    val category: QuoteCategory
)

/**
 * Repository of 100+ curated local/offline quotes for the App Limit blocker and focus shields.
 */
object FocusQuotesProvider {

    private var lastIndex: Int = -1

    val quotes: List<FocusQuote> get() = com.example.data.repository.AllStudyQuotes.FOCUS_QUOTES

    /**
     * Retrieves a quote without immediate repetition.
     */
    @Synchronized
    fun getNextQuote(seed: Long = System.currentTimeMillis()): FocusQuote {
        if (quotes.isEmpty()) {
            return FocusQuote(0, "Protect your time. Protect your future.", "FocusShield", QuoteCategory.FOCUS)
        }
        val size = quotes.size
        var nextIdx = ((seed % size).toInt() + (0..size).random()) % size
        if (nextIdx == lastIndex) {
            nextIdx = (nextIdx + 1) % size
        }
        lastIndex = nextIdx
        return quotes[nextIdx]
    }
}

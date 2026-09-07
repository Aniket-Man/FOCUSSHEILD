package com.zenlock.focusguard.domain.model

/**
 * Result of YouTube content classification.
 */
enum class ContentVerdict {
    ALLOW,      // Educational content - let it through
    BLOCK,      // Distracting content - block it
    UNKNOWN     // No matching keywords - defer to default setting
}

/**
 * Detailed classification result with reasoning.
 */
data class ClassificationResult(
    val verdict: ContentVerdict,
    val reason: String,
    val matchedKeyword: String? = null,
    val isShorts: Boolean = false
)

package com.example.feature.blocker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Floating-window protection overlay.
 *
 * This intentionally reuses the same visual component as the split-screen blocker so both
 * anti-multitasking protections have identical UI and behavior. Only the block decision differs.
 */
@Composable
fun FloatingWindowBlockOverlayScreen(
    blockedAppName: String,
    blockedPackageName: String? = null,
    onReturnToSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    BlockOverlayScreen(
        blockedAppName = blockedAppName,
        blockedPackageName = blockedPackageName,
        blockDecision = "BLOCK_FLOATING_WINDOW",
        onReturnToSession = onReturnToSession,
        modifier = modifier
    )
}

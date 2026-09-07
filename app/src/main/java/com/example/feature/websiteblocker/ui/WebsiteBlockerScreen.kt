package com.example.feature.websiteblocker.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteBlockerScreen(
    viewModel: WebsiteBlockerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    WebsiteBlockerSheetContent(
        onDismiss = onNavigateBack,
        modifier = modifier.fillMaxSize(),
        viewModel = viewModel
    )
}

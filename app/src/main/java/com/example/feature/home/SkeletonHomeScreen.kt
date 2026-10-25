package com.example.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusColors
import com.example.core.ui.SkeletonBox

@Composable
internal fun SkeletonHomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(FocusColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SkeletonBox(modifier = Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 16.dp)) }
            items(5) {
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 16.dp))
            }
        }
    }
}

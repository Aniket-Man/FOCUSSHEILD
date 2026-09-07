package com.example.feature.blocker

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private val BlockedBadgeOrange = Color(0xFFF5822B)

/**
 * Full-screen focus shield shown over a distracting app during a study session.
 *
 * Deliberately sparse: the blocked app's own icon, one sentence naming what was blocked, and a
 * single way forward. A busy screen full of options invites negotiation with the block — the point
 * is to make returning to focus the path of least resistance.
 */
@Composable
fun BlockOverlayScreen(
    blockedAppName: String,
    onReturnToSession: () -> Unit,
    blockDecision: String = "BLOCK",
    channelName: String? = null,
    videoTitle: String? = null,
    blockedPackageName: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var appIcon by remember(blockedPackageName) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(blockedPackageName) {
        appIcon = blockedPackageName
            ?.takeIf { it.isNotBlank() }
            ?.let {
                try {
                    context.packageManager.getApplicationIcon(it)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                } catch (_: Exception) {
                    null
                }
            }
    }

    val headline = when (blockDecision) {
        "BLOCK_SHORTS" -> "Shorts are blocked during focus"
        "BLOCK_UNAPPROVED_CHANNEL" ->
            if (!channelName.isNullOrBlank()) "$channelName is blocked during focus"
            else "This channel is blocked during focus"
        "BLOCK_UNKNOWN_YOUTUBE_CONTENT" -> "This video is blocked during focus"
        "BLOCK_UNINSTALL" -> "Uninstalling is blocked during focus"
        "BLOCK_SPLIT_SCREEN" -> "Split screen is blocked during focus"
        "BLOCK_FLOATING_WINDOW" -> "Floating windows are blocked during focus"
        else -> "$blockedAppName is blocked during focus"
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("block_overlay_screen"),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Brand mark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.HourglassEmpty,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "FocusShield",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Blocked app icon with a "no entry" badge
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1A1A1D)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = appIcon
                    if (icon != null) {
                        Image(
                            bitmap = icon.toBitmap(192, 192).asImageBitmap(),
                            contentDescription = blockedAppName,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BlockedBadgeOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = "Blocked",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 38.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("block_overlay_headline")
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onReturnToSession,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .testTag("stay_in_focus_button")
            ) {
                Text(
                    text = "Stay in Focus",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 19.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

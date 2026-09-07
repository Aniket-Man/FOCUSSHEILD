package com.example.feature.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.LocalFocusColors
import com.example.core.design.FocusSpacing
import com.example.core.permission.AwaitPermissionGrantEffect
import com.example.core.permission.OnboardingPermission
import com.example.core.ui.FocusLottieAnimation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modern Onboarding Experience (Inspired by Opal, One Sec & Modern Focus Utilities).
 *
 * Page 1: Dynamic Hero with the phone's actual FocusShield launcher icon, pulsing glow, and interactive goal selector.
 * Page 2: Multi-Platform Distraction Shielding with official YouTube, YouTube Shorts, Instagram Reels & Facebook visuals.
 * Page 3: 100% Local & Private System Setup with auto-return detection, live progress, and strict entry gating.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // 0 aurora hero · 1 distraction shield · 2 permissions · 3 commitment
    val pagerState = rememberPagerState(pageCount = { 4 })

    // Live grant state for every permission in the onboarding flow.
    var grantedStates by remember {
        mutableStateOf(OnboardingPermission.flow.associateWith { it.isGranted(context) })
    }

    // The permission the user has just been sent to Settings for, if any.
    var awaitingPermission by remember { mutableStateOf<OnboardingPermission?>(null) }

    fun refreshPermissions() {
        grantedStates = OnboardingPermission.flow.associateWith { it.isGranted(context) }
    }

    // Pull the app back to the front the moment the awaited permission flips to granted, so the
    // user lands on the next step instead of being stranded in system Settings.
    AwaitPermissionGrantEffect(
        awaiting = awaitingPermission,
        onSettled = {
            awaitingPermission = null
            refreshPermissions()
        }
    )

    // Refresh whenever the user returns from Android Settings by any route (including Back).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Polling ticker on the permissions page so Settings toggles show up without a Back press.
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 2) {
            while (true) {
                delay(500)
                refreshPermissions()
            }
        }
    }

    val activePermission = OnboardingPermission.flow.firstOrNull { grantedStates[it] != true }
    val allMandatoryGranted = OnboardingPermission.flow
        .filter { it.isMandatory }
        .all { grantedStates[it] == true }

    // Page roles. Pages 0 (aurora hero) and 3 (commitment) are immersive and own their full
    // layout including action buttons; the permissions page (2) likewise owns its chrome. Only the
    // distraction-shield page (1) uses the shared top bar + dots + Next bar below.
    val page = pagerState.currentPage
    val usesSharedChrome = page == 1

    fun goToPage(target: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(target) }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        color = FocusColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (usesSharedChrome) Modifier.statusBarsPadding() else Modifier)
                .padding(bottom = if (usesSharedChrome) 16.dp else 0.dp)
        ) {
            if (usesSharedChrome) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.base, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_focus_shield_app_icon),
                                contentDescription = "FocusShield App Logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = "FocusShield",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = FocusColors.TextPrimary,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    TextButton(
                        onClick = { goToPage(2) },
                        modifier = Modifier.testTag("onboarding_skip_to_perms_button")
                    ) {
                        Text(
                            text = "Permissions",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> OnboardingWelcomePage(onGetStarted = { goToPage(1) })
                    1 -> OnboardingDistractionShieldPage()
                    2 -> OnboardingPermissionsPage(
                        grantedStates = grantedStates,
                        activePermission = activePermission,
                        allMandatoryGranted = allMandatoryGranted,
                        onAllow = { permission ->
                            awaitingPermission = permission
                            permission.request(context)
                        },
                        onContinue = { goToPage(3) },
                        onSkip = { goToPage(3) }
                    )
                    3 -> OnboardingCommitmentPage(onCommitted = onCompleteOnboarding)
                }
            }

            // Shared bottom bar (distraction-shield page only)
            if (usesSharedChrome) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.base),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { goToPage(2) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FocusColors.Primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("onboarding_next_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Set Up Permissions",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private val OnboardingAuroraGreen = Color(0xFF7BE04B)
private val CommitYellow = Color(0xFFF5C518)

/**
 * Final page: the user physically commits to their intention by pressing and holding a button.
 * The deliberate friction — you must hold, not tap — is the point: it turns a throwaway "next"
 * into a small ritual, which is what makes the promise stick.
 */
@Composable
private fun OnboardingCommitmentPage(onCommitted: () -> Unit) {
    val holdDurationMillis = 1500
    var isHolding by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }

    val fillProgress by animateFloatAsState(
        targetValue = if (isHolding || committed) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isHolding) holdDurationMillis else 260,
            easing = LinearEasing
        ),
        label = "commitFill"
    )

    // Fire onCommitted once the hold-driven fill actually reaches the top.
    LaunchedEffect(fillProgress, isHolding) {
        if (isHolding && fillProgress >= 1f && !committed) {
            committed = true
            onCommitted()
        }
    }

    val infinite = rememberInfiniteTransition(label = "commitPulse")
    val idlePulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF171307), Color(0xFF050505))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(96.dp))

            Text(
                text = "I will use FocusShield to",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "focus sincerely on my studies,\nand kill my phone addiction.",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // Press-and-hold commit dial: a ring fills as it is held.
            Box(
                modifier = Modifier.size(150.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(if (isHolding) 1f else idlePulse)
                ) {
                    // Track
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx())
                    )
                    // Progress arc
                    drawArc(
                        color = CommitYellow,
                        startAngle = -90f,
                        sweepAngle = 360f * fillProgress,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 10.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .scale(if (isHolding) 1f else idlePulse)
                        .clip(CircleShape)
                        .background(CommitYellow)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown()
                                    isHolding = true
                                    // Wait until the finger lifts / leaves.
                                    waitForUpOrCancellation()
                                    if (!committed) isHolding = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassEmpty,
                        contentDescription = null,
                        tint = Color(0xFF1A1503),
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = if (isHolding) "Keep holding…" else "Tap and hold to commit",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp
                )
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

/**
 * Page 1: Full-bleed aurora hero — "#1 Study App for students to focus" — with social-proof
 * laurels along the bottom. The dark aurora photo sits behind an edge-to-edge scrim so white
 * headline text stays legible regardless of where the aurora happens to be bright.
 */
@Composable
private fun OnboardingWelcomePage(onGetStarted: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Purple-first visual language: the onboarding starts with the same brand family used
        // throughout the app instead of switching to an unrelated green/aurora palette.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6D28D9),
                            Color(0xFF8B5CF6),
                            Color(0xFF4C1D95)
                        )
                    )
                )
        )

        // Subtle native Compose decoration; no screenshot is used as a background.
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-70).dp, y = 80.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
        )
        Box(
            modifier = Modifier
                .size(360.dp)
                .offset(x = 180.dp, y = 430.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_focus_shield_app_icon),
                    contentDescription = "FocusShield",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "FocusShield",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 40.sp
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Block distractions.\nProtect your study time.",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    letterSpacing = (-0.6).sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "A focused workspace for deeper, distraction-free study.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 16.sp,
                    lineHeight = 23.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(if (index == 0) 24.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == 0) Color.White else Color.White.copy(alpha = 0.28f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF6D28D9)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("onboarding_get_started_button")
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * A single laurel-wreathed social-proof statistic, e.g. 🌿 "Trusted by / **2M** / students" 🌿.
 * The two laurel branches are the same vector mirrored left and right of the text.
 */
@Composable
private fun LaurelStat(
    topLabel: String,
    highlight: String,
    bottomLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_laurel_branch),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .height(66.dp)
                .width(22.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = topLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
            )
            Text(
                text = highlight,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
            )
            Text(
                text = bottomLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_laurel_branch),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .height(66.dp)
                .width(22.dp)
                .scale(scaleX = -1f, scaleY = 1f)
        )
    }
}

/**
 * Page 2: Multi-Platform Distraction Blocker with YouTube, Shorts, Reels & Facebook Brand Assets
 */
@Composable
private fun OnboardingDistractionShieldPage() {
    var ytStudyChecked by remember { mutableStateOf(true) }
    var shortsChecked by remember { mutableStateOf(true) }
    var reelsChecked by remember { mutableStateOf(true) }
    var fbChecked by remember { mutableStateOf(true) }

    val purple = FocusColors.Primary
    val purpleLight = FocusColors.PrimaryLight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Hero: the generated reference is a single flowing layout, not a grid of cards.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = if (LocalFocusColors.current.isDark) {
                Color(0xFF12101C)
            } else {
                Color(0xFFF8F4FF)
            },
            border = BorderStroke(1.dp, purple.copy(alpha = 0.28f))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "PROTECT YOUR FOCUS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = purpleLight,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enable Smart\nProtections",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        letterSpacing = (-0.4).sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose the protections you want FocusShield to manage.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(purple))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(purple.copy(alpha = 0.28f)))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(purple.copy(alpha = 0.18f)))
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        ProtectionRow(
            drawableRes = R.drawable.ic_youtube,
            eyebrow = "YOUTUBE",
            title = "Study Mode",
            description = "Keep approved lectures and study playlists while hiding distracting feeds.",
            isChecked = ytStudyChecked,
            onCheckedChange = { ytStudyChecked = it },
            accent = purple
        )
        ProtectionRow(
            drawableRes = R.drawable.ic_youtube_shorts,
            eyebrow = "YOUTUBE",
            title = "Shorts Auto-Dismiss",
            description = "Detect Shorts and automatically leave the endless vertical video feed.",
            isChecked = shortsChecked,
            onCheckedChange = { shortsChecked = it },
            accent = purple
        )
        ProtectionRow(
            drawableRes = R.drawable.ic_instagram_reels,
            eyebrow = "INSTAGRAM",
            title = "Reels Blocker",
            description = "Block Reels while keeping direct messaging and normal communication available.",
            isChecked = reelsChecked,
            onCheckedChange = { reelsChecked = it },
            accent = purple
        )
        ProtectionRow(
            drawableRes = R.drawable.ic_facebook,
            eyebrow = "FACEBOOK",
            title = "Feed & Reels Shield",
            description = "Block continuous video feeds and recommended Reels during study time.",
            isChecked = fbChecked,
            onCheckedChange = { fbChecked = it },
            accent = purple
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = FocusColors.EmeraldLight.copy(alpha = if (LocalFocusColors.current.isDark) 0.45f else 0.75f),
            border = BorderStroke(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FocusColors.EmeraldSuccess.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FocusColors.EmeraldSuccess,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "100% On-Device Detection",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Detection stays on your device using the Accessibility view hierarchy.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun ProtectionRow(
    drawableRes: Int,
    eyebrow: String,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = FocusColors.SurfaceVariant,
        border = BorderStroke(1.dp, accent.copy(alpha = if (isChecked) 0.38f else 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 21.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = FocusColors.TextMuted,
                    uncheckedTrackColor = FocusColors.SurfaceSubtle,
                    uncheckedBorderColor = FocusColors.CardBorder
                )
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

/**
 * Interactive Platform Card Component for YouTube, Shorts, Reels, and Facebook
 */
@Composable
private fun InteractivePlatformShieldCard(
    drawableRes: Int,
    badgeTitle: String,
    description: String,
    brandColor: Color,
    containerBg: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161820),
        border = BorderStroke(1.dp, if (isChecked) brandColor.copy(alpha = 0.35f) else FocusColors.CardBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerBg)
                    .border(1.dp, brandColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = badgeTitle,
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = badgeTitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 14.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = brandColor,
                    uncheckedThumbColor = Color(0xFF888888),
                    uncheckedTrackColor = Color(0xFF222530)
                )
            )
        }
    }
}

/** Marketing copy, not live telemetry. Change the numbers here, in one place. */
private const val FOCUSING_NOW_COUNT = 4716
private const val TRUST_LINE = "Trusted by 2M+ students ❤️"

private val AccentGreen = Color(0xFF6FD13B)
private val BubbleGrey = Color(0xFF2A2C2E)
private val WhyPillGreen = Color(0xFF13260E)

/**
 * Page 3: sequential permission flow.
 *
 * Only the first ungranted permission is expanded — title, one line of plain-language rationale and
 * an Allow button. Everything after it stays collapsed and dimmed, and everything already granted
 * collapses behind a checkmark, so there is exactly one thing to do on screen at any moment.
 */
@Composable
private fun OnboardingPermissionsPage(
    grantedStates: Map<OnboardingPermission, Boolean>,
    activePermission: OnboardingPermission?,
    allMandatoryGranted: Boolean,
    onAllow: (OnboardingPermission) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    var whyDialogFor by remember { mutableStateOf<OnboardingPermission?>(null) }

    val flow = OnboardingPermission.flow
    val grantedCount = flow.count { grantedStates[it] == true }
    val progress by animateFloatAsState(
        targetValue = grantedCount.toFloat() / flow.size.toFloat(),
        label = "permissionProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("onboarding_permissions_page")
    ) {
        // Soft green floor glow, matching the reference design.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, AccentGreen.copy(alpha = 0.13f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Overall setup progress
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.coerceIn(0.04f, 1f))
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            MascotSpeechBubble(
                isComplete = allMandatoryGranted,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(52.dp))

            flow.forEachIndexed { index, permission ->
                PermissionFlowRow(
                    permission = permission,
                    isGranted = grantedStates[permission] == true,
                    isActive = permission == activePermission,
                    onAllow = { onAllow(permission) }
                )
                if (index != flow.lastIndex) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.White.copy(alpha = 0.09f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (allMandatoryGranted) {
                Button(
                    onClick = onContinue,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboarding_finish_button")
                ) {
                    Text(
                        text = "Start focusing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(WhyPillGreen)
                        .clickable { whyDialogFor = activePermission ?: OnboardingPermission.flow.first() }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                        .testTag("onboarding_why_permission_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(27.dp)
                            .clip(CircleShape)
                            .background(AccentGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "?",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFF0A1A06),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Why should I give this permission?",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = AccentGreen,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = TRUST_LINE,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 13.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_skip_button")
            ) {
                Text(
                    text = if (allMandatoryGranted) "Not now" else "Continue without blocking",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    whyDialogFor?.let { permission ->
        AlertDialog(
            onDismissRequest = { whyDialogFor = null },
            containerColor = Color(0xFF15171A),
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PrivacyTip,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = permission.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = permission.whyItMatters,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.72f),
                            lineHeight = 20.sp
                        )
                    )
                    Text(
                        text = "FocusShield runs fully offline. No servers, no telemetry, nothing leaves your phone.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AccentGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        whyDialogFor = null
                        onAllow(permission)
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(text = "Allow", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { whyDialogFor = null }) {
                    Text(text = "Close", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }
}

/**
 * Mascot avatar plus the speech bubble that carries the ask.
 */
@Composable
private fun MascotSpeechBubble(
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    val message = if (isComplete) {
        buildAnnotatedString {
            append("You're all set — ")
            withStyle(SpanStyle(color = AccentGreen, fontWeight = FontWeight.Bold)) {
                append("time to out-focus everyone else!")
            }
        }
    } else {
        buildAnnotatedString {
            append("Almost there, allow these permissions to ")
            withStyle(SpanStyle(color = AccentGreen, fontWeight = FontWeight.Bold)) {
                append("focus alongside $FOCUSING_NOW_COUNT people now!")
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painter = painterResource(R.drawable.ic_focus_mascot),
                contentDescription = "FocusShield mascot",
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            // Rotated square peeking out behind the bubble forms the speech tail.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 4.dp, y = 34.dp)
                    .size(14.dp)
                    .rotate(45f)
                    .background(BubbleGrey)
            )
            Box(
                modifier = Modifier
                    .padding(start = 9.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BubbleGrey)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

/**
 * One row of the permission flow. Collapsed and dimmed unless it is the user's current step.
 */
@Composable
private fun PermissionFlowRow(
    permission: OnboardingPermission,
    isGranted: Boolean,
    isActive: Boolean,
    onAllow: () -> Unit
) {
    val titleColor = when {
        isActive -> Color.White
        isGranted -> Color.White.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.26f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
            .testTag("permission_row_${permission.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permission.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
            if (isActive) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = permission.rationale,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 15.sp
                    )
                )
            }
        }

        when {
            isGranted -> Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = AccentGreen,
                modifier = Modifier.size(24.dp)
            )

            isActive -> {
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onAllow,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 26.dp,
                        vertical = 0.dp
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("permission_allow_${permission.name}")
                ) {
                    Text(
                        text = "Allow",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                    )
                }
            }
        }
    }
}

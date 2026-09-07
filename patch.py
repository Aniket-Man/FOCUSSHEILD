with open("app/src/main/java/com/example/core/ui/QuickActionCard.kt", "r") as f:
    content = f.read()

target = """    val cardShape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 0.dp else 2.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(cardShape)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, cardShape)"""

replacement = """    val cardShape = RoundedCornerShape(18.dp)
    
    val glassBg = if (isDark) {
        FocusColors.Surface.copy(alpha = 0.48f)
    } else {
        FocusColors.Surface.copy(alpha = 0.72f)
    }
    val glassBorderBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f),
                FocusColors.CardBorder.copy(alpha = 0.45f)
            )
        } else {
            listOf(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.80f),
                FocusColors.CardBorder.copy(alpha = 0.35f)
            )
        }
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 0.dp else 2.dp,
                shape = cardShape,
                ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.03f),
                spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.04f)
            )
            .clip(cardShape)
            .background(glassBg)
            .border(1.dp, glassBorderBrush, cardShape)"""

if target in content:
    with open("app/src/main/java/com/example/core/ui/QuickActionCard.kt", "w") as f:
        f.write(content.replace(target, replacement))
    print("Success")
else:
    print("Failed to find target")

with open("app/src/main/java/com/example/core/ui/QuickActionCard.kt", "r") as f:
    content = f.read()

target = "val cardShape = RoundedCornerShape(18.dp)"
insertion = """val cardShape = RoundedCornerShape(18.dp)
    
    val glassBg = if (isDark) {
        FocusColors.Surface.copy(alpha = 0.48f)
    } else {
        FocusColors.Surface.copy(alpha = 0.72f)
    }
    val glassBorderBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.22f),
                FocusColors.CardBorder.copy(alpha = 0.45f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.80f),
                FocusColors.CardBorder.copy(alpha = 0.35f)
            )
        }
    )"""

if target in content:
    with open("app/src/main/java/com/example/core/ui/QuickActionCard.kt", "w") as f:
        f.write(content.replace(target, insertion, 1))
    print("Success")
else:
    print("Failed")

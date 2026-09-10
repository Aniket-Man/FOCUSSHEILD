package com.example.feature.profile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.design.FocusColors
import com.example.core.util.ProfilePhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Longest side the picked image is decoded down to before cropping. */
private const val DECODE_TARGET_PX = 2048

/** Side of the square JPEG written to disk and uploaded. */
private const val OUTPUT_SIZE_PX = 1024

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f

/** The square viewport covers this fraction of the available area, leaving room for the dim. */
private const val VIEWPORT_FRACTION = 0.96f

/** Photo editors use a dark backdrop so the white crop guide stays legible in every app theme. */
private val CropBackdrop = Color(0xFF101014)

/**
 * Full-screen, WhatsApp-style crop screen shown straight after the user picks a profile photo.
 *
 * The user pinch-zooms and pans the image behind a circular guide with everything outside the
 * circle dimmed; confirming writes a square 1024x1024 JPEG to [ProfilePhotoStorage]'s staging
 * file and hands its `file://` URI back. Nothing is committed to the saved avatar here — that
 * happens on Save — so dismissing this screen or the sheet leaves the previous avatar intact.
 */
@Composable
fun ImageCropDialog(
    sourceUri: Uri,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(sourceUri) { mutableStateOf(false) }
    var saving by remember(sourceUri) { mutableStateOf(false) }

    // Geometry published by the viewport once it has been measured. Held here rather than inside
    // the layout block so the toolbar's confirm action can read the same values.
    var viewportSidePx by remember(sourceUri) { mutableFloatStateOf(0f) }
    var coverScale by remember(sourceUri) { mutableFloatStateOf(1f) }
    var scale by remember(sourceUri) { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember(sourceUri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(sourceUri) {
        val decoded = withContext(Dispatchers.Default) { decodeForCropping(context, sourceUri) }
        if (decoded == null) failed = true else bitmap = decoded
    }

    // `coverScale` depends on both the decoded image and the measured viewport, either of which
    // can arrive second, so it is derived rather than set inside a layout callback.
    LaunchedEffect(bitmap, viewportSidePx) {
        val image = bitmap
        coverScale = if (image != null && viewportSidePx > 0f) {
            max(viewportSidePx / image.width, viewportSidePx / image.height)
        } else {
            1f
        }
        scale = MIN_SCALE
        offset = Offset.Zero
    }

    /** Keeps the image covering the viewport: the offset can never pull an edge inside it. */
    fun clampOffset(candidate: Offset, atScale: Float, image: Bitmap): Offset {
        val k = coverScale * atScale
        val maxX = max(0f, (image.width * k - viewportSidePx) / 2f)
        val maxY = max(0f, (image.height * k - viewportSidePx) / 2f)
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY)
        )
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = CropBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                CropToolbar(
                    saving = saving,
                    confirmEnabled = bitmap != null && !saving && viewportSidePx > 0f,
                    onCancel = onCancel,
                    onConfirm = {
                        val source = bitmap ?: return@CropToolbar
                        // Capture the transform as it is now: the coroutine below can resume
                        // after further gestures have moved it.
                        val capturedScale = scale
                        val capturedOffset = offset
                        saving = true
                        scope.launch {
                            val staged = cropAndStage(
                                context = context,
                                source = source,
                                viewportPx = viewportSidePx,
                                cover = coverScale,
                                scale = capturedScale,
                                offset = capturedOffset
                            )
                            saving = false
                            if (staged != null) onConfirm(staged) else failed = true
                        }
                    }
                )

                val density = LocalDensity.current
                val viewportSide = with(density) { viewportSidePx.toDp() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged {
                            viewportSidePx =
                                min(it.width, it.height).toFloat() * VIEWPORT_FRACTION
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        failed -> CropError(onCancel = onCancel)

                        bitmap == null -> CircularProgressIndicator(
                            color = FocusColors.Primary,
                            modifier = Modifier.size(38.dp)
                        )

                        else -> {
                            val image = bitmap!!
                            Box(
                                modifier = Modifier
                                    .size(viewportSide)
                                    .clipToBounds()
                                    .background(Color.Black)
                                    .pointerInput(image) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val nextScale =
                                                (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                            // Scale the offset about the viewport centre so a pinch
                                            // zooms in place instead of drifting.
                                            val zoomFactor = nextScale / scale
                                            scale = nextScale
                                            offset = clampOffset(
                                                (offset + pan) * zoomFactor,
                                                nextScale,
                                                image
                                            )
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = image.asImageBitmap(),
                                    contentDescription = "Crop preview",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        // `requiredSize`, not `size`: the parent viewport is a
                                        // square, so a plain `size` would clamp this back to a
                                        // square and `FillBounds` would then stretch the bitmap into
                                        // it — squashing every non-square photo. Overriding the
                                        // incoming constraints is the whole point; the viewport's
                                        // `clipToBounds` is what trims the overflow.
                                        .requiredSize(
                                            width = with(density) {
                                                (image.width * coverScale).toDp()
                                            },
                                            height = with(density) {
                                                (image.height * coverScale).toDp()
                                            }
                                        )
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        }
                                )

                                CropGuideOverlay()
                            }
                        }
                    }
                }

                if (bitmap != null && !failed) {
                    Text(
                        text = "Pinch to zoom · Drag to reposition",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                    )
                }
            }
        }
    }
}

/** Dims everything outside the circular guide and draws the guide ring itself. */
@Composable
private fun BoxScope.CropGuideOverlay() {
    val strokeWidth = with(LocalDensity.current) { 2.dp.toPx() }
    Canvas(modifier = Modifier.matchParentSize()) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addOval(Rect(center = centre, radius = radius))
        }
        drawPath(dim, Color.Black.copy(alpha = 0.62f))
        drawCircle(
            color = Color.White,
            radius = radius - strokeWidth / 2f,
            center = centre,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
private fun CropToolbar(
    saving: Boolean,
    confirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCancel,
            enabled = !saving,
            modifier = Modifier.testTag("image_crop_cancel_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Cancel",
                tint = Color.White
            )
        }

        Text(
            text = "Adjust Photo",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        if (saving) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = FocusColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            IconButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.testTag("image_crop_confirm_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (confirmEnabled) FocusColors.Primary
                            else FocusColors.Primary.copy(alpha = 0.4f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Confirm crop",
                        tint = FocusColors.TextOnDark,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CropError(onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Couldn't load that image.",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) {
            Text("Choose another photo", color = FocusColors.Primary, fontSize = 13.sp)
        }
    }
}

/**
 * Crops the currently visible square out of [source] and stages it as a JPEG.
 * Returns the staged `file://` URI, or null if the crop or the write failed.
 */
private suspend fun cropAndStage(
    context: Context,
    source: Bitmap,
    viewportPx: Float,
    cover: Float,
    scale: Float,
    offset: Offset
): String? {
    val cropped = withContext(Dispatchers.Default) {
        // `k` is how many source pixels each viewport pixel covers.
        val k = cover * scale
        val sourceSide = viewportPx / k
        val left = source.width / 2f - offset.x / k - sourceSide / 2f
        val top = source.height / 2f - offset.y / k - sourceSide / 2f

        val side = sourceSide.roundToInt().coerceIn(1, min(source.width, source.height))
        val leftClamped = left.roundToInt().coerceIn(0, source.width - side)
        val topClamped = top.roundToInt().coerceIn(0, source.height - side)

        try {
            Bitmap.createBitmap(source, leftClamped, topClamped, side, side)
        } catch (e: Exception) {
            null
        }
    } ?: return null

    val squared = withContext(Dispatchers.Default) {
        if (cropped.width == OUTPUT_SIZE_PX) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, OUTPUT_SIZE_PX, OUTPUT_SIZE_PX, true)
                .also { if (it !== cropped) cropped.recycle() }
        }
    }

    val staged = ProfilePhotoStorage.writeStagedCrop(context, squared)
    squared.recycle()
    return staged
}

/**
 * Two-pass downsample of the picked image, honouring its EXIF orientation so a camera photo is
 * cropped upright rather than sideways. Returns null if the image cannot be decoded.
 */
private fun decodeForCropping(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longestSide = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longestSide / (sampleSize * 2) >= DECODE_TARGET_PX) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        applyExifRotation(context, uri, decoded)
    } catch (e: Exception) {
        null
    }
}

private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val degrees = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    if (degrees == 0) return bitmap

    return try {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    } catch (e: Exception) {
        bitmap
    }
}

package com.rob.cropperlib.ui.views

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.airbnb.lottie.compose.*
import com.rob.cropperlib.R
import kotlin.math.roundToInt
import kotlin.properties.Delegates


var containerWidth by Delegates.notNull<Float>()
var containerHeight by Delegates.notNull<Float>()

var imageHeight by Delegates.notNull<Int>()
var imageWidth by Delegates.notNull<Int>()

lateinit var cropAreaBounds: Rect

val scale = mutableStateOf(1f)
val scaleToOverlayBounds = mutableStateOf(1f)
val offsetX = mutableStateOf(0f)
val offsetY = mutableStateOf(0f)
val imageCenter = mutableStateOf(Offset.Zero)

val minimalScale = mutableStateOf(0.1f)
val maximalScale = mutableStateOf(5f)

@Composable
fun CropView(
    modifier: Modifier = Modifier,
    cropBoarderColor: Color = Color.Yellow,
    backgroundColor: Color = Color.White,
    toolbarColor: Color = Color.White,
    toolbarHeight: Dp = 56.dp,
    withToolbarLeftBtn: @Composable (() -> Unit)? = null,
    withToolbarRightBtn: @Composable (() -> Unit)? = null,
    withToolbarTitle: @Composable (() -> Unit)? = null,
    bitmap: Bitmap? = null,
    maxScale: Float = 5f,
    minScale: Float = 0.1f,
    aspectRatio: Float = 3 / 4f,
    progressLabel: String = "",
    shouldShowOverlay: Boolean = false
) {
    if (bitmap != null) {
        BoxWithConstraints(
            modifier = modifier.background(backgroundColor)
        ) {
            val lifecycleOwner = LocalLifecycleOwner.current

            var croppedImage by remember {
                mutableStateOf<Bitmap?>(null)
            }


            containerWidth = with(LocalDensity.current) { maxWidth.toPx() }
            containerHeight = with(LocalDensity.current) { maxHeight.toPx() }

            val left = 40f
            val top = (containerHeight - (containerWidth - 80f) / aspectRatio) / 2
            val right = containerWidth - 40f
            val bottom = containerHeight - (containerHeight - (containerWidth - 80f) / aspectRatio) / 2

            cropAreaBounds = Rect(left, top, right, bottom)

            imageHeight = bitmap.height
            imageWidth = bitmap.width

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        minimalScale.value = minScale
                        maximalScale.value = maxScale
                        zoomToCropAreaBounds(minimalScale.value, maximalScale.value, aspectRatio)
                        moveToCenter()
                        croppedImage = cropImage(
                            bitmap,
                            cropAreaBounds,
                            offsetX.value,
                            offsetY.value,
                            scale.value
                        )
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale.value = maxOf(minScale, minOf(maxScale, scale.value * zoom))

                                offsetX.value += pan.x
                                offsetY.value += pan.y
                                if ((zoom > 1 || zoom < 1) && (scale.value >= minimalScale.value && scale.value < maxScale)) {
                                    offsetX.value *= zoom
                                    offsetY.value *= zoom
                                }
                                moveToCropAreaPosition(minScale, maxScale, aspectRatio)
                                croppedImage = cropImage(
                                    bitmap,
                                    cropAreaBounds,
                                    offsetX.value,
                                    offsetY.value,
                                    scale.value
                                )
                            }
                        },
                    onDraw = {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        val rectPath = Path().apply {
                            addRect(cropAreaBounds)
                        }
                        withTransform({
                            translate(offsetX.value, offsetY.value)
                            scale(
                                scaleX = maxOf(minScale, minOf(maxScale, scale.value)),
                                scaleY = maxOf(minScale, minOf(maxScale, scale.value))
                            )
                            imageCenter.value = this.center
                        }) {
                            drawImage(bitmap.asImageBitmap(), topLeft = cropAreaBounds.center)
                        }
                        clipPath(rectPath, clipOp = ClipOp.Difference) {
                            drawRect(
                                color = backgroundPrimaryInverseOpacity50,
                                topLeft = Offset.Zero,
                                size = Size(canvasWidth, canvasHeight)
                            )
                        }
                        drawRect(
                            color = cropBoarderColor,
                            topLeft = Offset(
                                40f,
                                (canvasHeight - (canvasWidth - 80f) * 4 / 3) / 2
                            ),
                            size = Size(canvasWidth - 80f, (canvasWidth - 80f) * 4 / 3),
                            style = Stroke(width = 4.dp.toPx())
                        )
                    })
            }

            Column(
                modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(toolbarHeight)
                        .background(toolbarColor),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (withToolbarLeftBtn != null) {
                        withToolbarLeftBtn()
                    }
                    if (withToolbarTitle != null) {
                        withToolbarTitle()
                    }
                    if (withToolbarRightBtn != null) {
                        withToolbarRightBtn()
                    }
                }
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = progressLabel,
                    fontSize = 24.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Thin
                )
            }
            if (shouldShowOverlay) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {}
                    )
                    .background(backgroundPrimaryOpacity50)) {
                    Loader(modifier = Modifier.align(Alignment.Center))
                }
            }
            if (croppedImage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .width(maxWidth / 3)
                        .aspectRatio(3 / 4f)
                        .background(Color.Red)
                ) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = croppedImage!!.asImageBitmap(),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun Loader(modifier: Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.spinner_animation_2))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    LottieAnimation(
        modifier = modifier.size(90.dp),
        composition = composition,
        progress = { progress },
    )
}

private fun moveToCropAreaPosition(minScale: Float, maxScale: Float, aspectRatio: Float) {
    if (scale.value < scaleToOverlayBounds.value) {
        zoomToCropAreaBounds(minScale, maxScale, aspectRatio)
    }
    if (offsetX.value > -cropAreaBounds.width / 2) {
        offsetX.value = -cropAreaBounds.width / 2
    }
    if (offsetX.value + imageWidth * scale.value < cropAreaBounds.width / 2) {
        offsetX.value = cropAreaBounds.width / 2 - imageWidth * scale.value
    }
    if (offsetY.value > -cropAreaBounds.height / 2) {
        offsetY.value = -cropAreaBounds.height / 2
    }
    if (offsetY.value + imageHeight * scale.value < cropAreaBounds.height / 2) {
        offsetY.value = cropAreaBounds.height / 2 - imageHeight * scale.value
    }
}

private fun zoomToCropAreaBounds(minScale: Float, maxScale: Float, aspectRatio: Float) {
    when {
        // If image inside overlay
        (imageWidth < cropAreaBounds.width && imageHeight < cropAreaBounds.height) -> {
            if (imageWidth / imageHeight.toFloat() < aspectRatio) {
                scale.value *= cropAreaBounds.width / (imageWidth * scale.value)
            } else {
                scale.value *= cropAreaBounds.height / (imageHeight * scale.value)
            }
        }
        // If image width less then overlay width
        (imageWidth < cropAreaBounds.width && imageHeight >= cropAreaBounds.height) -> {
            scale.value *= cropAreaBounds.width / (imageWidth * scale.value)
        }
        // If image height less then overlay height
        (imageWidth >= cropAreaBounds.width && imageHeight < cropAreaBounds.height) -> {
            scale.value *= cropAreaBounds.height / (imageHeight * scale.value)

        }
        // If image outside the overlay
        (imageWidth >= cropAreaBounds.width && imageHeight >= cropAreaBounds.height) -> {
            if (imageWidth / imageHeight.toFloat() < aspectRatio) {
                scale.value *= cropAreaBounds.width / (imageWidth * scale.value)
            } else {
                scale.value *= cropAreaBounds.height / (imageHeight * scale.value)
            }
        }
    }
    minimalScale.value = scale.value
    scale.value = maxOf(minScale, minOf(maxScale, scale.value))

    scaleToOverlayBounds.value = scale.value
}

private fun moveToCenter() {
    offsetX.value = -imageWidth / 2f * scale.value
    offsetY.value = -imageHeight / 2f * scale.value
}

private fun cropImage(bitmap: Bitmap, overlay: Rect, offsetX: Float, offsetY: Float, scale: Float) : Bitmap?{
    var croppedImage: Bitmap? = null
    try {
        croppedImage = Bitmap.createBitmap(
            bitmap,
            ((-offsetX - overlay.width / 2) / scale).roundToInt(),
            ((-offsetY - overlay.height / 2) / scale).roundToInt(),
            (overlay.width / scale).roundToInt(),
            (overlay.height / scale).roundToInt()
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return croppedImage
}


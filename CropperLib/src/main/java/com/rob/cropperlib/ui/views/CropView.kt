package com.rob.cropperlib.ui.views

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt
import kotlin.properties.Delegates


var containerWidth by Delegates.notNull<Float>()
var containerHeight by Delegates.notNull<Float>()

var imageHeight by Delegates.notNull<Int>()
var imageWidth by Delegates.notNull<Int>()

lateinit var cropAreaBounds: Rect

var scale by mutableStateOf(1f)
var scaleToOverlayBounds by mutableStateOf(1f)
var offsetX by mutableStateOf(0f)
var offsetY by mutableStateOf(0f)
var imageCenter by mutableStateOf(Offset.Zero)

var minScaleDef by mutableStateOf(0.1f)
var maxScaleDef by mutableStateOf(5f)

@Composable
fun CropView(
    modifier: Modifier = Modifier,
    croppedBitmap: (Bitmap?) -> Unit,
    cropBoarderColor: Color = Color.Yellow,
    backgroundColor: Color = Color.Black,
    bitmap: Bitmap? = null,
    maxScale: Float = 5f,
    minScale: Float = 0.1f,
    aspectRatio: Float = 3 / 4f
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
            val bottom =
                containerHeight - (containerHeight - (containerWidth - 80f) / aspectRatio) / 2

            cropAreaBounds = Rect(left, top, right, bottom)

            imageHeight = bitmap.height
            imageWidth = bitmap.width

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        minScaleDef = minScale
                        maxScaleDef = maxScale
                        zoomToCropAreaBounds(minScaleDef, maxScaleDef, aspectRatio)
                        moveToCenter()
                        croppedImage = cropImage(
                            bitmap,
                            cropAreaBounds,
                            offsetX,
                            offsetY,
                            scale
                        )
                        croppedBitmap(croppedImage)
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
                                scale = maxOf(minScale, minOf(maxScale, scale * zoom))

                                offsetX += pan.x
                                offsetY += pan.y
                                if ((zoom > 1 || zoom < 1) && (scale >= minScaleDef && scale < maxScale)) {
                                    offsetX *= zoom
                                    offsetY *= zoom
                                }
                                moveToCropAreaPosition(minScale, maxScale, aspectRatio)
                                croppedImage = cropImage(
                                    bitmap,
                                    cropAreaBounds,
                                    offsetX,
                                    offsetY,
                                    scale
                                )
                                croppedBitmap(croppedImage)
                            }
                        },
                    onDraw = {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        val rectPath = Path().apply {
                            addRect(cropAreaBounds)
                        }
                        withTransform({
                            translate(offsetX, offsetY)
                            scale(
                                scaleX = maxOf(minScale, minOf(maxScale, scale)),
                                scaleY = maxOf(minScale, minOf(maxScale, scale))
                            )
                            imageCenter = this.center
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
                    }
                )
            }
        }
    }
}

private fun moveToCropAreaPosition(minScale: Float, maxScale: Float, aspectRatio: Float) {
    if (scale < scaleToOverlayBounds) {
        zoomToCropAreaBounds(minScale, maxScale, aspectRatio)
    }
    if (offsetX > -cropAreaBounds.width / 2) {
        offsetX = -cropAreaBounds.width / 2
    }
    if (offsetX + imageWidth * scale < cropAreaBounds.width / 2) {
        offsetX = cropAreaBounds.width / 2 - imageWidth * scale
    }
    if (offsetY > -cropAreaBounds.height / 2) {
        offsetY = -cropAreaBounds.height / 2
    }
    if (offsetY + imageHeight * scale < cropAreaBounds.height / 2) {
        offsetY = cropAreaBounds.height / 2 - imageHeight * scale
    }
}

private fun zoomToCropAreaBounds(minScale: Float, maxScale: Float, aspectRatio: Float) {
    when {
        // If image inside overlay
        (imageWidth < cropAreaBounds.width && imageHeight < cropAreaBounds.height) -> {
            scale *= if (imageWidth / imageHeight.toFloat() < aspectRatio) {
                cropAreaBounds.width / (imageWidth * scale)
            } else {
                cropAreaBounds.height / (imageHeight * scale)
            }
        }
        // If image width less then overlay width
        (imageWidth < cropAreaBounds.width && imageHeight >= cropAreaBounds.height) -> {
            scale *= cropAreaBounds.width / (imageWidth * scale)
        }
        // If image height less then overlay height
        (imageWidth >= cropAreaBounds.width && imageHeight < cropAreaBounds.height) -> {
            scale *= cropAreaBounds.height / (imageHeight * scale)

        }
        // If image outside the overlay
        (imageWidth >= cropAreaBounds.width && imageHeight >= cropAreaBounds.height) -> {
            scale *= if (imageWidth / imageHeight.toFloat() < aspectRatio) {
                cropAreaBounds.width / (imageWidth * scale)
            } else {
                cropAreaBounds.height / (imageHeight * scale)
            }
        }
    }
    minScaleDef = scale
    scale = maxOf(minScale, minOf(maxScale, scale))

    scaleToOverlayBounds = scale
}

private fun moveToCenter() {
    offsetX = -imageWidth / 2f * scale
    offsetY = -imageHeight / 2f * scale
}

private fun cropImage(
    bitmap: Bitmap,
    overlay: Rect,
    offsetX: Float,
    offsetY: Float,
    scale: Float
): Bitmap? {
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


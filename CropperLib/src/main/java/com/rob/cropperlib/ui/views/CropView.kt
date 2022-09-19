package com.rob.cropperlib.ui.views

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.properties.Delegates

const val aspectRatio = 3/4f

var containerWidth by Delegates.notNull<Float>()
var containerHeight by Delegates.notNull<Float>()

var imageHeight by Delegates.notNull<Int>()
var imageWidth by Delegates.notNull<Int>()

lateinit var overlayBounds : Rect

val scale = mutableStateOf(1f)
val scaleToOverlayBounds = mutableStateOf(1f)
val offsetX = mutableStateOf(0f)
val offsetY = mutableStateOf(0f)
val imageCenter = mutableStateOf(Offset.Zero)

@Composable
fun CropView(
    modifier: Modifier = Modifier,
    cropBoarderColor: Color = Color.White,
    backgroundColor: Color = Color.White,
    toolbarColor: Color = Color.White,
    toolbarHeight: Dp = 56.dp,
    withToolbarLeftBtn: @Composable (() -> Unit)? = null,
    withToolbarRightBtn: @Composable (() -> Unit)? = null,
    withToolbarTitle: @Composable (() -> Unit)? = null,
    bitmap: Bitmap? = null,
    maxScale: Float = 5f,
    minScale: Float = 0.1f,
) {
    if (bitmap != null) {
        BoxWithConstraints(
            modifier = modifier
        ) {

            val lifecycleOwner = LocalLifecycleOwner.current

            containerWidth = with(LocalDensity.current) { maxWidth.toPx() }
            containerHeight = with(LocalDensity.current) { maxHeight.toPx() }

            val left = 40f
            val top = (containerHeight - (containerWidth - 80f) * 4 / 3) / 2
            val right = containerWidth - 40f
            val bottom = containerHeight - (containerHeight - (containerWidth - 80f) * 4 / 3) / 2

            overlayBounds = Rect(left, top, right, bottom)

            imageHeight = bitmap.height
            imageWidth = bitmap.width

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        zoomToOverlayBounds(minScale, maxScale)
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
                            forEachGesture {
                                awaitPointerEventScope {
                                    awaitFirstDown()
                                    do {
                                        val event = awaitPointerEvent()
                                        scale.value *= event.calculateZoom()
                                        if (scale.value <= minScale) scale.value = minScale
                                        if (scale.value >= maxScale) scale.value = maxScale
                                        val offset = event.calculatePan()
                                        offsetX.value += offset.x
                                        offsetY.value += offset.y
                                    } while (event.changes.any { it.pressed })
                                }
                                if (scale.value < scaleToOverlayBounds.value){
                                    zoomToOverlayBounds(minScale, maxScale)
                                }
                                if (offsetX.value > -overlayBounds.width/2){
                                    offsetX.value = -overlayBounds.width/2
                                }
                                if (offsetX.value + imageWidth*scale.value < overlayBounds.width/2){
                                    offsetX.value = overlayBounds.width/2 - imageWidth*scale.value
                                }
                                if (offsetY.value > -overlayBounds.height/2){
                                    offsetY.value = -overlayBounds.height/2
                                }
                                if (offsetY.value + imageHeight*scale.value < overlayBounds.height/2){
                                    offsetY.value = overlayBounds.height/2 - imageHeight*scale.value
                                }
                            }
                        },
                    onDraw = {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        val rectPath = Path().apply {
                            addRect(overlayBounds)
                        }
                        withTransform({
                            translate(offsetX.value, offsetY.value)
                            scale(
                                scaleX = maxOf(minScale, minOf(maxScale, scale.value)),
                                scaleY = maxOf(minScale, minOf(maxScale, scale.value))
                            )
                            imageCenter.value = this.center
                        }) {
                            drawImage(bitmap.asImageBitmap(), topLeft = overlayBounds.center)
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
                modifier = Modifier
                    .wrapContentSize()
                    .padding(15.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Text(text = "X: ${offsetX.value}")
                Text(text = "Y: ${offsetY.value}")
                Text(text = "S: ${scale.value}")
                Text(text = "Image sizes: ${imageWidth}x${imageHeight}")
                Text(text = "Container sizes: ${overlayBounds.topLeft}")
                Text(text = "Image Center: ${imageCenter.value}")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(toolbarHeight)
                    .align(Alignment.TopCenter)
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
        }
    }
}

fun zoomToOverlayBounds(minScale: Float, maxScale: Float) {
    when {
        // If image inside overlay
        (imageWidth < overlayBounds.width && imageHeight < overlayBounds.height) -> {
            if ( imageWidth/imageHeight.toFloat() < aspectRatio) {
                scale.value *= overlayBounds.width / (imageWidth * scale.value)
            } else {
                scale.value *= overlayBounds.height / (imageHeight * scale.value)
            }
        }
        // If image width less then overlay width
        (imageWidth < overlayBounds.width && imageHeight >= overlayBounds.height)->{
            scale.value *= overlayBounds.width / (imageWidth * scale.value)
        }
        // If image height less then overlay height
        (imageWidth >= overlayBounds.width && imageHeight < overlayBounds.height)->{
            scale.value *= overlayBounds.height / (imageHeight * scale.value)
        }
        // If image outside the overlay
        (imageWidth >= overlayBounds.width && imageHeight >= overlayBounds.height)->{
            if (imageWidth/imageHeight.toFloat() < aspectRatio) {
                scale.value *= overlayBounds.width / (imageWidth * scale.value)
            } else {
                scale.value *= overlayBounds.height / (imageHeight * scale.value)
            }
        }
    }
    scale.value = maxOf(minScale, minOf(maxScale, scale.value))
    scaleToOverlayBounds.value = scale.value
    offsetX.value = -imageWidth / 2f * scale.value
    offsetY.value = -imageHeight / 2f * scale.value
}


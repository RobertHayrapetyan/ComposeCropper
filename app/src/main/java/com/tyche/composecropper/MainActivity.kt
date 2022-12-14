package com.tyche.composecropper

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.rob.cropperlib.ui.views.CropView
import com.tyche.composecropper.ui.theme.ComposeCropperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightNavigationBars = true
            isAppearanceLightStatusBars = true
        }
        window.apply {
            navigationBarColor = ContextCompat.getColor(this@MainActivity, R.color.white)
            statusBarColor = ContextCompat.getColor(this@MainActivity, R.color.white)
        }
        var imageUri: Uri? = null
        val compose = mutableStateOf(false)
        val bitmap = mutableStateOf<Bitmap?>(null)
        val croppedBitmap = mutableStateOf<Bitmap?>(null)
        val pickMedia =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                imageUri = uri
                compose.value = true
                imageUri?.let {
                    bitmap.value = if (Build.VERSION.SDK_INT < 28) {
                        MediaStore.Images
                            .Media.getBitmap(this.contentResolver, it)
                    } else {
                        val source = ImageDecoder
                            .createSource(this.contentResolver, it)
                        ImageDecoder.decodeBitmap(source)
                    }
                }
            }

        setContent {
            ComposeCropperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column(modifier = Modifier.wrapContentSize()) {
                        Text(modifier = Modifier.clickable {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, text = "Text")
                    }
                    if (compose.value) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CropView(
                                modifier = Modifier.fillMaxSize(),
                                cropBoarderColor = Color.White,
                                backgroundColor = Color.Black,
                                bitmap = bitmap.value,
                                croppedBitmap = {
                                    croppedBitmap.value = it
                                }
                            )
                            if (croppedBitmap.value != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp)
                                        .width(100.dp)
                                        .aspectRatio(3 / 4f)
                                        .background(Color.Transparent)
                                ) {
                                    Image(
                                        modifier = Modifier.fillMaxSize(),
                                        bitmap = croppedBitmap.value!!.asImageBitmap(),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
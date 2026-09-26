package com.invictus.xcode.feature.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File

/** Renders a raster image (png/jpg/webp/gif/bmp) with pinch-zoom + pan. */
@Composable
fun ImagePreviewView(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ZoomableBox(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

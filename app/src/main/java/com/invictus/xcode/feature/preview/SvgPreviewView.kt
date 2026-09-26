package com.invictus.xcode.feature.preview

import android.graphics.drawable.PictureDrawable
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.io.File

/** Renders an .svg file. AndroidSVG parses it to a Picture; ImageView draws a PictureDrawable. */
@Composable
fun SvgPreviewView(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var error by remember(file) { mutableStateOf<String?>(null) }

    if (error != null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    ZoomableBox(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { view ->
                try {
                    val svg = SVG.getFromInputStream(file.inputStream())
                    view.setImageDrawable(PictureDrawable(svg.renderToPicture()))
                } catch (e: SVGParseException) {
                    error = e.message ?: "Invalid SVG"
                } catch (e: Exception) {
                    error = e.message ?: "Could not load SVG"
                }
            },
        )
    }
}

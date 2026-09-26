package com.invictus.xcode.feature.preview

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Renders a local video file with standard playback controls (play/pause, seek). Plays straight
 * from a file:// Uri -- fine here since Xcode already requires All Files Access to do anything
 * (see FileOpenPolicy/TextFileIo), unlike sharing a file cross-app, which is what FileProvider
 * exists for.
 */
@Composable
fun VideoPreviewView(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var videoView: VideoView? = null

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            VideoView(context).apply {
                setVideoURI(Uri.fromFile(file))
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                requestFocus()
                start()
                videoView = this
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose { videoView?.stopPlayback() }
    }
}

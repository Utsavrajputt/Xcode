package com.invictus.xcode.feature.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.PictureDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.caverock.androidsvg.SVG
import com.invictus.xcode.R
import com.invictus.xcode.core.editor.TabBuffer
import com.invictus.xcode.core.preview.VectorExport
import com.invictus.xcode.core.preview.VectorXmlToSvg
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 300L
private val LightCanvas = Color(0xFFF2F2F2)
private val DarkCanvas = Color(0xFF121212)

/**
 * Live preview of an Android VectorDrawable tab. The XML is converted to SVG
 * ([VectorXmlToSvg]) and drawn with AndroidSVG; the bar underneath saves the result to Downloads
 * as PNG or SVG, or copies the SVG text. While the XML is mid-edit and invalid, the last good
 * drawing stays on screen with the parse error underneath.
 *
 * [revision] is the same re-render cue the Markdown preview uses (Sora's content isn't observable).
 */
@Composable
fun VectorXmlPreviewView(
    buffer: TabBuffer,
    revision: Long,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var xml by remember { mutableStateOf(buffer.content.toString()) }
    LaunchedEffect(revision) {
        delay(DEBOUNCE_MS)
        xml = buffer.content.toString()
    }

    val result = remember(xml) { VectorXmlToSvg.convert(xml) }
    var lastGood by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(result) { (result as? VectorXmlToSvg.Result.Ok)?.let { lastGood = it.svg } }
    val svg = (result as? VectorXmlToSvg.Result.Ok)?.svg ?: lastGood
    val error = (result as? VectorXmlToSvg.Result.Err)?.message

    val drawable = remember(svg) {
        svg?.let { runCatching { PictureDrawable(SVG.getFromString(it).renderToPicture()) }.getOrNull() }
    }

    // Black icons vanish on a dark canvas and white ones on a light canvas, so it's switchable.
    var darkCanvas by rememberSaveable { mutableStateOf(false) }
    val baseName = buffer.file.nameWithoutExtension

    fun save(label: String, block: suspend () -> java.io.File) {
        scope.launch {
            runCatching { block() }
                .onSuccess { onMessage(context.getString(R.string.vector_preview_saved, it.name)) }
                .onFailure { onMessage(context.getString(R.string.vector_preview_save_failed, it.message ?: label)) }
        }
    }

    Column(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (darkCanvas) DarkCanvas else LightCanvas),
            contentAlignment = Alignment.Center,
        ) {
            if (drawable != null) {
                ZoomableBox(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                        update = { view -> view.setImageDrawable(drawable) },
                    )
                }
            } else if (error == null) {
                Text(stringResource(R.string.preview_unsupported), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (error != null) {
            Text(
                text = stringResource(R.string.vector_preview_invalid, error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            ExportButton(XIcons.Download, stringResource(R.string.vector_preview_png), svg != null) {
                svg?.let { save("PNG") { VectorExport.savePng(it, baseName) } }
            }
            ExportButton(XIcons.Download, stringResource(R.string.vector_preview_svg), svg != null) {
                svg?.let { save("SVG") { VectorExport.saveSvg(it, baseName) } }
            }
            ExportButton(XIcons.ContentCopy, stringResource(R.string.vector_preview_copy_svg), svg != null) {
                svg?.let {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText(baseName, it))
                    // Android 13+ shows its own "copied" confirmation.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        onMessage(context.getString(R.string.vector_preview_copied))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { darkCanvas = !darkCanvas }) {
                Icon(XIcons.Palette, contentDescription = stringResource(R.string.vector_preview_background))
            }
        }
    }
}

@Composable
private fun ExportButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

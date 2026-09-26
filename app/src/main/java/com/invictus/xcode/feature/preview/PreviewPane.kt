package com.invictus.xcode.feature.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.invictus.xcode.core.editor.TabBuffer
import com.invictus.xcode.core.preview.PreviewType
import java.io.File

/** The preview half of a markdown/html tab, in both SPLIT and PREVIEW app-bar modes. */
@Composable
fun PreviewPane(
    buffer: TabBuffer,
    previewType: PreviewType,
    revision: Long,
    darkTheme: Boolean,
    projectRoot: File?,
    onSetHtmlJsEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (previewType) {
        PreviewType.MARKDOWN -> MarkdownPreviewView(buffer, revision, darkTheme, modifier)
        PreviewType.HTML -> HtmlPreviewView(buffer, revision, projectRoot, onSetHtmlJsEnabled, modifier)
        else -> Unit // NONE / media: never routed here.
    }
}

package com.invictus.kodex.feature.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.invictus.kodex.core.editor.TabBuffer
import com.invictus.kodex.core.preview.PreviewType
import java.io.File

/** The preview half of a markdown/html/vector-xml tab, in both SPLIT and PREVIEW app-bar modes. */
@Composable
fun PreviewPane(
    buffer: TabBuffer,
    previewType: PreviewType,
    revision: Long,
    darkTheme: Boolean,
    projectRoot: File?,
    onSetHtmlJsEnabled: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (previewType) {
        PreviewType.MARKDOWN -> MarkdownPreviewView(buffer, revision, darkTheme, modifier)
        PreviewType.HTML -> HtmlPreviewView(buffer, revision, projectRoot, onSetHtmlJsEnabled, modifier)
        PreviewType.VECTOR_XML -> VectorXmlPreviewView(buffer, revision, onMessage, modifier)
        else -> Unit // NONE / media: never routed here.
    }
}

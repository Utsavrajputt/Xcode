package com.invictus.xcode.feature.preview

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.invictus.xcode.core.editor.TabBuffer
import kotlinx.coroutines.delay

private const val DEBOUNCE_MS = 300L

/**
 * Renders [buffer]'s text as markdown. [revision] is the caller's cue to re-render -- it's a
 * plain counter (EditorUiState.activeContentRevision) rather than reading buffer.content
 * reactively, since Sora's Content is a mutable CharSequence Compose can't observe on its own.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownPreviewView(
    buffer: TabBuffer,
    revision: Long,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assetLoader = remember { PreviewAssetLoader.forAppAssets(context) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ) = assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String) {
                        pageReady = true
                    }
                }
                loadUrl(PreviewAssetLoader.urlFor("assets/preview/markdown_shell.html"))
                webView = this
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    LaunchedEffect(webView, pageReady, darkTheme) {
        if (pageReady) webView?.evaluateJavascript("setDark(${darkTheme});", null)
    }

    LaunchedEffect(webView, pageReady, revision, darkTheme) {
        if (!pageReady) return@LaunchedEffect
        delay(DEBOUNCE_MS)
        val text = buffer.content.toString()
        val base64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        webView?.evaluateJavascript("setContent(\"$base64\");", null)
    }
}

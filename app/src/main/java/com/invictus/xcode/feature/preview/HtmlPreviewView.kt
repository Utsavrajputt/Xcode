package com.invictus.xcode.feature.preview

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.invictus.xcode.R
import com.invictus.xcode.core.editor.TabBuffer
import java.io.File
import kotlinx.coroutines.delay

/**
 * Renders [buffer]'s HTML file inside the sandboxed appassets.androidplatform.net domain, served
 * from [projectRoot] (see [PreviewAssetLoader.forProjectRoot] for the path-escape check). JS is
 * off by default -- previewed HTML is the user's own file, but it can itself embed or fetch
 * further untrusted content, so the same caution as opening any unknown local HTML applies.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreviewView(
    buffer: TabBuffer,
    revision: Long,
    projectRoot: File?,
    onRequestJsToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val root = projectRoot ?: buffer.file.parentFile ?: buffer.file
    val assetLoader = remember(root) { PreviewAssetLoader.forProjectRoot(root) }
    val relativePath = remember(root, buffer.file) {
        root.canonicalFile.toPath().relativize(buffer.file.canonicalFile.toPath()).toString()
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showJsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(buffer) {
        if (!buffer.htmlJsPromptShown) showJsDialog = true
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = buffer.htmlJsEnabled
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ) = assetLoader.shouldInterceptRequest(request.url)
                }
                webView = this
            }
        },
        update = { view ->
            view.settings.javaScriptEnabled = buffer.htmlJsEnabled
        },
    )

    // Reload on every debounced edit and whenever the JS setting changes -- unlike markdown,
    // there's no cheap partial-update hook into an arbitrary user HTML page, so a full reload
    // (still local, still instant) is the straightforward correct behavior here.
    LaunchedEffect(webView, relativePath, revision, buffer.htmlJsEnabled) {
        delay(300L)
        webView?.loadUrl(PreviewAssetLoader.urlFor(relativePath))
    }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    if (showJsDialog) {
        AlertDialog(
            onDismissRequest = {
                showJsDialog = false
                onRequestJsToggle(false)
            },
            title = { Text(stringResource(R.string.preview_html_js_dialog_title)) },
            text = { Text(stringResource(R.string.preview_html_js_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showJsDialog = false
                    onRequestJsToggle(true)
                }) { Text(stringResource(R.string.preview_html_js_dialog_enable)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJsDialog = false
                    onRequestJsToggle(false)
                }) { Text(stringResource(R.string.preview_html_js_dialog_keep_off)) }
            },
        )
    }
}

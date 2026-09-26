package com.invictus.xcode.feature.preview

import android.content.Context
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

/**
 * WebViewAssetLoader lets a WebView load http(s) URLs that actually resolve to local bytes --
 * required for relative script/css/img tags to work at all, and far safer than file:// (which
 * Chromium heavily restricts and which also happily follows ../ out of the sandbox with no
 * checks of its own).
 */
object PreviewAssetLoader {

    /** For the bundled markdown_shell.html: serves this app's own assets/preview/ tree only. */
    fun forAppAssets(context: Context): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

    /**
     * For HTML preview: serves the *project's* files so relative `<script src="./x.js">` and
     * `<link href="../shared.css">` in the user's own HTML resolve. [projectRoot] is the sole
     * trust boundary -- every request is canonicalized and must stay under it, so a crafted
     * `../../` in the previewed HTML can't read anything outside the open project.
     */
    fun forProjectRoot(projectRoot: File): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/", ProjectRootPathHandler(projectRoot))
            .build()

    /** appassets.androidplatform.net URL for a file, given the loader that will serve it. */
    fun urlFor(relativePath: String): String = "https://$DOMAIN/$relativePath"

    private const val DOMAIN = "appassets.androidplatform.net"

    private class ProjectRootPathHandler(root: File) : WebViewAssetLoader.PathHandler {
        private val rootCanonical: File = root.canonicalFile

        override fun handle(path: String): WebResourceResponse? {
            return try {
                val requested = File(rootCanonical, path).canonicalFile
                // The one check that matters: resolved path must not have escaped the project.
                if (!requested.path.startsWith(rootCanonical.path + File.separator) && requested != rootCanonical) {
                    return null
                }
                if (!requested.isFile) return null
                val mime = MimeTypeMap.getFileExtensionFromUrl(requested.name)
                    ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                    ?: "text/html"
                WebResourceResponse(mime, "utf-8", FileInputStream(requested))
            } catch (_: FileNotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}

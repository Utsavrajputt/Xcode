package com.invictus.xcode.core.github

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Coil loader used only for GitHub avatars. GitHub serves avatars with a very short
 * max-age, so a network interceptor rewrites the response headers to 72h; Coil's disk
 * cache then treats the file as fresh for that long.
 */
object AvatarImageLoader {
    private val MAX_AGE_SECONDS = TimeUnit.HOURS.toSeconds(72)

    fun create(context: Context): ImageLoader {
        val app = context.applicationContext
        val http = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", "public, max-age=$MAX_AGE_SECONDS")
                    .build()
            }
            .build()
        return ImageLoader.Builder(app)
            .okHttpClient(http)
            .diskCache {
                DiskCache.Builder()
                    .directory(app.cacheDir.resolve("github_avatars"))
                    .maxSizeBytes(20L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

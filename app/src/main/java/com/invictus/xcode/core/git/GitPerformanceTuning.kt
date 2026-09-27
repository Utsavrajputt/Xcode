package com.invictus.xcode.core.git

import org.eclipse.jgit.storage.file.WindowCacheConfig

/**
 * JGit's [WindowCacheConfig] defaults are tuned for a desktop JVM: a trusted local disk and a
 * heap it can spend generously. Three of them are directly why "Resolving deltas" is so much
 * slower here than native git in Termux on the *same* phone (JGit is pure Java either way -
 * this doesn't close that gap - it just stops JGit from paying its own avoidable overhead on
 * top of it):
 *
 * - `packedGitMMAP` (default **false**): every pack read goes through `RandomAccessFile` - a
 *   syscall plus a copy into a JVM `byte[]` - instead of a page-cache-backed memory map.
 *   Resolving a delta chain means repeatedly seeking back into the pack to find each base
 *   object, which is exactly the access pattern mmap is for.
 * - `deltaBaseCacheLimit` (default **10 MiB**): the cache of already-inflated base objects
 *   that multiple deltas chain off of. Once the working set of bases for a resolve pass
 *   exceeds this, JGit re-inflates the same base repeatedly instead of reusing it - on a repo
 *   with any depth to it, 10 MiB is small enough to make this the common case.
 * - `streamFileThreshold` (default **50 MiB**, JGit's own `PackConfig.DEFAULT_BIG_FILE_THRESHOLD`):
 *   below this, JGit decompresses an object into one contiguous `byte[]` it can seek around in;
 *   above it, delta application has to re-seek through an inflate stream instead, which JGit's
 *   own changelog calls out as "very expensive" - worth raising for repos with larger tracked
 *   files (assets, generated code, etc).
 *
 * All three are process-wide in JGit (a single static [WindowCache][org.eclipse.jgit.internal.storage.file.WindowCache]
 * shared by every [org.eclipse.jgit.lib.Repository] in the JVM), so this only needs to run
 * once, before any repository is touched - [com.invictus.xcode.XcodeApp.onCreate], next to
 * [installGitByteCounting].
 *
 * The actual limits are computed from the app's max heap (capped well below it) instead of a
 * flat constant, so this helps on a phone with room to spare without risking an `OutOfMemoryError`
 * on one that doesn't.
 */
fun installGitPerformanceTuning() {
    val maxHeap = Runtime.getRuntime().maxMemory()
    val deltaBaseCacheLimit = minOf(64L * WindowCacheConfig.MB, maxHeap / 8, Int.MAX_VALUE.toLong()).toInt()
    val streamFileThreshold = minOf(64L * WindowCacheConfig.MB, maxHeap / 4, Int.MAX_VALUE.toLong()).toInt()
    val packedGitLimit = minOf(64L * WindowCacheConfig.MB, maxHeap / 4)

    val cfg = WindowCacheConfig()
    cfg.setPackedGitMMAP(true)
    cfg.setDeltaBaseCacheLimit(deltaBaseCacheLimit)
    cfg.setStreamFileThreshold(streamFileThreshold)
    cfg.setPackedGitLimit(packedGitLimit)
    cfg.install()
}

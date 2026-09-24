package com.invictus.xcode.core.project

import java.io.File

/** Path-prefix helpers that respect segment boundaries ("/a/bc" is not under "/a/b"). */
object PathUtil {
    fun isSameOrUnder(path: String, root: String): Boolean =
        path == root || path.startsWith(root.trimEnd(File.separatorChar) + File.separator)

    /** Moves [path] from below [old] to the same relative spot below [new]. */
    fun rebase(path: String, old: String, new: String): String = new + path.removePrefix(old)
}

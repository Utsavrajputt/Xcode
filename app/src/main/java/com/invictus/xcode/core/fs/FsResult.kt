package com.invictus.xcode.core.fs

import java.io.File

enum class FsError { INVALID_NAME, ALREADY_EXISTS, NOT_FOUND, INSIDE_ITSELF, PERMISSION, IO }

sealed interface FsResult<out T> {
    data class Ok<out T>(val value: T) : FsResult<T>
    data class Err(val error: FsError, val detail: String? = null) : FsResult<Nothing>
}

/** One child of a directory. [isDirectory] is resolved once so sorting never re-hits the disk. */
data class FsEntry(val file: File, val isDirectory: Boolean) {
    val name: String get() = file.name
}

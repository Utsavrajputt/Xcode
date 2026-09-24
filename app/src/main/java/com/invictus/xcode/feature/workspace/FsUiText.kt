package com.invictus.xcode.feature.workspace

import com.invictus.xcode.R
import com.invictus.xcode.core.fs.FsError
import com.invictus.xcode.core.fs.FsResult

fun FsResult.Err.toUiText(): UiText = when (error) {
    FsError.INVALID_NAME -> UiText(R.string.fs_err_invalid_name)
    FsError.ALREADY_EXISTS -> UiText(R.string.fs_err_exists)
    FsError.NOT_FOUND -> UiText(R.string.fs_err_not_found)
    FsError.INSIDE_ITSELF -> UiText(R.string.fs_err_inside_itself)
    FsError.PERMISSION -> UiText(R.string.fs_err_permission)
    FsError.IO -> UiText(R.string.fs_err_io)
}

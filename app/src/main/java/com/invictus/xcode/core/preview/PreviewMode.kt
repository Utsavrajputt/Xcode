package com.invictus.xcode.core.preview

/** Per-tab state for MARKDOWN/HTML tabs, cycled by the app bar's preview toggle button. */
enum class PreviewMode {
    EDITOR,
    SPLIT,
    PREVIEW,
    ;

    fun next(): PreviewMode = when (this) {
        EDITOR -> SPLIT
        SPLIT -> PREVIEW
        PREVIEW -> EDITOR
    }
}

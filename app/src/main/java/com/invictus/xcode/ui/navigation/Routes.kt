package com.invictus.xcode.ui.navigation

object Routes {
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val WORKSPACE = "workspace"
    const val EDITOR = "editor"
    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_EDITING = "settings/editing"
    const val SETTINGS_BEHAVIOR = "settings/behavior"
    const val MEDIA_PREVIEW = "media_preview"
    const val GIT = "git/{projectPath}"
    const val GIT_CLONE = "git_clone"

    /** Encoded deep-link style route for a project's Source Control screen. */
    fun git(projectPath: String): String =
        "git/${android.net.Uri.encode(projectPath)}"
}

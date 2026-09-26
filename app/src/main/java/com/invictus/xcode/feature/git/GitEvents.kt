package com.invictus.xcode.feature.git

sealed interface GitEvent {
    data object Refresh : GitEvent
    data object StageAll : GitEvent
    data object UnstageAll : GitEvent
    data class Stage(val path: String) : GitEvent
    data class Unstage(val path: String) : GitEvent
    data class CommitMessageChange(val text: String) : GitEvent
    data object ToggleAmend : GitEvent
    data object Commit : GitEvent
    data object Push : GitEvent
    data object Pull : GitEvent
    data object Fetch : GitEvent
    data object OpenIdentity : GitEvent
    data class SaveIdentity(val name: String, val email: String, val isLocal: Boolean) : GitEvent
    data object DismissIdentity : GitEvent
    data class SaveToken(val host: String, val username: String, val token: String) : GitEvent
    data object DismissToken : GitEvent
    data object DismissError : GitEvent
}

sealed interface GitCloneEvent {
    data class UrlChange(val value: String) : GitCloneEvent
    data class UsernameChange(val value: String) : GitCloneEvent
    data class TokenChange(val value: String) : GitCloneEvent
    data class ParentChange(val value: String) : GitCloneEvent
    data class FolderChange(val value: String) : GitCloneEvent
    data class BranchChange(val value: String) : GitCloneEvent
    data object Start : GitCloneEvent
    data object DismissError : GitCloneEvent
}

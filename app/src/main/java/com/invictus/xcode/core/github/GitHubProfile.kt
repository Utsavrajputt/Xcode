package com.invictus.xcode.core.github

/** Subset of GitHub's `GET /user` response shown on the token card. */
data class GitHubProfile(
    val login: String,
    val name: String?,
    /** Public profile email; null when the user keeps it private. */
    val email: String?,
    val avatarUrl: String,
    val publicRepos: Int,
    val followers: Int,
    val following: Int,
    val fetchedAt: Long,
) {
    /** Name to auto-fill into the git identity: display name, else the login. */
    val autofillName: String get() = name?.takeIf { it.isNotBlank() } ?: login

    /** Avatar URL sized for the 48dp card avatar (smaller download, same cache key per size). */
    val avatarUrlSmall: String
        get() = avatarUrl + (if ('?' in avatarUrl) "&" else "?") + "s=96"
}

package com.invictus.kodex.github.auth

import com.invictus.kodex.core.security.GitCredentialStore

/**
 * Thin wrapper over the existing [GitCredentialStore] (host `github.com`). Reads on every call and
 * never caches the token in a field, so signing out in Settings takes effect immediately.
 */
class GitHubTokenProvider(private val store: GitCredentialStore) {
    fun token(): String? = store.get(HOST)?.token?.takeIf { it.isNotBlank() }
    fun hasToken(): Boolean = token() != null

    companion object { const val HOST = "github.com" }
}

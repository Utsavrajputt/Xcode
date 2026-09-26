package com.invictus.xcode.core.git

import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/** One stored credential record, keyed by normalized host (see [normalizeHost]). */
data class GitCredential(
    val host: String,
    /** HTTPS basic-auth username. Any non-empty value works for token auth on most hosts. */
    val username: String,
    val token: String,
)

/** Key for the credential store: lowercase host from a clone/push/remote URL. */
fun normalizeHost(url: String): String = runCatching {
    URIish(url).host?.lowercase()
}.getOrNull() ?: url.substringAfter("://").substringBefore('/').lowercase()

/**
 * JGit credentials provider for token auth: the token travels as the basic-auth
 * password. `x-access-token` is GitHub's convention and is accepted as a placeholder
 * username by GitHub, GitLab, Codeberg and Bitbucket when a PAT is the password.
 */
class GitTokenCredentialsProvider(
    private val username: String,
    private val token: String,
) : UsernamePasswordCredentialsProvider(username, token)

/**
 * Reads the remote URL for [remoteName] from the repo config and returns a
 * credentials provider for its host, or null when no token is stored there.
 */
fun credentialsFor(
    config: org.eclipse.jgit.lib.Config,
    remoteName: String,
    credentialLookup: (host: String) -> GitCredential?,
): GitTokenCredentialsProvider? {
    val url = config.getString("remote", remoteName, "url") ?: return null
    val credential = credentialLookup(normalizeHost(url)) ?: return null
    return GitTokenCredentialsProvider(credential.username, credential.token)
}

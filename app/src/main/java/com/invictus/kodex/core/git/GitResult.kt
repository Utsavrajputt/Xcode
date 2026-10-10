package com.invictus.kodex.core.git

import com.invictus.kodex.core.git.model.GitAuthFailureType
import com.invictus.kodex.core.git.model.GitErrorDetails
import com.invictus.kodex.core.git.model.GitErrorKind
import org.eclipse.jgit.api.errors.EmptyCommitException
import org.eclipse.jgit.api.errors.TransportException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

/** Outcome of any JGit call: value, or a user-presentable error bundle. */
sealed interface GitResult<out T> {
    data class Ok<T>(val value: T) : GitResult<T>
    data class Err(val error: GitErrorDetails) : GitResult<Nothing>
}

/**
 * Turns raw JGit exceptions into [GitErrorDetails]. Auth failures are classified
 * separately so the UI can offer a "update token" shortcut instead of a dead end.
 */
object GitErrorFactory {

    fun from(throwable: Throwable, title: String, message: (Throwable) -> String): GitErrorDetails {
        if (throwable is CancellationException) throw throwable
        return GitErrorDetails(
            title = title,
            message = message(throwable),
            exceptionClass = throwable.javaClass.name,
            stackTrace = throwable.stackTraceToString(),
            authFailure = classifyAuth(throwable),
            kind = classifyKind(throwable),
        )
    }

    private fun classifyKind(t: Throwable): GitErrorKind {
        var cause: Throwable? = t
        while (cause != null) {
            when (cause) {
                is GitPushRejectedException -> return GitErrorKind.PUSH_REJECTED
                is GitUnfetchedCommitsException -> return GitErrorKind.UNFETCHED_COMMITS
                is EmptyCommitException -> return GitErrorKind.EMPTY_COMMIT
                is GitIdentityMissingException -> return GitErrorKind.IDENTITY_MISSING
            }
            cause = cause.cause
        }
        return GitErrorKind.OTHER
    }

    private fun classifyAuth(t: Throwable): GitAuthFailureType {
        var cause: Throwable? = t
        while (cause != null) {
            when {
                cause is TransportException -> {
                    val msg = cause.message ?: return GitAuthFailureType.UNKNOWN
                    return when {
                        msg.contains("401") || msg.contains("403") || msg.contains("not authorized", true) ||
                            msg.contains("authentication", true) || msg.contains("Auth fail", true) ->
                            GitAuthFailureType.INVALID_CREDENTIALS
                        msg.contains("404") -> GitAuthFailureType.PERMISSION_DENIED
                        msg.contains("timed out", true) || msg.contains("timeout", true) ->
                            GitAuthFailureType.NETWORK_ERROR
                        msg.contains("Connection refused", true) || msg.contains("No route to host", true) ||
                            cause.cause is UnknownHostException -> GitAuthFailureType.NETWORK_ERROR
                        else -> GitAuthFailureType.UNKNOWN
                    }
                }
                cause is UnknownHostException || cause is SocketTimeoutException || cause is SSLException ->
                    return GitAuthFailureType.NETWORK_ERROR
            }
            cause = cause.cause
        }
        return GitAuthFailureType.NONE
    }
}

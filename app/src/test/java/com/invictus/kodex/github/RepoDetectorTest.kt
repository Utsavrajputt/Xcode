package com.invictus.kodex.github

import com.invictus.kodex.github.data.RepoDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepoDetectorTest {
    @Test fun https_with_dot_git() {
        val r = RepoDetector.detect(listOf("origin" to "https://github.com/Utsavrajputt/Kodex.git"))
        assertEquals("Utsavrajputt/Kodex", r?.fullName)
    }

    @Test fun https_without_suffix() {
        assertEquals("a/b", RepoDetector.detect(listOf("origin" to "https://github.com/a/b"))?.fullName)
    }

    @Test fun ssh_scp_style() {
        assertEquals("a/b", RepoDetector.detect(listOf("origin" to "git@github.com:a/b.git"))?.fullName)
    }

    @Test fun ssh_url_style() {
        assertEquals("a/b", RepoDetector.detect(listOf("origin" to "ssh://git@github.com/a/b.git"))?.fullName)
    }

    @Test fun non_github_remote_is_ignored() {
        assertNull(RepoDetector.detect(listOf("origin" to "https://gitlab.com/a/b.git")))
    }

    @Test fun no_remotes() {
        assertNull(RepoDetector.detect(emptyList()))
    }

    @Test fun origin_preferred_over_other_github_remote() {
        val r = RepoDetector.detect(
            listOf("upstream" to "https://github.com/up/stream.git", "origin" to "https://github.com/me/fork.git"),
        )
        assertEquals("me/fork", r?.fullName)
    }

    @Test fun first_github_remote_when_origin_is_not_github() {
        val r = RepoDetector.detect(
            listOf("origin" to "https://gitlab.com/x/y.git", "gh" to "https://github.com/me/mirror.git"),
        )
        assertEquals("me/mirror", r?.fullName)
    }

    @Test fun host_only_url_is_rejected() {
        assertNull(RepoDetector.detect(listOf("origin" to "https://github.com/onlyowner")))
    }
}

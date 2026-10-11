package com.invictus.kodex.github

import com.invictus.kodex.github.install.InstallDecision
import com.invictus.kodex.github.install.InstallKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallDecisionTest {
    private val a = setOf("aa")
    private val b = setOf("bb")

    @Test fun notInstalled() = assertEquals(InstallKind.Install, InstallDecision.decide(null, 5, null, a))

    @Test fun updateReinstallDowngrade() {
        assertEquals(InstallKind.Update, InstallDecision.decide(4, 5, a, a))
        assertEquals(InstallKind.Reinstall, InstallDecision.decide(5, 5, a, a))
        assertEquals(InstallKind.Downgrade, InstallDecision.decide(6, 5, a, a))
    }

    @Test fun signatureMismatchWinsOverVersion() {
        assertEquals(InstallKind.SignatureMismatch, InstallDecision.decide(4, 5, a, b))
        assertEquals(InstallKind.SignatureMismatch, InstallDecision.decide(9, 5, a, b))
    }

    @Test fun rotatedCertHistoryOverlapIsNotAMismatch() {
        assertEquals(InstallKind.Update, InstallDecision.decide(4, 5, setOf("old", "new"), setOf("new")))
    }

    @Test fun unknownSignersNeverBlock() {
        assertEquals(InstallKind.Update, InstallDecision.decide(4, 5, null, a))
        assertEquals(InstallKind.Update, InstallDecision.decide(4, 5, a, emptySet()))
    }

    @Test fun onlyDowngradeIsBlocked() {
        assertTrue(InstallDecision.isBlocked(InstallKind.Downgrade))
        assertFalse(InstallDecision.isBlocked(InstallKind.SignatureMismatch))
        assertFalse(InstallDecision.isBlocked(InstallKind.Update))
    }

    @Test fun abiCompatibility() {
        assertTrue(InstallDecision.abiCompatible(emptySet(), listOf("x86_64")))
        assertTrue(InstallDecision.abiCompatible(setOf("arm64-v8a"), listOf("arm64-v8a", "armeabi-v7a")))
        assertFalse(InstallDecision.abiCompatible(setOf("arm64-v8a"), listOf("x86_64")))
    }
}

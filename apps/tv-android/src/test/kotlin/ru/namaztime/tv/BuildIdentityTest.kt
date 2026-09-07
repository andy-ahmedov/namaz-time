package ru.namaztime.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildIdentityTest {
    @Test
    fun debugApplicationIdCannotOccupyAcceptedPilotIdentity() {
        assertEquals("ru.namaztime.tv.debug", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun checkedInVersionAndGeneratedDebugIdentityAreTraceable() {
        assertEquals(10, BuildConfig.VERSION_CODE)
        assertEquals("0.6.3-dev", BuildConfig.VERSION_NAME)
        assertEquals("debug", BuildConfig.BUILD_VARIANT)
        assertTrue(BuildConfig.BUILD_COMMIT.matches(Regex("[0-9a-f]{40}")))

        val identity = currentAppBuildIdentity()

        assertEquals("0.6.3-dev (10)", identity.versionLabel)
        assertEquals(BuildConfig.BUILD_COMMIT.take(12), identity.shortCommit)
        assertEquals("0.6.3-dev+g${BuildConfig.BUILD_COMMIT.take(12)}" +
            if (BuildConfig.BUILD_DIRTY) ".dirty" else "", identity.telemetryVersion)
        assertTrue(identity.telemetryVersion.length <= 64)
    }

    @Test
    fun identityValidationRejectsValuesThatCannotIdentifyAnArtifact() {
        val valid = AppBuildIdentity(
            versionName = "0.5.1-pilot.1",
            versionCode = 5,
            variant = "pilot",
            commit = "a".repeat(40),
            dirty = false,
        )
        assertEquals("0.5.1-pilot.1 (5)", valid.versionLabel)
        assertEquals("a".repeat(12), valid.shortCommit)
        assertFalse(valid.dirty)

        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(versionCode = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(versionName = "pilot latest")
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(variant = "unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(commit = "not-a-commit")
        }
    }
}

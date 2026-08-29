package ru.namaztime.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildIdentityTest {
    @Test
    fun applicationIdMatchesAcceptedPilotIdentity() {
        assertEquals("ru.namaztime.tv", BuildConfig.APPLICATION_ID)
    }
}

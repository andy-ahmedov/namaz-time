package ru.namaztime.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildIdentityTest {
    @Test
    fun debugApplicationIdCannotOccupyAcceptedPilotIdentity() {
        assertEquals("ru.namaztime.tv.debug", BuildConfig.APPLICATION_ID)
    }
}

package com.example.namaztime.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildIdentityTest {
    @Test
    fun applicationIdRemainsTheDocumentedT001Placeholder() {
        assertEquals("com.example.namaztime.tv", BuildConfig.APPLICATION_ID)
    }
}

package ru.namaztime.tv.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsNavigationTest {
    @Test
    fun everySettingsDestinationIsReachableInDocumentedDpadOrder() {
        val expectedRoutes = listOf(
            "mosque",
            "source",
            "iqamah",
            "appearance",
            "campaigns",
            "donation",
            "language",
            "kiosk",
            "diagnostics",
        )

        val visited = generateSequence(SettingsDestination.initial) { it.next }.toList()

        assertEquals(expectedRoutes, visited.map(SettingsDestination::route))
        assertNull(visited.last().next)
        assertNull(visited.first().previous)
        visited.zipWithNext().forEach { (current, next) ->
            assertEquals(current, next.previous)
        }
    }
}

package ru.namaztime.tv.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignEngineTest {
    private val engine = CampaignEngine()

    @Test
    fun `campaign lifecycle is start-inclusive and end-exclusive`() {
        val campaign = campaign()
        val cases = listOf(
            "2026-08-19T23:59:59Z" to CampaignHiddenReason.NOT_STARTED,
            "2026-08-20T00:00:00Z" to null,
            "2026-08-20T23:59:59Z" to null,
            "2026-08-21T00:00:00Z" to CampaignHiddenReason.EXPIRED,
        )

        cases.forEach { (instant, hiddenReason) ->
            val result = engine.resolve(listOf(campaign), Instant.parse(instant))
            if (hiddenReason == null) {
                assertEquals(campaign.id, (result as CampaignResolution.Active).campaign.id)
            } else {
                assertEquals(hiddenReason, (result as CampaignResolution.Hidden).reason)
            }
        }
    }

    @Test
    fun `zero-length campaign range is invalid instead of permanently inactive`() {
        val campaign = campaign().copy(endsAt = "2026-08-20T00:00:00Z")

        assertTrue(engine.preview(campaign) is CampaignPreview.Invalid)
        val result = engine.resolve(listOf(campaign), Instant.parse("2026-08-20T00:00:00Z"))
        assertEquals(CampaignHiddenReason.INVALID, (result as CampaignResolution.Hidden).reason)
    }

    @Test
    fun `invalid or unsafe URLs never become active`() {
        val invalidUrls = listOf(
            "http://example.org/path",
            "HTTPS://example.org/path",
            "https://user:password@example.org/path",
            "https:///missing-host",
            "https://exa mple.org/path",
            "https://example.org/${"x".repeat(2_100)}",
        )

        invalidUrls.forEach { url ->
            val result = engine.resolve(
                listOf(campaign().copy(httpsUrl = url)),
                Instant.parse("2026-08-20T12:00:00Z"),
            )

            assertEquals(CampaignHiddenReason.INVALID, (result as CampaignResolution.Hidden).reason)
        }
    }

    @Test
    fun `corrupt local text fields are rejected at the display boundary`() {
        val invalidCampaigns = listOf(
            campaign().copy(id = "x".repeat(129)),
            campaign().copy(title = "x".repeat(161)),
            campaign().copy(subtitle = "x".repeat(501)),
            campaign().copy(kind = "payment"),
            campaign().copy(placement = "fullscreen"),
        )

        invalidCampaigns.forEach { campaign ->
            val result = engine.resolve(
                listOf(campaign),
                Instant.parse("2026-08-20T12:00:00Z"),
            )

            assertEquals(CampaignHiddenReason.INVALID, (result as CampaignResolution.Hidden).reason)
        }
    }

    @Test
    fun `one corrupt local row does not suppress one valid active campaign`() {
        val result = engine.resolve(
            listOf(
                campaign().copy(id = "corrupt", httpsUrl = "javascript:alert(1)"),
                campaign(),
            ),
            Instant.parse("2026-08-20T12:00:00Z"),
        )

        assertEquals("campaign", (result as CampaignResolution.Active).campaign.id)
    }

    @Test
    fun `overlapping active campaigns fail closed without a publication priority`() {
        val result = engine.resolve(
            listOf(campaign().copy(id = "a"), campaign().copy(id = "b")),
            Instant.parse("2026-08-20T12:00:00Z"),
        )

        assertEquals(CampaignHiddenReason.AMBIGUOUS, (result as CampaignResolution.Hidden).reason)
    }

    @Test
    fun `preview validates a future campaign without making it active`() {
        val campaign = campaign().copy(
            startsAt = "2026-09-01T00:00:00Z",
            endsAt = "2026-09-02T00:00:00Z",
        )

        val preview = engine.preview(campaign)
        val display = engine.resolve(listOf(campaign), Instant.parse("2026-08-20T12:00:00Z"))

        assertTrue(preview is CampaignPreview.Valid)
        assertEquals(CampaignHiddenReason.NOT_STARTED, (display as CampaignResolution.Hidden).reason)
    }

    @Test
    fun `audit stub fingerprints the target without retaining the URL`() {
        val active = engine.resolve(
            listOf(campaign()),
            Instant.parse("2026-08-20T12:00:00Z"),
        ) as CampaignResolution.Active

        assertEquals(64, active.campaign.audit.targetSha256.length)
        assertTrue(active.campaign.audit.targetSha256.matches(Regex("[0-9a-f]{64}")))
        assertFalse(active.campaign.audit.toString().contains("example.org"))
    }

    private fun campaign() = CampaignInput(
        id = "campaign",
        kind = "website",
        httpsUrl = "https://example.org/mosque",
        title = "Расписание мечети",
        subtitle = "Откройте на телефоне",
        startsAt = "2026-08-20T00:00:00Z",
        endsAt = "2026-08-21T00:00:00Z",
        placement = "with_prayer_times",
    )
}

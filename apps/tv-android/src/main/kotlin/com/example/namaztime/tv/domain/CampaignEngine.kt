package com.example.namaztime.tv.domain

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant

data class CampaignInput(
    val id: String,
    val kind: String,
    val httpsUrl: String,
    val title: String,
    val subtitle: String?,
    val startsAt: String,
    val endsAt: String,
    val placement: String,
)

data class CampaignAuditStub(
    val campaignId: String,
    val targetSha256: String,
)

data class ResolvedCampaign(
    val id: String,
    val kind: String,
    val httpsUrl: String,
    val title: String,
    val subtitle: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val placement: String,
    val audit: CampaignAuditStub,
)

enum class CampaignHiddenReason {
    NO_CAMPAIGN,
    INVALID,
    NOT_STARTED,
    EXPIRED,
    AMBIGUOUS,
}

sealed interface CampaignResolution {
    data class Active(val campaign: ResolvedCampaign) : CampaignResolution
    data class Hidden(val reason: CampaignHiddenReason) : CampaignResolution
}

sealed interface CampaignPreview {
    data class Valid(val campaign: ResolvedCampaign) : CampaignPreview
    data object Invalid : CampaignPreview
}

class CampaignEngine {
    fun resolve(campaigns: List<CampaignInput>, now: Instant): CampaignResolution {
        if (campaigns.isEmpty()) return CampaignResolution.Hidden(CampaignHiddenReason.NO_CAMPAIGN)
        val valid = campaigns.mapNotNull(::validate)
        if (valid.isEmpty()) return CampaignResolution.Hidden(CampaignHiddenReason.INVALID)
        val active = valid.filter { campaign ->
            !now.isBefore(campaign.startsAt) && now.isBefore(campaign.endsAt)
        }
        return when (active.size) {
            0 -> CampaignResolution.Hidden(
                if (valid.any { now.isBefore(it.startsAt) }) {
                    CampaignHiddenReason.NOT_STARTED
                } else {
                    CampaignHiddenReason.EXPIRED
                },
            )
            1 -> CampaignResolution.Active(active.single())
            else -> CampaignResolution.Hidden(CampaignHiddenReason.AMBIGUOUS)
        }
    }

    fun preview(campaign: CampaignInput): CampaignPreview =
        validate(campaign)?.let(CampaignPreview::Valid) ?: CampaignPreview.Invalid

    private fun validate(campaign: CampaignInput): ResolvedCampaign? {
        return try {
            val uri = URI(campaign.httpsUrl)
            val startsAt = Instant.parse(campaign.startsAt)
            val endsAt = Instant.parse(campaign.endsAt)
            val valid = campaign.id.isNotBlank() &&
                campaign.id.codePointLength() <= MAX_ID_LENGTH &&
                campaign.kind in ALLOWED_KINDS &&
                campaign.title.isNotBlank() &&
                campaign.title.codePointLength() <= MAX_TITLE_LENGTH &&
                (campaign.subtitle?.codePointLength() ?: 0) <= MAX_SUBTITLE_LENGTH &&
                campaign.httpsUrl.toByteArray(StandardCharsets.UTF_8).size <= MAX_URL_BYTES &&
                uri.scheme == "https" &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                !uri.isOpaque &&
                endsAt > startsAt &&
                campaign.placement in ALLOWED_PLACEMENTS
            if (!valid) {
                null
            } else {
                ResolvedCampaign(
                    id = campaign.id,
                    kind = campaign.kind,
                    httpsUrl = campaign.httpsUrl,
                    title = campaign.title,
                    subtitle = campaign.subtitle,
                    startsAt = startsAt,
                    endsAt = endsAt,
                    placement = campaign.placement,
                    audit = CampaignAuditStub(
                        campaignId = campaign.id,
                        targetSha256 = sha256(campaign.httpsUrl),
                    ),
                )
            }
        } catch (_: DateTimeException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.codePointLength(): Int = codePointCount(0, length)

    private companion object {
        const val MAX_ID_LENGTH = 128
        const val MAX_TITLE_LENGTH = 160
        const val MAX_SUBTITLE_LENGTH = 500
        const val MAX_URL_BYTES = 2_048
        val ALLOWED_KINDS = setOf("donation", "website", "telegram", "schedule", "contacts", "custom")
        val ALLOWED_PLACEMENTS = setOf("always", "with_prayer_times", "rotation")
    }
}

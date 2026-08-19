# MAWAQIT data-access research

**Research date:** 2026-08-19  
**Purpose:** distinguish MAWAQIT's own client/API model from third-party HTML scraping and decide whether it is a suitable source for this project.

## Evidence labels

- `CONFIRMED_OFFICIAL`: MAWAQIT-owned website, help center, PyPI owner or GitHub organization.
- `CONFIRMED_THIRD_PARTY`: independently maintained package/repository that describes its own behavior.
- `INFERENCE`: architectural conclusion drawn from those sources.

## Current finding

`CONFIRMED_OFFICIAL`: MAWAQIT now publishes an official async Python client under the MAWAQIT GitHub organization and verified PyPI owner. The current client:

1. accepts a MAWAQIT account login/password or an existing API token;
2. obtains an API access token after login;
3. searches mosques by coordinates or keyword;
4. selects a mosque by UUID;
5. fetches mosque information and a full-year prayer-time calendar as JSON.

This means the up-to-date situation is more nuanced than “MAWAQIT has no API”:

- there is an official account-authenticated API client;
- it is not an anonymous, generally documented public API contract;
- the older help-center article saying the API is private remains useful as a warning that arbitrary third-party use is not automatically authorized;
- access to the client code under an open-source license does not by itself grant unrestricted rights to reuse or redistribute MAWAQIT's prayer-time data.

For production use, obtain written confirmation covering account automation, rate limits, data redistribution, attribution, caching, commercial/non-commercial use and revocation policy.

## Product-side data model

MAWAQIT publicly documents two timing modes for a mosque:

```text
automatic calculation
OR
annual calendar
```

It also supports annual CSV/PDF export and manual CSV modification/upload. The TV application can operate from cached data after setup and refresh when configuration changes. Combined with the official client's full-year calendar response, the likely high-level flow is:

```text
mosque administrator chooses calculation or calendar
                    ↓
          MAWAQIT central mosque record
                    ↓
       authenticated API / first-party TV sync
                    ↓
              local offline cache
```

The exact first-party TV polling cadence and storage schema were not reverse engineered and remain `UNKNOWN`.

## Why third-party projects sometimes scrape MAWAQIT

`CONFIRMED_THIRD_PARTY`: several independent integrations read public MAWAQIT pages or embedded page data and convert it into JSON/calendar data. Some add TTL caching or a small proxy. This exists because:

- anonymous public pages are easy to consume;
- browser CORS can block direct client calls;
- older or unauthenticated integrations may not use the official account-token client;
- consumers may want only a small subset of fields.

This does **not** mean the MAWAQIT TV application itself continuously scrapes HTML. It means scraping is one unofficial adapter technique in the surrounding ecosystem.

## Recommendation for this repository

Do not make MAWAQIT a hidden upstream dependency for the Russian pilot.

Treat it as one optional provider only after permission:

```text
provider kind: partner_api
credentials: backend secret store only
scope: explicitly approved mosques
retrieval: backend only
cache: full effective calendar/snapshot
publication: normalize → validate → approve → sign
TV: never receives MAWAQIT credentials and never calls MAWAQIT directly
```

Do not scrape MAWAQIT as a fallback merely because the official API is authenticated. If permission is unavailable, use the pilot mosque's own approved calendar or a directly authorized DUM source.

## Sources

- MAWAQIT official Python client repository: https://github.com/mawaqit/mawaqit-py
- MAWAQIT verified PyPI project: https://pypi.org/project/mawaqit/
- MAWAQIT prayer-time product: https://mawaqit.net/en/prayer-times
- MAWAQIT API help article: https://help.mawaqit.net/en/articles/11991838-can-i-use-your-api
- MAWAQIT offline TV article: https://help.mawaqit.net/en/articles/11791418-does-the-mawaqit-app-work-offline
- MAWAQIT annual export article: https://help.mawaqit.net/en/articles/8999582-how-to-download-the-prayer-times-of-my-mosque
- MAWAQIT manual calendar article: https://help.mawaqit.net/en/articles/11813326-how-to-manually-adjust-prayer-times-shuruq-in-mawaqit

Third-party examples are research evidence only and are not endorsed dependencies:

- https://github.com/mrsofiane/mawaqit-api
- https://pypi.org/project/py-mawaqit/
- https://almunadi.net/

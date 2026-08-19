# How prayer-time applications obtain data

## Direct answer: is it constant parsing?

Usually **no**. Mature prayer-time applications rarely parse authority websites continuously on the TV or phone. The common production patterns are:

1. local astronomical calculation;
2. calculation through an API;
3. mosque-managed annual timetable synchronized from a backend;
4. a hybrid downloadable parameter/timetable package;
5. scheduled server-side parsing/import only when an authority offers no machine-readable format.

The client normally caches a month, a year or a versioned parameter bundle and keeps working offline.

## Verified product examples

| Product | Publicly documented/static model | Does every TV continuously parse authority sites? |
|---|---|---|
| IslamApp для Мечети 1.6.2 | `STATIC`: bundled regional GeoJSON policy/timetable snapshot plus a periodically replaceable snapshot; local lookup/calculation | No |
| MAWAQIT | `PUBLIC/OFFICIAL_CODE`: mosque chooses automatic calculation or an annual calendar; administrators can export yearly CSV/PDF; TV/Box caches data offline; an official account-authenticated Python API client can fetch a full-year calendar | No |
| ConnectMazjid | `PUBLIC`: mosque administrator edits prayer/iqamah data once and the service synchronizes TV, mobile and web clients | No |
| mySalah | `PUBLIC`: selectable static schedules from central mosques/authorities alongside automatic calculation | No evidence of client scraping; static schedule is the stated model |
| Tarjiha | `PUBLIC`: server-side calculation using mosque-selected method, then on-device cache; mosque controls iqamah rules and exceptions | No |
| AlAdhan | `PUBLIC`: remote JSON calculation API for day/month/year; documentation warns results may differ from a local authority | No HTML parsing |

The implementation details of products other than the supplied IslamApp APK were not reverse engineered. Their rows above are limited to their own public documentation.

## Pattern A — local calculation

### How it works

```text
coordinates + local date + timezone + method parameters
                         ↓
                 calculation library
                         ↓
             Fajr/Sunrise/Dhuhr/Asr/Maghrib/Isha
```

Examples:

- Pillars states that location-based calculation is performed locally on the phone and location is not sent to its servers.
- BatoulApps Adhan is a tested Kotlin library that calculates from coordinates, date and method parameters and supports madhhab, high-latitude rules and minute adjustments.

### Advantages

- works completely offline;
- no source website dependency;
- cheap and fast;
- deterministic and testable.

### Limitations

- a generic method can differ from the timetable followed by a specific mosque or regional authority;
- high-latitude and seasonal rules are not reducible to one global angle;
- “method named after an authority” is not the same as receiving an approved official timetable.

Use this pattern as a fallback or approved calculation policy, not as automatic proof of official status.

## Pattern B — calculation API

AlAdhan exposes daily, monthly, annual and range endpoints. A request supplies coordinates/address/city and method parameters; the service returns JSON. It supports method 14, named “Spiritual Administration of Muslims of Russia,” Hanafi/Shafi selection and tuning offsets. Its own documentation warns that returned times may not match a local mosque or authority because local values can be adjusted.

Typical client behavior:

```text
one API request for month/year
        ↓
cache response locally
        ↓
show daily rows offline
        ↓
refresh only when coverage/version changes
```

This is not HTML scraping. It is an API-backed calculation service.

## Pattern C — mosque-managed central timetable

### MAWAQIT

MAWAQIT lets a mosque maintain/export a full yearly calendar, including CSV/PDF paths for administrators. Its TV app is documented to work offline after initial setup and to use cached data, reconnecting for changes. That strongly indicates a central mosque record synchronized to clients rather than permanent parsing on each TV.

The current official MAWAQIT Python client adds an important 2026 detail: after account login/token acquisition it can search mosques and fetch a mosque's full-year calendar as JSON. This is an account-authenticated first-party API path, not an anonymous public contract. An older help article still describes the API as private, so production reuse requires explicit permission and terms rather than assuming that an open-source client grants data-redistribution rights. See [MAWAQIT_API_RESEARCH.md](MAWAQIT_API_RESEARCH.md).

### My-Masjid

My-Masjid lets each mosque manage exact adhan and iqamah times and publishes the same data to mobile and large screens. Its browser display can continue offline once loaded, and its Android TV app supports reboot launch, screen-on and D-pad navigation.

Typical architecture:

```text
mosque administrator edits/imports timetable
                     ↓
           central authoritative record
                     ↓
       mobile / TV / browser clients synchronize
                     ↓
               local offline cache
```

For mosque displays, this is often more appropriate than consumer-app calculation because iqamah and Jumu'ah are local operational decisions.

## Pattern D — downloadable regional policy/timetable package

The uploaded `IslamApp для Мечети 1.6.2` APK uses this pattern.

```text
bundled + remotely replaceable GeoJSON snapshot
          ↓
coordinate-in-polygon policy selection
          ↓
exact TimeTable OR Dynamic calculation parameters
          ↓
local calculation/lookup and offline display
```

The package is checked periodically rather than fetching daily times per city. This combines low network use with support for regional special rules and exact-city timetable overrides.

## Pattern E — scheduled server-side parsing/import

Parsing is reasonable only when an authoritative organization publishes data exclusively as HTML, PDF or spreadsheet and grants or permits its use.

Correct flow:

```text
scheduled backend fetch
    ↓
store immutable raw response/file + headers + hash
    ↓
provider-specific parser
    ↓
normalize
    ↓
validate invariants and compare with previous version
    ↓
manual approval for material changes
    ↓
signed published snapshot
    ↓
TV downloads only the snapshot
```

Wrong flow:

```text
TV opens authority HTML every day
→ CSS selector breaks
→ missing values silently become calculation
→ screen continues showing unverified data
```

## How often should parsing/sync occur?

There is no universal cadence. Start with the source's change model:

| Source type | Suggested default | Actual download behavior |
|---|---|---|
| Annual official XLSX/PDF/CSV | conditional check daily or weekly during publication season; otherwise weekly | download only when ETag/Last-Modified/hash changes |
| Official daily/30-day HTML table | 1–4 conditional checks per day if permitted | parse only changed content |
| Official API with version/ETag | every 6–24 hours | no payload download on `304 Not Modified` |
| Mosque-owned admin calendar | push or short polling for manifest; daily safety refresh | download only a new approved version |
| Calculation profile bundle | weekly or on app start with long backoff | versioned artifact only when changed |
| Emergency override | push plus short safety poll | small separately signed update |

These are operational starting points, not religious rules. The authoritative organization may publish only once a year, while a mosque may change iqamah weekly.

## Why the TV should not be the parser

- HTML/PDF changes would break every installed screen at once.
- Source attribution and approval become hard to audit.
- TV hardware and network are unreliable.
- parsing dependencies increase APK size and attack surface;
- authority sites may rate-limit or forbid automated use;
- a bad page response could replace correct cached data.

The backend should absorb source variability; the TV should consume one stable contract.

## Recommended source hierarchy for our product

Hierarchy is configured **per mosque**, not globally:

```text
1. exact timetable approved by the pilot mosque
2. official regional source selected by that mosque
3. official central/federal source selected by that mosque
4. approved manual import
5. pre-approved calculation profile
6. no fallback / explicit error state
```

The system must not silently jump from level 1 to level 5.

## Provider types in the Go backend

```text
official_api
  HTTP JSON/XML from a named authority

official_file
  CSV/XLSX/PDF or other published artifact

official_html
  documented server-side parser with fixtures

mosque_calendar
  values edited/imported and approved by mosque staff

calculation_profile
  coordinates/polygon + parameters + approval scope

manual_import
  operator-supplied artifact with approval and audit
```

Each provider returns both raw evidence metadata and normalized rows.

## Recommended synchronization contract

TV request:

```http
GET /v1/devices/{device_id}/manifest
If-None-Match: "manifest-etag"
```

Manifest contains:

- latest approved snapshot ID/version;
- payload URL;
- payload SHA-256;
- Ed25519 signature and key ID;
- schema version;
- effective range;
- minimum compatible app version;
- emergency override version.

TV behavior:

1. keep rendering current local snapshot;
2. fetch manifest opportunistically;
3. on `304`, do nothing;
4. on a new version, download to a temporary file;
5. validate schema, hash, signature, timezone and date coverage;
6. import transactionally;
7. activate only after all checks pass;
8. keep previous version for rollback.

## Recommendation for the first pilot

Do not start with a nationwide scraper. Obtain one approved annual/monthly table from the first mosque, import it through `manual_import` or `official_file`, and prove:

- correct display;
- midnight rollover;
- iqamah rules;
- offline operation;
- signed atomic update;
- source/freshness diagnostics.

Only then build the first authority-specific adapter.

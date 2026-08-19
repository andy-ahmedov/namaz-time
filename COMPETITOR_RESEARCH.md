# COMPETITOR_RESEARCH.md

## Evidence convention

- `PUBLIC`: official product/store/help documentation.
- `STATIC`: clean-room static analysis of the user-supplied IslamApp APK.
- `INFERENCE`: likely architecture, not directly proven.

## Comparison matrix

| Product / approach | Prayer-time source model | Mosque-specific iqamah | Offline | TV/admin pattern | What to borrow | What not to assume |
|---|---|---:|---:|---|---|---|
| IslamApp для Мечети | `STATIC`: bundled + periodically replaceable regional package; polygon selects either timetable or dynamic calculation profile | yes: fixed or offset | yes by design | Android TV app; local settings; best-effort boot | compact offline package, nested geographic overrides, atomic last-known-good update | dataset provenance and official contracts are not embedded in the observed data |
| IslamApp mobile | `PUBLIC`: works without internet and uses mosque times where possible | consumer-oriented | yes | mobile app | hybrid schedule + calculation idea | public wording does not reveal exact backend or approvals |
| MAWAQIT | `PUBLIC/OFFICIAL_CODE`: automatic calculation or mosque annual calendar; official account-authenticated client fetches mosque/year data | yes | product/box support offline modes | central dashboard, API-backed sync, TV/mobile ecosystem | explicit calculation-vs-calendar choice, moderation, annual CSV and authenticated integration | client license does not automatically grant prayer-data redistribution rights |
| My-Masjid | `PUBLIC`: mosque centrally manages exact adhan/iqamah | yes | browser display can continue after load | cloud admin + large-screen/Android TV | mosque-owned values, messages, D-pad/restart features | real-time sync still needs a local cache and failure policy |
| ConnectMazjid | `PUBLIC`: one admin portal synchronizes prayer times to TV/mobile/web | yes | not sufficiently specified publicly | multi-client SaaS | one update, many displays; operator simplicity | “instant” marketing wording is not a reliability guarantee |
| mySalah | `PUBLIC`: static schedules from central mosques/authorities plus automatic calculation methods | consumer app | likely local schedule | mobile | explicit static-vs-calculated distinction | store description alone does not prove update cadence or provenance schema |
| Tarjiha | `PUBLIC`: server-side calculation selected per mosque; device schedule cache | committee-defined fixed/offset rules and exceptions | yes | mosque management + mobile | explicit per-mosque method and offline schedule cache | a calculated profile is still not an official Russian DUM timetable |
| AlAdhan API | documented calculation API; daily/monthly/annual endpoints and configurable method/tuning | no mosque-local iqamah by itself | only if consumer caches | remote API | useful benchmark/onboarding/calculation fallback | method `14` does not guarantee exact schedule of every Russian mosque |
| Adhan library / local calculation | coordinates + date + method/angles/madhab/high-latitude rules | no | fully | embedded library | deterministic calculation and testability | calculation is not automatically “official” |
| Controlled HTML/file adapter | periodic backend import from approved official source | separate | TV remains offline-capable | server-side ingest | fills gap where authority has no API | must not run on TV or silently survive schema drift |

## MAWAQIT API status, August 2026

MAWAQIT's 2025 help article says its API is private, but the MAWAQIT organization now publishes an official Python client whose current version logs in for an API token, searches mosques and fetches a full-year calendar. The correct conclusion is not “no API”; it is “authenticated first-party API exists, while anonymous/public third-party rights and service terms are not established.” Details: [MAWAQIT_API_RESEARCH.md](MAWAQIT_API_RESEARCH.md).

## IslamApp findings that materially change our design

The competitor is not a thin website wrapper. The supplied APK contains an offline regional ruleset and supports a separate downloadable replacement. Coordinates are matched against geographic polygons; more specific regions override broader ones. The winning profile is either:

- a 366-row daily timetable; or
- a dynamic calculation configuration with seasonal date ranges, jurisprudential method and per-prayer adjustments.

That explains how the app can support city/region selection and remain usable without requesting an official website every day. Details: [APK_RESEARCH_ISLAMAPP_1_6_2.md](APK_RESEARCH_ISLAMAPP_1_6_2.md).

## Product patterns worth adopting

### 1. Offline-first snapshot

Adopt the update mechanics, not the proprietary payload: one versioned artifact, conditional HTTP request, staging file, validation, atomic replace and last-known-good fallback.

### 2. Mosque authority as a first-class actor

MAWAQIT, My-Masjid and ConnectMazjid all expose a mosque-admin model. This is more appropriate for iqamah, Jumu'ah and one-off changes than trying to infer them from city calculation.

### 3. Multiple source strategies

A mature product needs both:

- exact approved timetable for places where one exists;
- calculated profile for uncovered places, clearly labeled and approved.

### 4. Annual schedule workflows

Annual CSV/JSON is operationally simple, reviewable and offline-friendly. It should be the first production provider, even if APIs are added later.

### 5. TV-specific operation

Useful baseline features repeated across products:

- D-pad navigation;
- readable 10-foot layout;
- keep-screen-on;
- restart/autostart options;
- announcements;
- backgrounds/themes;
- QR campaign;
- prayer/iqamah countdown;
- remote management after MVP;
- offline cache.

## Gaps in competitors that our product should address

- visible provenance and approval status;
- signed immutable snapshots;
- clear stale/expired state instead of silent fallback;
- robust rollback;
- deterministic timezone handling independent of device configuration;
- explicit distinction between adhan and iqamah;
- audit trail for time-affecting changes;
- parser schema-drift protection;
- privacy-minimal local mode;
- documented managed-kiosk deployment.

## Clean-room boundary

Competitor behavior may inform requirements. Do not copy its extracted data, embedded secrets, resource files, code, iconography, branding, exact wording or pixel layout. Build only from the independent specifications in this repository.

## Public references

See [SOURCES.md](SOURCES.md) for store pages, official product documentation and platform documentation used in this comparison.

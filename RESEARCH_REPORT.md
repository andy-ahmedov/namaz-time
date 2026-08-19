# Research report: mosque prayer-time TV application

**Research date:** 2026-08-19  
**Scope:** IslamApp public behavior, supplied screenshots, clean-room static APK analysis, prayer-time acquisition patterns in comparable applications, official Russian timetable sources, and a development package for Codex.

## 1. Final answer in one paragraph

The uploaded APK confirms that IslamApp solves prayer times through an offline-first hybrid bundle, not through permanent parsing of DUM pages on every television. It ships and periodically replaces a geographic package containing either exact annual tables or regional calculation profiles. The selected coordinates choose the most specific polygon; the app then performs a local lookup/calculation and applies separately stored iqamah rules. Comparable products use local calculation, calculation APIs, mosque-managed calendars or cached central data. Parsing is only one source-adapter technique and belongs on a backend with raw artifact storage, validation, approval and signed snapshot publication.

## 2. What public sources establish

`CONFIRMED_PUBLIC`:

- IslamApp for Mosque is designed for TVs in mosques.
- It displays five obligatory prayers, sunrise and optionally Jumu'ah, Tahajjud, Duha and midnight.
- The store says the app already contains exact times for many mosques/countries and asks users to contact support when local values differ.
- RuStore lists version 1.6.2 dated 9 July 2026 and Android 9 minimum.
- Sber lists operation on Salute TV and SberBox devices and voice launch commands.
- The phone IslamApp says it can provide times without Internet and use mosque times where possible.

The public pages do not document a nationwide live DUM API.

## 3. What the supplied screenshots establish

`CONFIRMED_PUBLIC` from user-provided photographs:

- selected settlement shown in settings;
- separate iqamah settings;
- appearance/theme section;
- language selection;
- fundraising QR configuration;
- auto-start setting;
- URL, title, subtitle and preview for QR;
- next-prayer countdown;
- current date/time;
- QR campaign card and bottom informational text.

The screenshots are product evidence, not permission for pixel-perfect duplication.

## 4. What the APK newly establishes

`CONFIRMED_STATIC`:

- native Android TV packaging and Compose/TV-related dependencies;
- bundled encrypted prayer-parameter snapshot;
- remotely replaceable snapshot from a dedicated download path;
- GeoJSON features and coordinate-in-polygon matching;
- exact timetable and dynamic calculation profile variants;
- local calculation fallback;
- six-day periodic WorkManager update request with constraints;
- atomic temp-file replacement and Last-Modified handling;
- local iqamah fixed/offset model;
- local QR rendering;
- bundled paired landscape/portrait themes;
- boot receiver-based best-effort autostart;
- OSM/Android/Google/Huawei location components.

The complete technical report is [APK_RESEARCH_ISLAMAPP_1_6_2.md](APK_RESEARCH_ISLAMAPP_1_6_2.md).

## 5. The prayer-time data question

### Not one universal technique

| Product/source pattern | Where values come from | Network need |
|---|---|---|
| Pillars-style local app | coordinates + local algorithm | none after location/config |
| Adhan library | local parameters and equations | none |
| AlAdhan API | remote calculation API | request, then cache |
| MAWAQIT | mosque-admin calendar/calculation in central platform; official account-token client can fetch a full-year calendar | authenticated sync/setup/changes; TV cached offline |
| My-Masjid | mosque-managed exact adhan/iqamah | sync, then browser/TV cache |
| IslamApp TV | downloaded regional profile/timetable package + local logic | periodic package update |
| DUM HTML/PDF/XLSX | authority-published artifact | scheduled server-side ingest |

Therefore “парсинг или API?” is the wrong binary. A robust service supports multiple provider kinds behind one normalized model.

### Current MAWAQIT API nuance

MAWAQIT's older help text says its API is private, while its current official GitHub/PyPI client authenticates a normal account, obtains a token and fetches mosque/year-calendar JSON. These facts are compatible: an authenticated first-party API exists, but it is not an unrestricted anonymous public contract. Use therefore depends on written permission, service/data terms and redistribution rights. See [MAWAQIT_API_RESEARCH.md](MAWAQIT_API_RESEARCH.md).

## 6. Official Russian sources

### DUM RF

The official homepage exposes today's Moscow times and requires attribution when site materials are used. A public nationwide machine-readable city API was not found during this research. A provider would therefore need either permission/access from DUM RF or a carefully governed page/file adapter.

### DUM Republic of Tatarstan

The official timetable section publishes a city/district selector, a rolling table and a downloadable annual schedule. This is suitable for an `official_file` or permitted `official_html` adapter. Regional seasonal rules are important; a generic global calculation method cannot safely replace the published timetable.

### Central Spiritual Administration of Muslims of Russia

CDUM publishes annual/static timetable pages for a limited set of cities. Treat each explicit page as a scoped source, not a nationwide API.

### Required legal/organizational step

Before production ingestion, contact the relevant authority or pilot mosque and clarify:

- whether automated retrieval is allowed;
- preferred machine-readable format;
- attribution text;
- update notification method;
- geographic scope;
- who approves corrections;
- whether redistribution to screens/API is permitted.

## 7. Recommended product architecture

```text
authority / mosque / calculation profile
             ↓
       provider adapter (Go)
             ↓
immutable raw artifact + metadata + hash
             ↓
normalization → validation → diff
             ↓
operator/religious approval
             ↓
signed versioned prayer snapshot
             ↓
manifest + ETag + HTTPS
             ↓
Android TV transactional import
             ↓
Room/SQLite last-known-good
             ↓
Compose TV display
```

Backend is a modular monolith until scale proves otherwise.

## 8. Data-source policy

For every source, store:

- kind and canonical identifier;
- authority name and scope;
- canonical URL/file origin;
- retrieval time and HTTP metadata;
- immutable raw SHA-256;
- parser/normalizer version;
- effective date range;
- timezone;
- licensing/attribution requirements;
- approval state, actor and timestamp;
- published snapshot ID/version.

For each daily row, preserve source revision and validation flags.

## 9. Snapshot security

The APK's encrypted bundle proves that bundling data is operationally useful, but a symmetric key inside a client is not a trust boundary. Our snapshots should use:

- TLS transport;
- canonical payload bytes;
- SHA-256 payload hash;
- Ed25519 signature;
- rotatable `key_id`;
- schema and compatibility version;
- transactional activation;
- previous-version rollback.

## 10. MVP boundary

MVP:

- one pilot mosque;
- approved CSV/JSON import;
- five prayers + sunrise;
- separate iqamah fixed/offset rules;
- date/time and countdown;
- landscape TV screen;
- one/few original backgrounds;
- local QR;
- offline Room snapshot;
- D-pad settings;
- source/freshness diagnostics;
- best-effort launch after boot on pilot hardware.

Not MVP:

- all-Russia scraping;
- payment processing;
- consumer mobile app;
- video backgrounds;
- microservices;
- AI religious decisions;
- pretending that an unapproved calculation is official.

## 11. Remaining mandatory runtime research

The environment used for this package has no ADB, Android SDK/emulator or `/dev/kvm`, and no physical TV was attached. Runtime testing therefore remains pending. The next experiment requires a controlled Android TV/box and follows [BLACK_BOX_VALIDATION_PLAN.md](BLACK_BOX_VALIDATION_PLAN.md).

Static research is complete enough to start our own clean-room vertical slice; it is not complete enough to claim every runtime detail of IslamApp.

## 12. Repository package outcome

This package now contains:

- Codex rules and bounded tasks;
- APK findings and sanitized evidence;
- source acquisition strategy;
- product requirements;
- architecture/data/API contracts;
- JSON Schemas and OpenAPI skeleton;
- test/security/operations plans;
- runtime validation plan;
- source partnership checklist;
- documentation validation command.

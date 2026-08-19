# Clean-room static research: IslamApp для Мечети 1.6.2

## Executive conclusion

`CONFIRMED_STATIC`: the supplied APK does not continuously scrape DUM websites on the TV and does not need an online call for every displayed prayer time. Its prayer-time subsystem is a hybrid offline-first design:

```text
bundled encrypted regional snapshot
          +
periodically downloaded replacement snapshot
          ↓
coordinates → containing geographic features → most specific feature
          ↓
TimeTable profile OR Dynamic calculation profile
          ↓
local daily calculation / lookup
          ↓
locally stored iqamah rules → TV display
          ↓
MWL calculation fallback when no profile matches
```

This is the most useful architectural discovery from the APK. It explains how the product supports many cities, special regional rules and offline operation without constant page parsing.

## Scope and evidence level

### Artifact

- File supplied by the user: `IslamApp для Мечети_1.6.2_APKPure.apk`.
- Package: `islam.islamapp.masjid`.
- `versionCode`: `62`.
- `versionName`: `1.6.2`.
- File size: `63,615,043` bytes.
- SHA-256: `eeaf087c9ed2a5e69375e60466f3c893439d5eb11a14d96913e2e0a1337d899e`.

### Trust caveat

The file came from APKPure, not directly from RuStore/Google Play. Its package/version agree with the current RuStore entry, but this analysis does **not** prove byte-for-byte identity with an official-store APK. The observed certificate cannot be compared with an official copy until such a copy is obtained lawfully.

### Method

Performed:

- ZIP/APK integrity inspection;
- binary AndroidManifest decoding;
- DEX string/class/method inspection;
- focused bytecode disassembly around prayer parameters, updates, location, QR, iqamah, themes and autostart;
- aggregate/schema analysis of the bundled parameter artifact;
- certificate/signing metadata inspection;
- comparison of selected public timetable values against official public pages.

Not performed:

- application execution;
- UI walkthrough on an Android TV/box;
- HTTPS interception;
- TLS bypass or pinning bypass;
- authentication bypass;
- modification/repackaging of the APK;
- publication of the embedded secret, decrypted full dataset or proprietary resources.

All runtime statements remain `UNKNOWN` unless independently confirmed later.

## 1. Android TV packaging

`CONFIRMED_STATIC`:

- minimum Android API: 28 / Android 9;
- target and compile SDK: 37;
- `android.software.leanback` is required;
- touchscreen is explicitly not required;
- main activity exposes both `LAUNCHER` and `LEANBACK_LAUNCHER`;
- app class: `islam.islamapp.masjid.MyApplication`;
- main activity: `islam.islamapp.masjid.presentation.MainActivity`.

This matches RuStore's Android 9 minimum and the public TV positioning.

## 2. Prayer-time parameter package

### Bundled and remotely replaceable data

`CONFIRMED_STATIC`:

- APK contains an asset named `Params`.
- The app may also use `filesDir/Params`, downloaded after installation.
- It chooses the downloaded file only when it is newer than the bundled build timestamp; otherwise it keeps the bundled asset.
- The data is encrypted with AES-GCM and decrypted locally.
- The decryption material is necessarily present in the application binary. It is not reproduced in this repository.

Security lesson: client-side symmetric encryption can hide casual inspection, but it cannot prove who authored a snapshot. Our application should use a digital signature, such as Ed25519, and keep only a public verification key on the TV.

### Aggregate structure

The decrypted content was inspected only to understand architecture and schema. The full data is not redistributed.

`CONFIRMED_STATIC` aggregate facts:

| Property | Value |
|---|---:|
| Container | GeoJSON `FeatureCollection` |
| Geographic features | 139 |
| `Dynamic` calculation profiles | 127 |
| `TimeTable` profiles | 12 |
| Features with `iso_code=RU` | 66 |
| MultiPolygon geometries | 74 |
| GeometryCollection geometries | 41 |
| Polygon geometries | 24 |
| TimeTable rows in each timetable feature | 366 |
| Gregorian profiles | 125 |
| Umm al-Qura calendar profiles | 2 |

Every feature includes approximately:

```text
iso_code
level
name
osm_id
geometry
prayer_params
```

The embedded schema does **not** contain explicit fields such as authority name, source URL, license, retrieval timestamp, effective range, approval actor or raw checksum. Therefore the dataset alone does not prove that any row is officially synchronized with a named DUM.

## 3. Selection by geographic polygon

`CONFIRMED_STATIC`:

1. The selected coordinates are tested against GeoJSON geometries.
2. All matching features are sorted by descending `level`.
3. The first, most specific match is used.
4. A city-level timetable can override a wider oblast calculation profile.
5. When no feature supplies parameters, the code falls back to a Muslim World League calculation profile.

This is more robust than matching on a localized city string. It also explains why the app can ship a broad country profile while overriding specific cities or regions.

### Clean-room lesson

For our product, geographic polygons can select a **calculation policy**, but they must not themselves imply authority. Keep these concerns separate:

```text
geographic_scope → which policy applies
source_record     → who approved/provided it
published_snapshot → exact data delivered to a mosque/device
```

## 4. Two prayer-time modes

### `TimeTable`

A timetable profile maps month/day keys to six explicit times. It is suitable when an authority or mosque publishes exact annual values.

Representative static observation: the exact Moscow city feature contains 366 rows. Its row for 19 August is:

```text
Fajr 02:23
Sunrise 05:04
Dhuhr 12:38
Asr 16:31
Maghrib 20:01
Isha 22:05
```

The official DUM RF homepage displayed the same six Moscow values on 19 August 2026. This is strong correlation, but it still does not prove a contractual feed, source attribution or update path.

### `Dynamic`

A dynamic profile stores one or more date ranges with parameters including:

- location/coordinates;
- Fajr angle;
- Maghrib angle where applicable;
- Isha angle or fixed offset behavior;
- juristic method for Asr;
- high-latitude adjustment;
- fixed Dhuhr time where applicable;
- per-prayer minute offsets;
- special fixed-offset configuration.

The calculation is local and includes common PrayTimes-style astronomical operations such as solar position, Julian date, Asr calculation, high-latitude adjustment and per-prayer tuning.

### Representative regional behavior

`CONFIRMED_STATIC`, summarized rather than copied as a dataset:

- **Ulyanovsk region:** a winter profile uses angle-based Fajr/Isha and Hanafi Asr; a summer date range switches to fixed minute relationships around sunrise/sunset; then it returns to the winter profile.
- **Tatarstan:** a regional dynamic profile uses Hanafi Asr and fixed Dhuhr, with a summer range where Fajr/Isha are expressed through minute offsets instead of ordinary twilight angles.
- **Moscow:** exact city timetable overrides a broader Moscow Oblast dynamic profile.
- **Ufa/Bashkortostan, Dagestan and other regions:** separate profiles with their own angles, madhhab and offsets exist.
- **Chechnya and Ingushetia:** full annual timetable features are present.

These examples show why a single `method=Russia` flag is insufficient for mosque-grade accuracy across Russia.

## 5. Parameter update flow

### Schedule

`CONFIRMED_STATIC`:

- periodic work is enqueued when the prayer-time facade observes activity start;
- unique work name: `prayerparams:periodicwork`;
- first intended window is the next local 06:00;
- repeat interval is six days;
- the requested flex interval is one minute, but the included WorkManager implementation clamps it to its five-minute minimum;
- constraints require network connectivity, charging and non-low storage;
- a separate one-shot work item named `prayerparams:work` exists and does not require charging.

WorkManager is intentionally inexact. The six-day value therefore means a desired periodic cadence, not an exact server poll at a guaranteed timestamp.

### HTTP and atomic replacement

`CONFIRMED_STATIC` update behavior:

1. request a versioned parameter artifact from a dedicated download host/path;
2. send `Accept-Encoding: identity`;
3. use HTTP caching and a short request cache policy;
4. accept cached/no-change responses;
5. write response to `filesDir/Params.tmp`;
6. stream and synchronize the file;
7. verify Content-Length when available;
8. atomically move/replace it as `filesDir/Params`;
9. apply server `Last-Modified` time;
10. trigger recalculation/refresh;
11. retry server/transient failures while distinguishing HTTP error classes.

This is a solid last-known-good pattern. Improvements for our design:

- signed manifest and snapshot;
- explicit schema version;
- payload SHA-256;
- minimum/maximum compatible client version;
- source and approval metadata;
- rollback slot;
- coverage expiry and stale-state UI;
- no client-embedded symmetric trust secret.

## 6. Location and city selection

`CONFIRMED_STATIC`:

- OSM Nominatim `/search` and `/reverse` are present;
- Android Geocoder and Google/Huawei location integrations are also present;
- placemark, coordinates and timezone are persisted locally;
- the OSM helper serializes/rate-limits calls with a minimum delay around 1.5 seconds.

The public Nominatim service has strict usage requirements. A production app should not build uncontrolled autocomplete or periodic/bulk geocoding directly into every TV. Better options:

- manually curated city/mosque catalog;
- backend proxy with cache and switchable provider;
- self-hosted Nominatim for scale;
- commercial geocoder with suitable terms;
- geocoding only as a user-triggered setup action.

Precise location is unnecessary after the mosque is paired or coordinates are explicitly selected.

## 7. Iqamah model

`CONFIRMED_STATIC` core model:

```text
IqamaParam.FixedTime(LocalTime)
IqamaParam.Offset(long)
```

This confirms a useful MVP model: each prayer can have either a fixed iqamah time or an offset from adhan. The inspected core did not prove a full rule engine for weekday/date-range/one-off precedence.

Our product should extend the model deliberately:

```text
one-off date override
→ date-range + weekday rule
→ seasonal rule
→ base fixed/offset rule
→ no value
```

## 8. QR fundraising

`CONFIRMED_STATIC` data model includes:

```text
url
shortUrl
title
subtitle
alwaysShow
showWithPrayerTimes
```

The QR is generated locally by a QR rendering library. The configured target can be wrapped by an IslamApp donation redirect URL. A Firebase Remote Config flag related to short links is present; exact runtime semantics remain `UNKNOWN`.

Clean-room recommendation:

- generate QR locally;
- use direct HTTPS targets by default;
- only use our own redirect service when audit/rotation is required;
- maintain URL change history;
- validate scheme, domain and campaign dates;
- preview on the actual TV and test scan distance;
- never process payments inside the TV app.

## 9. Themes and orientation

`CONFIRMED_STATIC`:

- 14 bundled 4K PNG backgrounds are present: seven landscape/portrait pairs;
- image-theme models carry separate landscape and portrait resource IDs;
- theme metadata includes tints/palettes/borders and a blur-related option;
- orientation/onboarding code supports horizontal and vertical use.

The bundled backgrounds are proprietary and are not included in this repository. Our own product should use licensed or original assets, process them to device-appropriate sizes and maintain a safe fallback.

No static proof was found that this build lets operators upload arbitrary custom backgrounds. Treat custom upload as our product proposal, not a copied confirmed feature.

## 10. Autostart

`CONFIRMED_STATIC`:

- exported direct-boot-aware receiver handles `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED`;
- a persisted `autostart_app_enabled` setting exists;
- launch is gated by `Settings.canDrawOverlays(context)`;
- the receiver starts the main activity as a new/cleared/single-top task;
- vendor quick-boot permission is declared.

This is best-effort autostart and remains dependent on OEM behavior and permissions. It is not a full managed kiosk. Reliable dedicated signage requires a separate deployment path using device-owner/DPC or an EMM with lock-task mode.

## 11. Manifest permissions and SDKs

Observed permissions include boot, overlay, coarse/fine location, Internet, notifications, full-screen intent, foreground service, wake lock, screen-capture detection, legacy external storage, network/Wi-Fi state and vendor integrations.

The binary also contains Firebase Analytics, Crashlytics, Remote Config, Sessions and Installations, plus Google/Huawei location and map dependencies.

Important limitation: dependency/permission presence is not proof that every capability executes or that user data is collected. Google Play currently declares no data collection, while RuStore lists data/functions the app may request. Only controlled runtime inspection and developer documentation can resolve this fully.

For our local display mode, avoid contacts, advertising identifiers and continuous precise location. Use minimal telemetry or no telemetry until the operational requirement is explicit.

## 12. Signing metadata

`CONFIRMED_STATIC`:

- APK Signature Scheme v2 is present;
- certificate is self-issued for an IslamApp identity;
- certificate SHA-256: `937ebb29a40f8e3193e6c29ebcfd842f37c6df09a28aeb00c3eea22ade2dd0ee`;
- validity spans 2023-11-16 through 2048-11-09.

This does not prove the APKPure artifact is signed by the same key as every official-store build. Record the official certificate fingerprint during a later store installation and compare before trusting supply-chain identity.

## 13. What remains unknown

Runtime validation is still required for:

- exact location-search UI and fallback order;
- whether the parameter update request occurs immediately or only when constraints are met;
- cache behavior across clean install, upgrade and year change;
- behavior when downloaded `Params` is corrupted or incompatible;
- actual telemetry/network destinations used in normal operation;
- exact QR short-link flow;
- every theme/layout state;
- OEM-specific autostart reliability;
- clock drift and wrong-timezone behavior;
- exact behavior at midnight/year rollover;
- whether all declared permissions are requested at runtime.

The controlled plan is in [BLACK_BOX_VALIDATION_PLAN.md](BLACK_BOX_VALIDATION_PLAN.md).

## 14. Direct implementation decisions derived from the research

Adopt:

- offline-first local snapshot;
- one artifact containing a long future horizon;
- polygon/profile support for regional calculation policies;
- exact timetable override for selected scopes;
- separate iqamah model;
- atomic update and last-known-good;
- local QR generation;
- landscape/portrait assets;
- best-effort and managed-kiosk deployment modes as separate products.

Do not copy:

- the encrypted package or its key;
- raw prayer table/profile data;
- proprietary backgrounds;
- internal class/API names;
- exact redirect mechanism;
- exact visual composition;
- broad permission set without our own requirement.

Improve:

- provenance and approval;
- signed snapshots;
- explicit stale/coverage states;
- rollback;
- source licenses;
- parser drift protection;
- per-mosque emergency override;
- privacy-minimal diagnostics.

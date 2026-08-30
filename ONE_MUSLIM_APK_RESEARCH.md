# 1Muslim 5.9.5 clean-room APK research

Date: 2026-08-30  
Input: product-owner-supplied `1Muslim_5.9.5.apk`  
Scope: offline/static analysis plus an ordinary installation attempt; no authentication, pinning, anti-tamper, or store-protection bypass.

## Result

`CONFIRMED_STATIC`: 1Muslim is a hybrid prayer-time system. It can calculate times locally for places from a large bundled city catalog, but it also ships a second SQLite database containing 366 stored prayer-time rows for each of 621 named timetable cities. That timetable database can be replaced by a downloaded whole-database ZIP.

`CONFIRMED_STATIC`: the primary stored result named `Ульяновск` is timetable city ID 1187. Selecting it follows the stored-table branch; the Russia calculation method, Hanafi/Standard selector, twilight angles, and high-latitude calculator are not used to produce its raw times. With default zero corrections and the current `Europe/Ulyanovsk` rules, the displayed prayer minutes are the database values unchanged.

`INFERENCE`: the close relationship with the RDUM Ulyanovsk calendar is best explained by a shared or closely related upstream timetable, not by the APK's generic `RUSSIA` calculator. Static evidence does not identify who supplied the table, its approval scope, its license, or its authoritative source. Correlation must not be presented as provenance.

The full day-by-day result is in `ULYANOVSK_ONE_MUSLIM_COMPARISON.md`.

## Evidence boundary

The repository evidence vocabulary is used throughout:

- `CONFIRMED_STATIC` means a fact recovered from the supplied APK by clean-room static inspection. It is not proof that a path executed on a device.
- `CONFIRMED_RUNTIME` is reserved for behavior reproduced on a controlled device.
- `CONFIRMED_PUBLIC` is public first-party documentation or a supplied visual.
- `INFERENCE`, `PROPOSAL`, and `UNKNOWN` are not promoted to facts.

No APK, DEX, decoded manifest, decompiled code, embedded credential, raw database, timetable bulk data, or extracted resource is stored in Git. Aggregate counts, selected control-case metadata, hashes, independently generated statistics, and interface-level findings are retained.

## Reproducible static method

The following independent tools were used against the unchanged input:

- `sha256sum`, `stat`, `unzip -t`, `unzip -l`, and `strings`;
- Android SDK `aapt`, `aapt2`, `apkanalyzer`, `dexdump`, `apksigner`, and `adb`;
- JADX 1.5.6 and apktool 3.0.3 installed user-locally from their official releases;
- SQLite command-line inspection and Python standard-library `sqlite3` against databases extracted into an external research cache.

Important call paths were corroborated between JADX output, decoded smali/DEX strings, and direct database queries because JADX reported 620 partial decode/decompilation errors. Both extracted SQLite databases returned `ok` from `PRAGMA integrity_check`. This is database-file integrity, not publisher authenticity.

## Package and signing inventory

| Item | Finding | Evidence |
|---|---|---|
| APK SHA-256 | `4fea3403ec5d288163fbe649220bda86ccaaad3b8d76ba57227d7b2aea434bfb` | `CONFIRMED_STATIC` |
| Size | 86,520,143 bytes | `CONFIRMED_STATIC` |
| Package | `com.namaztime` | `CONFIRMED_STATIC` |
| Version | versionCode 240, versionName 5.9.5 | `CONFIRMED_STATIC` |
| SDK | min 26, target/compile 36 | `CONFIRMED_STATIC` |
| Packaging | three DEX files; required ABI and density split types | `CONFIRMED_STATIC` |
| Native code | no `lib/` entries in the supplied base APK | `CONFIRMED_STATIC` |
| Wrapper | application class is Google Play Pairip's application wrapper | `CONFIRMED_STATIC` |
| APK signature | one signer; v2 and v3 verify; Google Play SourceStamp verifies | `CONFIRMED_STATIC` |

`CONFIRMED_STATIC`: the supplied artifact is a base APK that declares required ABI and density splits. It is therefore not, by itself, an installable complete app set.

## Manifest and permissions

`CONFIRMED_STATIC`: the main activity exposes phone launcher and `LEANBACK_LAUNCHER` entry points and accepts the first-party `links.1muslimapp.com/app` deep-link host. GPS, accelerometer, gyroscope, compass, leanback, touchscreen, and camera hardware are optional features.

Declared permissions, grouped by purpose:

- location and network: coarse/fine location, network state, Internet;
- prayer alarms/playback: foreground service/media playback, notification policy, post notifications, boot completed, battery-optimization request, turn screen on, full-screen intent, vibrate, wake lock, alarm scheduling and exact alarm;
- ancillary features: camera, audio settings, launcher shortcut;
- commercial/platform services: Play billing, Firebase messaging, advertising ID, install referrer, and Play license checking.

`CONFIRMED_STATIC`: permission presence describes capability only. This research did not assert that every permission is exercised during prayer-time resolution.

## Libraries and network stack

`CONFIRMED_STATIC`: packaged metadata and reachable references include Jetpack Compose/Material 3, Room, DataStore, WorkManager, Hilt/Dagger, OkHttp/Retrofit, Firebase messaging/analytics/installations, Google Play Billing, and Google location/maps components.

Prayer/location-relevant network constants and interfaces are:

| Purpose | Static endpoint/interface | Evidence |
|---|---|---|
| Main API base | `https://1muslimapp.com/api/` | `CONFIRMED_STATIC` |
| Timetable database timestamp | `v5/get-db-timestamp` | `CONFIRMED_STATIC` |
| Per-city version comparison | `v5/compare-versions` | `CONFIRMED_STATIC` |
| Database ZIP | `https://1muslimapp.com/content/dbTemp/<timestamp>.zip` | `CONFIRMED_STATIC` |
| Geocoding proxy base | `https://geo.1muslimapp.com/api/` | `CONFIRMED_STATIC` |
| Remote name lookup | GeoNames-compatible `searchJSON` | `CONFIRMED_STATIC` |
| Remote nearby lookup | GeoNames-compatible `findNearbyJSON` | `CONFIRMED_STATIC` |

The APK also contains unrelated content, media, advertising, Quran, and pilgrimage network integrations. They are outside the prayer-source data flow and are not treated as prayer-time sources.

## Assets and databases

The base APK contains 349 asset entries and 2,471 resource entries.

| Asset | Sanitized finding | Evidence |
|---|---|---|
| `cities.sqlite.zip` | 45,481,984-byte uncompressed SQLite city catalog: 233,916 global places, 246 country codes, 395 timezones | `CONFIRMED_STATIC` |
| Russia subset of city catalog | 5,308 places, 83 distinct administrative values, 23 IANA timezones | `CONFIRMED_STATIC` |
| timestamp-named timetable ZIP | 16,171,008-byte uncompressed SQLite database with cultures, localizations, countries, cities, prayer rows, and ancillary content tables | `CONFIRMED_STATIC` |
| Timetable scale | 621 cities and 227,286 prayer rows; every city has exactly 366 month/day rows | `CONFIRMED_STATIC` |
| Other large assets | Quran text/audio/font data, pilgrimage media/config, duas, and devotional content | `CONFIRMED_STATIC` |

`CONFIRMED_STATIC`: the timetable `PrayerDays` table has month/day and Fajr, sunrise, Dhuhr, Asr, Maghrib, and Isha values but no Gregorian year or source/provenance fields. It is a reusable 366-day month/day table, not a 2026-specific record set.

`UNKNOWN`: no authoritative publisher identity, approval state, geographic authority scope, source retrieval time, raw source hash, or parser version is encoded with the inspected Ulyanovsk timetable rows.

## City search and location resolution

### Name search

`CONFIRMED_STATIC`: current name search launches the two local searches concurrently and concatenates their results:

1. The legacy timetable database performs a localized prefix lookup and returns stored city IDs with `calculated=false`.
2. The large city catalog searches canonical/localized names and returns at most ten places with coordinates, country code, administrative name, and IANA timezone. Those results are marked `calculated=true` and start with zero prayer offsets.
3. Only when both local result lists are empty does the app call its GeoNames-compatible proxy. The remote path is bounded by a short timeout in the inspected coordinator.

The selection UI has distinct localized labels for calculated results and database results. Therefore the same city name can expose materially different schedule policies rather than coordinates uniquely deciding the timetable.

### Device location

`CONFIRMED_STATIC`: location selection independently asks:

- the timetable database for the nearest stored city, accepting it only inside 80 km; and
- the global city catalog for its nearest place inside the configured search radius.

Remote nearby lookup is a fallback. The result can contain both a stored timetable candidate and a calculated-place candidate.

### Ulyanovsk candidates

| Catalog/path | Identity | Coordinates | Timezone | Policy flag | Evidence |
|---|---|---:|---|---|---|
| Stored timetable | ID 1187, `Ульяновск` | 54.318180, 48.383611 | `Europe/Ulyanovsk` | `calculated=false` | `CONFIRMED_STATIC` |
| Stored timetable | ID 2540, `Ульяновск 2` | 54.318180, 48.383611 | `Europe/Ulyanovsk` | `calculated=false` | `CONFIRMED_STATIC` |
| Global city catalog | ID 479123, Ulyanovsk | 54.328240, 48.386570 | `Europe/Ulyanovsk` | `calculated=true` | `CONFIRMED_STATIC` |

`CONFIRMED_STATIC`: the two stored Ulyanovsk rows have materially different timetables despite identical coordinates and timezone. This proves that stored city identity, not coordinate calculation alone, selects those results.

`CONFIRMED_STATIC`: the city entity validates an IANA timezone and contains compatibility fallbacks for older Android timezone databases, including `Europe/Ulyanovsk` to `Europe/Samara`. The canonical stored value remains `Europe/Ulyanovsk` when supported.

## Prayer-time data flow

The recovered interface-level flow is:

```text
name or device location
  -> parallel stored-timetable and global-place lookup
  -> explicit candidate (database or calculated)
  -> persist selected city identity, coordinates, timezone, policy flag, offsets
  -> if database:
       load exactly 366 PrayerDays rows by CityId
       map month/day rows onto real Gregorian dates, including leap day
     if calculated:
       resolve calculation settings and run one of the local engines
  -> apply timezone/DST transform and user corrections
  -> cache resolved days in Room
  -> display/alarms/widgets read cached data
```

### Stored timetable branch

`CONFIRMED_STATIC`: the loader reads the six stored fields ordered by month and day and requires exactly 366 rows. It maps them into the current date horizon with explicit leap-day handling. Because the source rows do not contain a year, the same annual template is projected onto different Gregorian years.

`CONFIRMED_STATIC`: post-processing applies timezone/DST behavior, a user quick correction of −1/0/+1 hour, and per-prayer minute offsets. Defaults are zero. Current `Europe/Ulyanovsk` has no DST, so an untouched ID 1187 selection retains stored minutes.

### Calculated branch

`CONFIRMED_STATIC`: the calculated path supports named methods, Standard and Hanafi Asr, high-latitude modes `NONE`, `MIDNIGHT`, `ONE_SEVENTH`, and `ANGLE_BASED`, custom Fajr/Isha values expressed as angles or minutes, an optional seasonal custom interval, and per-prayer offsets.

`CONFIRMED_STATIC`: country code `RU` maps to the named `RUSSIA` profile, whose packaged parameter constructor uses 16° Fajr and 15° Isha. For one global-catalog country-default lookup, the bundled country row instead contains `MWL` and `Standard`; multiple calculation/config paths therefore exist and runtime selection should not be inferred from a constant alone.

`CONFIRMED_STATIC`: the inspected modern calculator forces the twilight-angle high-latitude treatment above latitude 50°, while the catalog calculation-default lookup preselects angle-based adjustment above latitude 49°. Other named engines have dedicated branches. These rules matter for a calculated Ulyanovsk candidate, not stored timetable ID 1187.

`UNKNOWN`: without a successful runtime selection of the calculated Ulyanovsk candidate, the exact default method/Asr settings eventually shown for that candidate are not promoted to `CONFIRMED_RUNTIME`.

## Exact Ulyanovsk stored-table flow

For the result named exactly `Ульяновск` in the legacy database:

```text
localized search `Ульяновск`
  -> stored timetable city ID 1187
  -> coordinates 54.318180, 48.383611
  -> IANA timezone Europe/Ulyanovsk
  -> calculated=false
  -> SELECT 366 PrayerDays WHERE CityId=1187 ORDER BY Month, Day
  -> project template rows onto 2026 (skip stored Feb 29)
  -> default corrections = 0; no Ulyanovsk DST
  -> Room cache -> displayed times
```

Consequences:

- Calculation method: not consulted for this result (`CONFIRMED_STATIC`).
- Hanafi/Standard Asr selector: not consulted; Asr is already stored (`CONFIRMED_STATIC`).
- Fajr/Isha angles: not consulted; the values and seasonal behavior are already stored (`CONFIRMED_STATIC`).
- Dhuhr correction: encoded in the daily table, then optionally user-adjustable (`CONFIRMED_STATIC`).
- High-latitude rule: not consulted (`CONFIRMED_STATIC`).
- Timetable upstream and religious approval: `UNKNOWN`.

## Database update mechanism

`CONFIRMED_STATIC`: first-run/bootstrap logic extracts the timestamp-named bundled timetable ZIP into an application-private `muslim_database` directory. A stored timestamp is compared with the bundled timestamp so the newer local version remains selected.

`CONFIRMED_STATIC`: update logic can request a current database timestamp, compare selected city versions, stream an entire timestamp-addressed ZIP into app cache, unzip a timestamp-named SQLite file, switch the selected timestamp preference, and restart/reload timetable persistence.

`CONFIRMED_STATIC`: no cryptographic signature verification, expected content hash, or SQLite schema/integrity check was found in the inspected client activation path. This is a scoped absence finding; it does not claim that server-side controls do not exist.

`UNKNOWN`: the supplied base APK could not be executed far enough to observe an actual update request or response, so endpoint execution, payload schema, and production transport behavior remain unconfirmed at runtime.

## Runtime black-box attempt

Controlled device: running Android TV emulator, API 36.

`CONFIRMED_RUNTIME`: `adb install -r --no-streaming 1Muslim_5.9.5.apk` failed with `INSTALL_FAILED_MISSING_SPLIT: Missing split for com.namaztime`.

`CONFIRMED_STATIC`: the base manifest requires `base__abi` and `base__density` split types, which explains the ordinary package-manager rejection.

No matching split APKs or app bundle were supplied. Generating/modifying packages or bypassing Pairip/store controls would exceed the allowed ordinary black-box scope. Consequently city selection, offline restart, date changes, UI output, and network metadata are `UNKNOWN` at runtime for this artifact.

## What this proves—and does not prove

`CONFIRMED_STATIC`:

- 1Muslim as a whole is hybrid: bundled/downloadable static timetable plus local calculation plus local/remote place discovery.
- exact stored city identity chooses a timetable independently of coordinates;
- primary Ulyanovsk ID 1187 is a stored timetable with zero default correction behavior;
- the reusable 366-day table is sufficient for offline display after bootstrap;
- a generic Russia calculation profile exists but is not the ID 1187 mechanism.

`INFERENCE`:

- strong multi-prayer correlation and matching distinctive seasonal transitions indicate a shared or closely related Ulyanovsk timetable tradition/source.

`UNKNOWN`:

- who authored or approved 1Muslim's Ulyanovsk rows;
- whether ID 1187 is labeled or treated as official in production UI;
- whether users normally choose ID 1187, ID 2540, or the calculated candidate;
- whether the production service currently serves a newer/different table;
- the cause of the isolated July 3 Isha outlier;
- any private server-side validation, provenance, or update controls.

## Clean-room implication for NamazTime

`PROPOSAL`: reproduce the architecture pattern—not the competitor dataset—by keeping a public-source city catalog separate from an approved policy registry. A candidate place may locate policy scopes, but only an explicit authority/source/policy record may select an official timetable or calculation profile. Every chosen schedule must still pass NamazTime ingestion, validation, approval, signing, last-known-good, and offline persistence controls.

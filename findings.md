# Findings: 1Muslim and Russia city/source research

## Requirements

- Start from repository state after T033 and read all named research, data, plan, fixture, ADR, and spec material.
- Perform full offline/static APK analysis and ordinary runtime black-box observation where useful.
- Recover the actual Ulyanovsk data flow and compare an independent 2026 reproduction day-by-day with the approved effective schedule.
- Research official/practiced regional authorities and sources across Russia, explicitly retaining ambiguous and unknown mappings.
- Compare IslamApp and 1Muslim architectures and propose a clean-room NamazTime resolver.
- Produce the required research reports, registry draft, ADR, plan/task updates, and—only if evidence supports it—a tested Ulyanovsk registry vertical slice.
- Make five local checkpoint commits; do not push or create a PR.

## Evidence protocol

- Evidence labels used in prose: `CONFIRMED_PUBLIC`, `CONFIRMED_STATIC`, `CONFIRMED_RUNTIME`, `INFERENCE`, `PROPOSAL`, `UNKNOWN`.
- Official registry assessment is a separate field with values corresponding to confirmed official, strong evidence, ambiguous, and unknown; it does not promote public evidence into runtime evidence.
- Static call paths show packaged implementation possibilities, not proof that a path executed for Ulyanovsk.
- Correlation with an official calendar is not proof of source provenance.

## Research Findings

- APK input exists at workspace root and is approximately 83 MiB; its hash/package/version are recorded below and in `ONE_MUSLIM_APK_RESEARCH.md`.
- Git initially reports branch `main` tracking `origin/main` with no reported tracked/untracked changes before planning files are created.
- Repository T033 is complete after six UI/operator checkpoints and follow-up review commits. It preserves source Dhuhr onset, publishes a fixed 13:15 Dhuhr/Jumu'ah policy, and leaves the signed offline Ulyanovsk snapshot/provenance model intact.
- Existing Ulyanovsk source chain already includes the retained official 2026 annual PDF, the August image override, controlled transcriptions, an effective policy, reconciliation ledger, signed approval artifacts, and the pilot-local signed snapshot.
- The effective schedule's normalized SHA-256 recorded in `PLANS.md` is `e7bcc16ad55d00f136cbfc5629e2680babf3f71b331dd33ca4f6e1b1207dbf77`; all twelve August numeric differences are retained and resolved by the configured photo precedence.
- The existing official PDF provider is explicitly bound to locality `ulyanovsk`, timezone `Europe/Ulyanovsk`, the pilot mosque, and parser version `ulyanovsk-official-pdf-csv/v1`; it is therefore not a nationwide resolver.
- `START_HERE.md` and `PLANS.md` confirm the TV display path consumes local Room state only, and the pilot is delivered as an authenticated signed bundled snapshot/USB update. A future city registry must resolve publication inputs/control-plane policy, not call a source from composables.
- The official Ulyanovsk PDF explicitly describes Hanafi Asr and a summer Fajr/Isha analogy to Astrakhan; the printed transition cells are May 10 Isha / May 11 Fajr and Aug 2 Isha / Aug 3 Fajr. This is authoritative fixture evidence for the NamazTime source, not yet evidence of how 1Muslim implements it.
- All 31 August rows agree between annual PDF and monthly photo for Fajr, sunrise, zenith, Asr, Maghrib, and Isha. Differences are Dhuhr onset on Aug 20–30 (photo equals zenith, PDF is +10 minutes), the Aug 24 collective value, and transition wording.
- D-002 makes the monthly photo authoritative for every supplied August field, so the effective NamazTime schedule has the approximately ten-minute late-August Dhuhr change by deliberate source precedence. This must not be mistaken for a calculation defect.
- ADR 0001/0002/0003/0011 require explicit mosque-source binding, offline Room display, canonical Ed25519 verification, protected publication custody, and no silent fallback. A city registry cannot bypass candidate validation, approval, signing, or mosque binding.
- ADR 0013 proves device-local display/iqamah identity overlays cannot modify city, locality, timezone, authority, source, policy, approval, or signed snapshot bytes.
- `CONFIRMED_STATIC`: `1Muslim_5.9.5.apk` SHA-256 is `4fea3403ec5d288163fbe649220bda86ccaaad3b8d76ba57227d7b2aea434bfb`, size 86,520,143 bytes. It is already excluded by `.gitignore`'s `*.apk` rule and passes ZIP integrity testing.
- `CONFIRMED_STATIC`: package `com.namaztime`, versionCode `240`, versionName `5.9.5`, min SDK 26, target/compile SDK 36. The application class is `com.pairip.application.Application`, indicating Google Play Pairip protection/wrapping; static reachability may therefore be partially obscured.
- `CONFIRMED_STATIC`: the APK contains three DEX files, no `lib/` entries, 349 asset entries and 2,471 resource entries. Notable schedule/location assets are `assets/cities.sqlite.zip` and `assets/639215546358535521.zip`.
- `CONFIRMED_STATIC`: manifest declares optional GPS/touch/leanback, coarse/fine location, Internet/network, camera, exact alarms, boot, wake/full-screen/notification/audio permissions, Firebase messaging and Google Maps metadata. MainActivity exposes both phone launcher and `LEANBACK_LAUNCHER` plus `links.1muslimapp.com/app` deep links.
- `CONFIRMED_STATIC`: packaged dependencies visible from metadata include Compose/Material3, Room, DataStore, WorkManager, Hilt/Dagger, OkHttp, Firebase messaging/analytics/installations, Google Play billing and location/map-related services. Presence is not runtime proof.
- The Android SDK already supplies `aapt`, `aapt2`, `apksigner`, `dexdump`, `apkanalyzer`, `adb`, and `sqlite3`. Official JADX 1.5.6 and apktool 3.0.3 were installed under `/home/andy/.local/` for this research; their downloaded artifact SHA-256 values were recorded in the command log.
- `CONFIRMED_STATIC`: extracted outside Git, `cities.sqlite` contains 233,916 places globally. The Russia subset has 5,308 rows, 83 distinct administrative names, and 23 IANA timezones. Its canonical Ulyanovsk row is `id=479123`, coordinates `54.328240,48.386570`, timezone `Europe/Ulyanovsk`, administrative value `Ulyanovsk`.
- `CONFIRMED_STATIC`: the legacy prayer database contains 621 cities and 227,286 `PrayerDays` rows. Every city has exactly 366 daily rows, so this database is a bundled timetable dataset rather than merely a calculation-parameter registry.
- `CONFIRMED_STATIC`: legacy city `Id=1187` is `Ульяновск` / `Ulyanovsk`, coordinates `54.318180,48.383611`, GMT `4`, timezone `Europe/Ulyanovsk`. Its sampled daily prayer rows match the repository's official 2026 annual Ulyanovsk fixture, including the unusual May/August Fajr/Isha seasonal transitions.
- `CONFIRMED_STATIC`: a second legacy row, `Id=2540` (`Ульяновск 2`), has the same coordinates/timezone but materially different daily times. City identity therefore affects timetable selection; coordinates alone do not determine the result.
- `CONFIRMED_STATIC`: database bootstrap code extracts asset `639215546358535521.zip` to `filesDir/muslim_database/<timestamp>.sqlite`. Update code can replace it with `https://1muslimapp.com/content/dbTemp/<timestamp>.zip`, while API calls under `https://1muslimapp.com/api/` obtain database timestamps and compare city versions. This is a bundled-offline plus downloadable-dataset architecture.
- `CONFIRMED_STATIC`: the Retrofit interface includes `v5/get-db-timestamp`, `v5/compare-versions`, scheduled-notification-version, and Hijri-adjustment endpoints. Static presence proves packaged call paths and constants, not execution on a device.
- `CONFIRMED_STATIC`: legacy city search queries localized city names in the timetable database; its prayer loader selects `Time1`, `Sunrise`, `Time2`–`Time5` by `CityId`, ordered by month/day, and constructs a city with `calculated=false`. This directly supports rendering stored timetable values without local calculation for a selected legacy city.
- `CONFIRMED_STATIC`: the app also contains calculation profiles (including a `RUSSIA` method), Standard/Hanafi Asr choices, high-latitude adjustments, custom angles, and per-prayer minute offsets. Their presence supports calculated-city paths but does not explain legacy Ulyanovsk `Id=1187`, whose loaded city is non-calculated.
- `CONFIRMED_STATIC`: current name search runs the legacy timetable lookup and bundled global-catalog lookup concurrently, concatenates the results, and only invokes the app's GeoNames-compatible proxy after both local result lists are empty. The remote Retrofit base is `https://geo.1muslimapp.com/api/`, with GeoNames-style `searchJSON`/`findNearbyJSON` operations.
- `CONFIRMED_STATIC`: a global-catalog search result is constructed with `calculated=true`, its IANA timezone/country/admin/coordinates, zero offsets, and an initially null calculation placeholder. A legacy timetable result is constructed with `calculated=false` and a city ID that keys the 366 stored `PrayerDays` rows. The UI has distinct “calculated” and “database” result labels.
- `CONFIRMED_STATIC`: location-based selection independently asks (a) the legacy timetable database for its nearest city within 80 km and (b) the global catalog for its nearest city inside a search radius, returning the available candidates; remote GeoNames is a bounded fallback. Thus a physical coordinate may present both an official-ish stored timetable candidate and a calculated-place candidate rather than silently equating them.
- `CONFIRMED_STATIC`: downloaded timetable ZIPs are streamed to a cache file, extracted under the server-provided timestamp filename, and the selected timestamp preference is then changed. In the inspected path no cryptographic signature/hash verification or SQLite schema/integrity validation was found before activation. This absence is a static finding limited to the inspected update path, not a claim about server-side controls.
- `CONFIRMED_STATIC`: when a selected city is materialized, the repository branches on `calculated`. Calculated cities run the internal calculator for a date horizon; non-calculated cities require exactly 366 raw rows from the legacy database. Stored rows are then mapped to real years, leap-day handling is explicit, IANA zone DST rules and user-configurable per-prayer/±1-hour corrections are applied, and the result is cached in Room.
- `CONFIRMED_STATIC`: default quick time correction is zero (“no correction” in Russian resources), per-prayer offsets default to zero, and `Europe/Ulyanovsk` currently has no DST. Therefore an untouched legacy Ulyanovsk selection displays the stored database minutes unchanged; calculation method, Asr rule, and high-latitude algorithm are not consulted for that city path.
- `CONFIRMED_STATIC`: APK signature verification succeeds with v2 and v3 schemes and a Google Play SourceStamp; there is one app signer. Certificate digests were recorded locally for identity checking, while no private key or secret material was extracted or stored.
- `CONFIRMED_STATIC`: country code `RU` maps to a packaged `RUSSIA` calculation profile with 16° Fajr and 15° Isha parameters. The calculated path supports Standard/Hanafi Asr and multiple high-latitude modes, but the inspected modern engines force angle-based/twilight-angle handling for coordinates above 49°/50° in their respective default/calculation stages. These constants do not participate in stored Ulyanovsk ID 1187.
- `CONFIRMED_STATIC`: the base APK declares required ABI and density split types. An ordinary API 36 Android TV emulator install failed with `INSTALL_FAILED_MISSING_SPLIT`; no runtime app behavior was observed and no store/Pairip protection was bypassed.
- `CONFIRMED_STATIC`: full-year comparison of primary Ulyanovsk ID 1187 against the annual fixture covers 2,190 prayer fields: 1,912 exact (87.31%), 246 at ±1 minute (11.23%), none at ±2–5, and 32 above five minutes (1.46%). It has 228 all-six-prayer exact days and 334 days wholly within one minute.
- `CONFIRMED_STATIC`: comparison against the approved effective snapshot yields 1,901 exact fields (86.80%), 246 at ±1 (11.23%), none at ±2–5, and 43 above five minutes (1.96%). It has 218 all-six-prayer exact days and 323 days wholly within one minute.
- `CONFIRMED_STATIC`: the material annual differences are all July Dhuhr rows at −10/−11 minutes and July 3 Isha at −60 minutes. Effective comparison adds Aug 20–30 Dhuhr at +10 because NamazTime's approved monthly photo override uses zenith on those dates while 1Muslim retains the annual-style +10 timetable values.
- `INFERENCE`: the 366-row no-year template and the many systematic ±1 differences from September onward are consistent with a reusable annual template derived from a different Gregorian/leap-year basis, not a literal copy of the 2026 PDF. The exact template year/source remains `UNKNOWN`.
- `INFERENCE`: matching distinctive seasonal Fajr/Isha transitions and 98.54% of annual fields within one minute strongly support a shared or closely related upstream Ulyanovsk timetable tradition. They do not prove source authority or approval.
- `UNKNOWN`: July 3 Isha is an isolated 60-minute outlier among neighboring rows. Static code does not supply a rule that explains only this cell; a data-entry/version error is plausible but unproved.

## Technical Decisions

| Decision | Rationale |
|----------|-----------|
| Store only sanitized summaries and independently generated statistics/harness data. | Avoid committing competitor artifacts or reconstructable proprietary datasets. |
| Treat city coordinates/geometry, authority, source, and policy as separate concepts. | A geographic match cannot establish religious authority. |
| Fail closed at unavailable/ambiguous policy rather than silently applying a nationwide method. | Required product invariant. |

## Issues Encountered

| Issue | Resolution |
|-------|------------|
| The skill's example catch-up command invokes unavailable `python`. | Use `python3`, which will be checked next. |
| Initial combined repository inventory output was truncated because the search matched many Ulyanovsk/T033 lines. | Use smaller, exact file/chunk reads and never infer unread portions from truncated output. |
| Combined ADR plus repository-heading search output was truncated after the complete selected ADR text. | Re-read exact Architecture/Decision/spec chunks separately before design work. |
| `apkanalyzer manifest permissions` failed because its internal `aapt` invocation returned code 1. | Use decoded `manifest print` for permissions now and install/locate standalone build tools for cross-checking. |
| `sudo apt-get` cannot run non-interactively because this account requires a password. | Use user-local official release binaries and already installed Android SDK tools. |
| JADX completed with exit status 3 after 620 resource/decompilation errors; apktool reported unresolved resources. | Retain usable output, but corroborate important paths with decoded smali, DEX strings, and direct SQLite evidence. |

## Resources

- Local APK research input: `1Muslim_5.9.5.apk` (never commit).
- Planning skill: `/home/andy/.codex/skills/bx-dev/skill-library/general/planning-with-files/`.
- Provider skill: `.agents/skills/prayer-times-provider/`.

## Visual/Browser Findings

- None yet.

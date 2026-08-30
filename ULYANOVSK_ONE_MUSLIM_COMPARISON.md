# Ulyanovsk: 1Muslim stored timetable versus NamazTime 2026

Date: 2026-08-30  
Control case: primary stored 1Muslim city `Ульяновск`, ID 1187  
Comparison sign: `1Muslim minute − NamazTime reference minute`

## Conclusion

`CONFIRMED_STATIC`: primary Ulyanovsk ID 1187 is not produced by a prayer-time calculation profile. It is a 366-row month/day timetable projected onto 2026. The clean-room reproduction therefore implements that projection and comparison, not a fictitious reconstruction of angles or offsets.

Against NamazTime's approved effective schedule, 1Muslim matches 1,901 of 2,190 prayer fields exactly (86.80%); another 246 fields (11.23%) differ by exactly one minute. No field differs by 2–5 minutes. The 43 material fields are concentrated in three explained or bounded clusters:

- all 31 July Dhuhr rows are 10–11 minutes earlier;
- Aug 20–30 Dhuhr is exactly 10 minutes later than NamazTime effective because NamazTime's approved August-photo precedence replaces the annual Dhuhr values with zenith;
- July 3 Isha is 60 minutes earlier and remains `UNKNOWN`.

`INFERENCE`: the distinctive matching Fajr/Isha seasonal transitions and 98.04% of effective fields within one minute strongly indicate a shared or closely related Ulyanovsk timetable tradition/source. They do not establish that 1Muslim's data is official, approved, current, or derived from the repository's 2026 artifacts.

## Inputs and clean-room method

NamazTime references:

1. annual controlled transcription: `fixtures/pilot/ulyanovsk-2026/schedule.csv`;
2. approved effective signed snapshot: `apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json`;
3. August reconciliation and effective policy in `fixtures/pilot/ulyanovsk-2026/`.

External research input:

- timetable SQLite extracted outside Git from the supplied APK;
- extracted database SHA-256 `479bd5108dd59ca5202132b17deb25a1eaed1ed6b93632f537fbcdd2b5153e81`;
- city ID 1187, coordinates 54.318180, 48.383611, timezone `Europe/Ulyanovsk`.

`CONFIRMED_STATIC`: the database contains 366 rows for ID 1187 and no year column. For 2026 the app's stored-table behavior maps each month/day row to 2026 and excludes the February 29 template row, yielding 365 dates.

The independent harness is `research/tools/one_muslim_ulyanovsk_compare.py`. It:

- opens an explicitly supplied external SQLite in read-only mode;
- validates the 366-row and unique month/day invariants;
- projects the template onto the requested Gregorian year;
- validates all six prayer-time strings;
- compares all dates and fields with an annual CSV or signed snapshot;
- emits city metadata and aggregate statistics only, never source timetable rows.

Its synthetic tests are in `research/tools/test_one_muslim_ulyanovsk_compare.py` and run through `make test-research`.

Reproduction commands:

```bash
python3 research/tools/one_muslim_ulyanovsk_compare.py \
  --database /external/research/639215546358535521.sqlite \
  --city-id 1187 \
  --year 2026 \
  --reference-csv fixtures/pilot/ulyanovsk-2026/schedule.csv \
  --reference-name annual-ulyanovsk-2026

python3 research/tools/one_muslim_ulyanovsk_compare.py \
  --database /external/research/639215546358535521.sqlite \
  --city-id 1187 \
  --year 2026 \
  --reference-snapshot apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json \
  --reference-name effective-ulyanovsk-2026
```

## Whole-year statistics

### Against annual RDUM transcription

365 dates × 6 prayer fields = 2,190 comparisons.

| Bucket | Fields | Percent |
|---|---:|---:|
| exact | 1,912 | 87.31% |
| ±1 minute | 246 | 11.23% |
| ±2–5 minutes | 0 | 0.00% |
| more than 5 minutes | 32 | 1.46% |

- All six fields exact on 228 days.
- All six fields within one minute on 334 days.
- Maximum absolute difference: 60 minutes, Isha on July 3.

| Prayer | Exact | ±1 | ±2–5 | >5 | Maximum absolute difference |
|---|---:|---:|---:|---:|---:|
| Fajr | 318 | 47 | 0 | 0 | 1 |
| Sunrise | 313 | 52 | 0 | 0 | 1 |
| Dhuhr | 329 | 5 | 0 | 31 | 11 |
| Asr | 317 | 48 | 0 | 0 | 1 |
| Maghrib | 307 | 58 | 0 | 0 | 1 |
| Isha | 328 | 36 | 0 | 1 | 60 |

### Against approved effective snapshot

365 dates × 6 prayer fields = 2,190 comparisons.

| Bucket | Fields | Percent |
|---|---:|---:|
| exact | 1,901 | 86.80% |
| ±1 minute | 246 | 11.23% |
| ±2–5 minutes | 0 | 0.00% |
| more than 5 minutes | 43 | 1.96% |

- All six fields exact on 218 days.
- All six fields within one minute on 323 days.
- Maximum absolute difference: 60 minutes, Isha on July 3.

| Prayer | Exact | ±1 | ±2–5 | >5 | Maximum absolute difference |
|---|---:|---:|---:|---:|---:|
| Fajr | 318 | 47 | 0 | 0 | 1 |
| Sunrise | 313 | 52 | 0 | 0 | 1 |
| Dhuhr | 318 | 5 | 0 | 42 | 11 |
| Asr | 317 | 48 | 0 | 0 | 1 |
| Maghrib | 307 | 58 | 0 | 0 | 1 |
| Isha | 328 | 36 | 0 | 1 | 60 |

## Date-range analysis

### January through May

`CONFIRMED_STATIC`: all 906 prayer fields for Jan 1–May 31 match the annual and effective schedules exactly. The unusual official summer transition cells in May are included in that exact match. This would be unlikely for an unrelated generic 16°/15° calculation alone.

### June

`CONFIRMED_STATIC`: the only difference is Fajr on June 23 at −1 minute.

### July

`CONFIRMED_STATIC`: all 31 Dhuhr values differ materially:

- −10 minutes on July 1–21, July 24, and July 31;
- −11 minutes on July 22–23 and July 25–30.

The annual/effective schedule puts July Dhuhr at zenith +10 minutes. The 1Muslim rows instead equal the annual zenith value on 23 days and are one minute before it on eight days. This behavior is encoded directly in the stored daily rows.

`UNKNOWN`: static data contains no policy or provenance field explaining why July alone uses zenith-like Dhuhr. It is not caused by the generic calculator because ID 1187 bypasses calculation. A source-specific summer rule or timetable-version choice is possible but unproved.

`CONFIRMED_STATIC`: July 3 Isha is −60 minutes. Neighboring 1Muslim rows are approximately an hour later and the official row is also an hour later.

`INFERENCE`: the isolated shape is consistent with a one-cell data error, but neither code nor source metadata proves that diagnosis. It remains `UNKNOWN`, not silently corrected.

`CONFIRMED_STATIC`: July 7 Maghrib is the month's only additional difference, at −1 minute.

### August and the known late-August Dhuhr observation

`CONFIRMED_STATIC`: the 1Muslim table and annual CSV have the same Dhuhr value throughout August. Their only August annual-reference difference is Asr on Aug 29 at −1 minute.

`CONFIRMED_STATIC`: NamazTime effective deliberately differs from the annual source on Aug 20–30. The approved August photo has Dhuhr equal to zenith on those 11 dates; policy D-002 makes that supplied photo authoritative for August. 1Muslim retains the annual-style Dhuhr at zenith +10, so every one of those comparisons is exactly +10 minutes.

The observed late-August difference is therefore not explained by a prayer-calculation parameter:

```text
1Muslim ID 1187 -> stored annual-style Dhuhr (+10 over zenith)
NamazTime effective -> approved August-photo Dhuhr (= zenith)
result -> 1Muslim is +10 minutes on Aug 20–30
```

`UNKNOWN`: why 1Muslim's upstream timetable chose the annual-style value and whether another downloadable database version differs.

### September through December

`CONFIRMED_STATIC`: every difference is only ±1 minute. Non-zero field counts by month are:

| Month | Fajr | Sunrise | Dhuhr | Asr | Maghrib | Isha | Total |
|---|---:|---:|---:|---:|---:|---:|---:|
| September | 21 | 13 | 1 | 21 | 19 | 16 | 91 |
| October | 10 | 19 | 1 | 15 | 20 | 13 | 78 |
| November | 9 | 12 | 0 | 8 | 13 | 6 | 48 |
| December | 6 | 8 | 3 | 3 | 5 | 1 | 26 |

`INFERENCE`: the onset of dispersed one-minute differences after September is compatible with a perpetual no-year template prepared from a different Gregorian/leap-year basis or rounding convention. The schema proves reuse; it does not reveal the template's source year, so the exact cause is `UNKNOWN`.

## Why the schedules nearly coincide

The strongest evidence is the combination, not any single cell:

1. `CONFIRMED_STATIC`: ID 1187 stores all six daily fields; it does not calculate them.
2. `CONFIRMED_STATIC`: January–May is a 906-field exact match.
3. `CONFIRMED_STATIC`: the RDUM-specific high-latitude/summer Fajr and Isha transitions are present at matching dates/values.
4. `CONFIRMED_STATIC`: 2,158 of 2,190 annual fields (98.54%) are within one minute.
5. `CONFIRMED_STATIC`: a second same-coordinate timetable, `Ульяновск 2`, is a poor match: only 49 exact fields, 97 within one minute, 1,567 above five minutes, maximum 124 minutes.

`INFERENCE`: these facts reject “same coordinates plus generic Russia calculation” as the main explanation and strongly favor a shared or closely related timetable lineage for ID 1187.

`UNKNOWN`: the exact upstream artifact, publisher agreement, derivation year, and chain of custody. NamazTime must continue to cite and approve its own RDUM source chain; it must never borrow 1Muslim's timetable or call it official.

## Algorithm/parameter answers for ID 1187

| Question | Answer | Evidence |
|---|---|---|
| Coordinates | 54.318180, 48.383611 | `CONFIRMED_STATIC` |
| Timezone | `Europe/Ulyanovsk` | `CONFIRMED_STATIC` |
| Local calculation method | none in this path | `CONFIRMED_STATIC` |
| Hanafi Asr parameter | not consulted; Asr is stored | `CONFIRMED_STATIC` |
| Fajr/Isha angles | not consulted; values are stored | `CONFIRMED_STATIC` |
| Dhuhr offset/rule | daily table value; July behaves approximately as zenith, most other months approximately zenith +10 | `CONFIRMED_STATIC` for values; rationale `UNKNOWN` |
| High-latitude rule | not consulted | `CONFIRMED_STATIC` |
| Seasonal switches | already encoded in daily Fajr/Isha rows | `CONFIRMED_STATIC`; original rationale/source `UNKNOWN` |
| Additional adjustments | user per-prayer offsets and quick ±1 hour exist; defaults are zero | `CONFIRMED_STATIC` |
| Ready-made values | yes, 366 reusable month/day rows | `CONFIRMED_STATIC` |
| Update | whole timetable database may be downloaded and replaced | `CONFIRMED_STATIC`; actual runtime update `UNKNOWN` |

## Clean-room decision

`PROPOSAL`: do not attempt to reproduce Ulyanovsk by tuning the generic 1Muslim calculator. NamazTime already has the stronger mechanism: retained official artifacts, deterministic normalization, explicit source precedence, approval, signing, and last-known-good offline delivery. The reusable lesson is to resolve a canonical city to an explicit approved timetable policy—not to copy the competitor table or infer authority from a close numeric match.

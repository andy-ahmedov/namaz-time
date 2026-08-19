# TEST_STRATEGY.md

## Risk order

1. wrong prayer date/time/timezone;
2. wrong source or silent fallback;
3. failed update replacing good data;
4. incorrect iqamah/Jumu'ah precedence;
5. unreadable/unreachable TV UI;
6. power/network/process recovery;
7. security/privacy regression.

Tests are allocated in that order.

## Domain unit tests

### Time and next-event

- before Fajr, exact event boundary and after Isha;
- next-day Fajr;
- midnight/date rollover in mosque timezone;
- device timezone differs from mosque timezone;
- leap day and year boundary;
- DST zones even if pilot is non-DST;
- sunrise excluded/included according to configured countdown policy;
- `HH:MM` ordering and next-day semantics.

### Iqamah resolution

- fixed vs offset;
- exact-date override beats range/base;
- weekday rule;
- overlapping priorities;
- missing iqamah remains missing;
- offset crossing midnight is rejected or explicitly normalized;
- Maghrib special policy only when configured.

### Jumu'ah

- multiple sessions;
- Friday local date;
- effective range and locale;
- Dhuhr source row remains intact.

## Provider contract tests

Every provider ships:

- sanitized representative fixture;
- parser golden output;
- malformed/missing-field fixture;
- schema-drift fixture;
- duplicate/gap fixture;
- checksum test;
- source scope test;
- rate-limit/retry test where networked.

No live external site is required for normal CI. A separate scheduled canary may check reachability/shape without publishing.

## Validation/diff tests

- 365 and 366-day calendars;
- threshold report by prayer;
- changed source/method is always highlighted;
- candidate cannot be approved if raw hash changed;
- parser version included in approval;
- invalid timezone and date gaps block publication;
- deterministic diff hash.

## Snapshot tests

- JSON Schema validation;
- deterministic canonical payload;
- correct SHA-256;
- valid/invalid/unknown-key Ed25519 signature;
- tampered byte rejection;
- payload size limit;
- duplicate date rejection;
- insufficient future coverage rejection;
- transaction rollback on import failure;
- active pointer changes only after full validation;
- previous snapshot remains restorable.

## Android integration tests

- first install imports bundled synthetic snapshot;
- cold launch offline;
- Room migration preserves active snapshot;
- WorkManager sync with 200/304/401/404/500/timeout;
- process kill during download/import/activation;
- corrupt staged file;
- missing custom background fallback;
- clock/timezone mismatch diagnostic;
- saved focus/navigation state.

## Compose/UI tests

- main grid renders all fields and missing iqamah correctly;
- countdown does not relayout every second;
- D-pad reaches every setting;
- initial focus and back behavior;
- 720p/1080p/4K screenshots;
- long Russian/Arabic labels;
- RTL mixed content;
- QR panel on/off and expired state;
- stale vs expired status.

Golden images are reviewed for layout, not copied from a competitor.

## Physical device tests

Required before pilot:

- power cut/replug;
- boot and app relaunch;
- Wi-Fi off for seven-day soak or accelerated clock-safe test;
- router restart and captive/no-internet network;
- remote-only setup;
- real hall readability at multiple distances;
- QR scan from representative seating;
- 4K background memory soak;
- TV overscan/safe area;
- wrong system timezone/clock;
- OEM process killing;
- managed kiosk if promised.

## Runtime research of IslamApp

The competitor runtime plan is separate and non-invasive: [BLACK_BOX_VALIDATION_PLAN.md](BLACK_BOX_VALIDATION_PLAN.md). Its results inform requirements only; they are not product acceptance tests.

## Security tests

- authz cross-mosque access;
- expired/reused pairing code;
- token redaction;
- path/content-type/image bomb validation;
- snapshot downgrade/rollback authorization;
- signature key rotation;
- admin CSRF/session protections;
- dependency and secret scans;
- diagnostic bundle privacy review.

## CI gates

Initial:

```text
make docs-check
make lint
make test
```

As code appears, split into:

```text
make test-go
make test-contracts
make test-android-unit
make test-android-instrumented   # device/emulator job
```

A provider/publication PR cannot merge without fixtures and diff/validation tests.

## Release evidence

Each release records:

- commit and build IDs;
- contract/schema versions;
- test commands/results;
- signing key ID;
- supported hardware/OS matrix;
- active source revisions;
- known limitations;
- rollback procedure and previous version.

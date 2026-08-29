# PRODUCT_REQUIREMENTS.md

## Product statement

A mosque operator installs the application on an Android TV or box, binds it to a mosque, verifies an approved schedule, configures iqamah and optional information, and obtains a continuously readable display that remains useful without internet access.

## Success criteria for the first pilot

- the imam/authorized representative accepts every displayed adhan and iqamah value;
- the TV remains correct across a local date rollover with network disabled;
- a bad update cannot replace the active schedule;
- an operator can see source, snapshot version and coverage remaining;
- all setup is possible with a D-pad;
- a power cycle returns to the display with documented reliability for the pilot hardware.

## Personas

### Mosque operator

Not necessarily technical. Needs safe defaults, preview, clear source/freshness, rollback and simple remote/local changes.

### Authorized approver

Owns religious correctness. Reviews source, method, differences and exceptions before publication.

### Congregant

Reads from a distance. Needs unambiguous adhan/iqamah, next prayer and calm visual hierarchy.

### Technical installer

Needs pairing, boot/kiosk options, diagnostics and recovery across heterogeneous TV firmware.

### Service administrator

Maintains sources and devices without being able to silently publish unapproved prayer times.

## Functional requirements

### Prayer schedule

- `FR-PT-001`: show Fajr, Sunrise, Dhuhr, Asr, Maghrib and Isha for the mosque-local date;
- `FR-PT-002`: support optional Imsak, Duha, Tahajjud, Islamic midnight and Jumu'ah sessions;
- `FR-PT-003`: preserve source provenance and approval for every published schedule;
- `FR-PT-004`: use local persisted data when offline;
- `FR-PT-005`: support at least 90 future days; annual snapshot preferred;
- `FR-PT-006`: support exact-date approved overrides without mutating raw source data;
- `FR-PT-007`: reject gaps, invalid timezone and conflicting coverage before publication;
- `FR-PT-008`: never silently change provider or calculation method.

### Iqamah and Jumu'ah

- `FR-IQ-001`: configure fixed time or offset after adhan per prayer;
- `FR-IQ-002`: support base, seasonal/range, weekday and exact-date rules;
- `FR-IQ-003`: preview the fully resolved calendar before publish;
- `FR-IQ-004`: support one or more Friday sessions independently from Dhuhr;
- `FR-IQ-005`: keep an audit trail of every time-affecting change.

### Main display

- `FR-TV-001`: landscape main screen in MVP; portrait is post-MVP;
- `FR-TV-002`: show next relevant prayer/event and second-level countdown;
- `FR-TV-003`: show current mosque-local date and time;
- `FR-TV-004`: complete D-pad/select/back navigation;
- `FR-TV-005`: configurable built-in/custom background and contrast overlay;
- `FR-TV-006`: keep the display awake while foreground;
- `FR-TV-007`: recover after process recreation and power cycle;
- `FR-TV-008`: never blank because remote content failed;
- `FR-TV-009`: provide safe prayer-in-progress/dim mode as a later feature.
- `FR-TV-010`: allow a bounded device-local donation gratitude message; blank
  uses the current UI language's standard fallback and never changes schedule
  provenance.

### QR campaigns and announcements

- `FR-QR-001`: optional HTTPS URL, title and subtitle;
- `FR-QR-002`: generate QR locally;
- `FR-QR-003`: preview before activation;
- `FR-QR-004`: optional start/end and display placement;
- `FR-QR-005`: audit target URL changes;
- `FR-QR-006`: hide invalid/expired campaign without affecting prayer display;
- `FR-AN-001`: support a calm ticker/announcement area after MVP;
- `FR-AN-002`: emergency announcement must have explicit expiry.

### Synchronization and publication

- `FR-SY-001`: pilot local mode works without an account;
- `FR-SY-002`: remote mode pairs with a short-lived code;
- `FR-SY-003`: device downloads a version manifest before snapshot;
- `FR-SY-004`: verify hash, signature, schema and coverage before activation;
- `FR-SY-005`: failed update preserves last-known-good;
- `FR-SY-006`: keep a previous valid snapshot for rollback;
- `FR-SY-007`: support canary rollout and emergency revoke/rollback.

### Diagnostics

- `FR-DI-001`: show app/device version, mosque, timezone, snapshot, last sync and coverage;
- `FR-DI-002`: warn on implausible clock, timezone mismatch, stale/expired data or signature failure;
- `FR-DI-003`: export privacy-safe diagnostic bundle;
- `FR-DI-004`: expose boot/kiosk capability state;
- `FR-DI-005`: show concise source label to operator without cluttering the public display.

## Non-functional requirements

- `NFR-001 Correctness`: no silent fallback or source substitution;
- `NFR-002 Availability`: cached main screen survives at least seven days offline when coverage exists;
- `NFR-003 Performance`: target cold launch to cached display under three seconds on pilot hardware, then measure and revise;
- `NFR-004 Readability`: key times readable from the back of the real prayer hall under day/night lighting;
- `NFR-005 Privacy`: local mode needs no account, contacts, advertising ID or continuous precise location;
- `NFR-006 Auditability`: every publication/change has actor, timestamp, reason and before/after;
- `NFR-007 Recoverability`: rollback and restore are automated/tested;
- `NFR-008 Maintainability`: provider adapters cannot leak into domain/TV UI;
- `NFR-009 Accessibility`: focus, semantics, contrast and reduced motion are tested;
- `NFR-010 Security`: snapshots are signed; secrets are not stored in the APK/repo;
- `NFR-011 Compatibility`: MVP supports the selected pilot device first, then expands through a documented matrix.

## MVP screen set

1. Main prayer display.
2. First-run local setup / remote pairing.
3. Mosque/location selection.
4. Iqamah/Jumu'ah settings.
5. Theme/background settings.
6. QR campaign settings and preview.
7. Diagnostics/source screen.
8. Safe error/recovery screen.

Details: [UI_UX_SPEC.md](UI_UX_SPEC.md).

## MVP acceptance scenario

Given a TV with an approved cached schedule and no network, when the mosque-local date rolls over, the app shows the new day's adhan values, resolves iqamah rules, selects the correct next event and continues counting down. When the network returns and an invalid snapshot is offered, the client rejects it and continues with last-known-good while exposing a diagnostic warning.

## Explicitly out of scope for MVP

Payment processing, donation accounting, consumer mobile app, Quran reader, weather, AI religious advice, live video, video backgrounds, nationwide unapproved scraping, multi-tenant billing and arbitrary third-party plugins.

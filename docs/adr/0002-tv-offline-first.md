# ADR 0002: TV is offline-first and local state is authoritative for display

- Status: Proposed
- Date: 2026-08-19

## Context

Mosque TVs may have unstable Wi-Fi, OEM background limits and power cycles. Prayer display must not blank or change unpredictably because a backend/API is unavailable.

## Decision

Compose UI observes Room/SQLite only. Network synchronization downloads a candidate snapshot to staging, verifies integrity/schema/domain rules, imports transactionally and atomically switches the active pointer. WorkManager is used for inexact background sync, not prayer-time event accuracy.

T009 persists transport state and raw bytes outside Room using an atomic file
checkpoint plus staged/quarantined files. A pending stage is resumed before a
network call after process recreation only when its device/mosque/timezone/
manifest-origin fingerprint still matches current provisioning. Re-pairing
quarantines an old stage. Room remains the only display authority,
and its active/previous pointer changes only inside the importer transaction.

## Consequences

- main display works offline;
- network and UI are decoupled;
- testing requires staged-failure and migration coverage;
- device storage includes active/previous schedules/assets;
- real-time remote changes are eventually consistent rather than immediate guarantees.
- process interruption may leave a resumable stage or bounded rejected artifact,
  but never a partially active schedule.

## Rejected alternatives

- WebView/cloud page as main display;
- composable directly calling API;
- deleting current data before download;
- relying on exact periodic worker timing.

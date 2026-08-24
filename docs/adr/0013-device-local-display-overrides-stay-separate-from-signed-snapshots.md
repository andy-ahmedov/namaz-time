# ADR 0013: Device-local display overrides stay separate from signed snapshots

- Status: Accepted
- Date: 2026-08-21

## Context

The TV operator needs to configure one sadaqah QR presentation, an optional
standalone donation display and iqamah offsets for Fajr, Dhuhr, Asr, Maghrib
and Isha directly on the device.
The active Room snapshot is signed and provenance-bearing. Mutating that
snapshot from a local settings form would make the displayed values appear to
have authority they do not possess and would weaken rollback/recovery.

The existing QR generator and prayer-time engine already work entirely from
local state. What was missing was a validated operator input path and an
explicit projection boundary.

## Decision

QR URL, purpose, motivation and five minute offsets after adhan are validated and
stored in Preferences DataStore as device-local operator preferences. The QR
URL must be HTTPS and pass the existing campaign validation. Empty QR fields
disable the local panel. Sunrise cannot receive an iqamah value.

The public display projects valid operator iqamah offsets into ephemeral
current/next-day `offset_after_adhan` time-engine overrides. It never writes
them into Room or the signed snapshot. Sunrise cannot receive an offset.
Legacy fixed `HH:mm` preferences are not migrated because a conversion would
require choosing a date and adhan row; they are ignored and the approved base
policy remains active until the operator explicitly saves offsets.
Friday Dhuhr remains distinct from an applicable signed Jumu'ah session.

The operator QR takes display precedence over a snapshot campaign while it is
configured. It is explicitly local, has no approval or official-source claim,
and introduces no network call from Compose or the display reducer.

T028 adds an independent `schedule`/`donation` display preference and a
donation configuration containing an HTTPS QR target, transfer details,
localized operator text and an allowlisted image ID. Donation mode can be
activated only when all text fields and the existing campaign/QR validation
pass. Invalid or partial persisted content fails closed to schedule mode. Five
packaged image choices remain offline; the custom slot uses the same bounded
document-picker pipeline as Appearance but has a separate app-private file.
If that file is missing or corrupt, the donation screen renders its packaged
default. The screen performs no payment processing and does not write Room.

## Consequences

Positive:

- the mosque can configure the requested display without changing signed
  schedule provenance;
- invalid or partial QR configuration cannot reach the public display;
- five iqamah values remain independent and data-driven;
- donation mode remains usable without a schedule/network connection and keeps
  a focusable Settings path back to normal schedule mode;
- clearing the local values restores the signed snapshot projection.

Costs and limits:

- DataStore loss resets these local preferences;
- local values are not remotely approved, signed or fleet-managed;
- production remote mutation remains blocked on the authenticated approval
  and publication path;
- a physical-TV scan-distance and operator-entry acceptance run remains
  separate from emulator evidence.

## Rejected alternatives

- edit the active Room snapshot in place;
- label local iqamah or QR content as official;
- allow HTTP/arbitrary QR payloads;
- assign an iqamah to Sunrise;
- call an API from Compose when settings change.

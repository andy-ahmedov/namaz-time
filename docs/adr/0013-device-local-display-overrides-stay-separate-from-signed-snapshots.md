# ADR 0013: Device-local display overrides stay separate from signed snapshots

- Status: Accepted
- Date: 2026-08-21

## Context

The TV operator needs to configure one sadaqah QR presentation, an optional
standalone donation display and mosque-local iqamah controls directly on the
device.
The active Room snapshot is signed and provenance-bearing. Mutating that
snapshot from a local settings form would make the displayed values appear to
have authority they do not possess and would weaken rollback/recovery.

The existing QR generator and prayer-time engine already work entirely from
local state. What was missing was a validated operator input path and an
explicit projection boundary.

## Decision

QR URL and presentation copy are validated and stored in Preferences DataStore
as device-local operator preferences. The QR
URL must be HTTPS and pass the existing campaign validation. Empty QR fields
disable the local panel. Sunrise cannot receive an iqamah value.

As amended by T033 on 2026-08-29, Fajr, Asr, Maghrib and Isha remain bounded
`adhan + N minutes` controls. Dhuhr is a bounded mosque-local fixed clock time,
whose approved pilot default comes from the signed policy (`13:15`). One
operator Dhuhr value projects to both Dhuhr iqamah, including Friday, and every
applicable Jumu'ah session. The controls move in one-minute steps. Sunrise
cannot receive an iqamah.

The public display projects valid values into ephemeral current/next-day
time-engine overrides. It never writes them into Room or the signed snapshot.
The Dhuhr projection is rejected when it would precede source Dhuhr onset, so
the approved base policy remains active. The new fixed-time preference uses a
new DataStore key. The former integer Dhuhr-offset key and legacy `HH:mm` keys
are deliberately ignored rather than silently reinterpreted.

The operator QR takes display precedence over a snapshot campaign while it is
configured. It is explicitly local, has no approval or official-source claim,
and introduces no network call from Compose or the display reducer.

T028 adds an independent `schedule`/`donation` display preference and a
donation configuration containing an HTTPS QR target, local transfer details
and an allowlisted image ID. T029 replaces the ambiguous transfer-details blob
with bounded recipient, bank, card-number, SBP/phone and collection-link
fields. Existing labelled Russian/English blobs are deterministically mapped;
an unlabelled legacy blob is preserved as recipient text until the operator
edits and saves it. The former free-form public message is replaced by the
fixed localized gratitude copy specified for the display. Donation mode can be
activated only when the HTTPS target, at least one local detail field and the
existing campaign/QR validation pass. Invalid persisted content fails closed
to schedule mode. Five
packaged image choices remain offline; the custom slot uses the same bounded
document-picker pipeline as Appearance but has a separate app-private file.
If that file is missing or corrupt, the donation screen renders its packaged
default. The screen performs no payment processing and does not write Room.

## Consequences

Positive:

- the mosque can configure the requested display without changing signed
  schedule provenance;
- invalid or partial QR configuration cannot reach the public display;
- four offset controls and the linked Dhuhr/Jumu'ah time remain explicit and
  data-driven;
- donation mode remains usable without a schedule/network connection and keeps
  a focusable Settings path back to normal schedule mode;
- transfer-detail columns can be rendered consistently without parsing an
  operator blob in the display layer;
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

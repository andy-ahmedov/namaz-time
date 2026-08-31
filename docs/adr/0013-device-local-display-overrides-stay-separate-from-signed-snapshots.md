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

T033 also permits two bounded presentation-only identity fields: displayed
mosque name (80 Unicode code points) and displayed address (160). They are
trimmed, reject control characters and live only in Preferences DataStore.
Blank values restore the canonical/pilot display identity. The projection does
not and cannot change `mosqueId`, signed mosque/locality fields, source scope,
timezone, approval, provenance or Room snapshot bytes; canonical locality and
timezone remain visible as read-only context in Settings.

T028 adds an independent `schedule`/`donation` display preference and a
donation configuration containing an HTTPS QR target, local transfer details
and an allowlisted image ID. T029 replaces the ambiguous transfer-details blob
with structured fields. T043 removes the redundant collection-link field from
the active model, UI, validation and display. Recipient, bank, card number and
SBP/phone remain bounded. Existing labelled Russian/English blobs are
deterministically mapped while legacy collection-link lines are recognized and
ignored; the former dedicated DataStore key is never projected and is removed
on the next normal configuration save;
an unlabelled legacy blob is preserved as recipient text until the operator
edits and saves it. T033 adds a separate optional gratitude field bounded to
240 Unicode code points; it is trimmed, rejects control characters and remains
device-local. Blank stores no override and resolves at render time to the
localized RU/EN gratitude copy. Donation mode can be
activated only when the HTTPS target, at least one local detail field and the
existing campaign/QR validation pass. Invalid persisted content fails closed
to schedule mode. Five packaged image choices remain offline in a built-in-only
D-pad filmstrip; the custom image is a separate action using the same bounded
document-picker pipeline as Appearance and has a separate app-private file.
If that file is missing or corrupt, the donation screen renders its packaged
default. As amended by T033, the standalone screen's date, mosque-local time
and current-prayer label are a presentation projection of the same active Room
schedule and `PrayerTimeEngine` resolution used by the prayer display. It does
not calculate prayer times independently. If the active schedule is absent or
invalid, the screen fails closed to the existing unavailable state. The screen
performs no payment processing and does not write Room.

## Consequences

Positive:

- the mosque can configure the requested display without changing signed
  schedule provenance;
- invalid or partial QR configuration cannot reach the public display;
- four offset controls and the linked Dhuhr/Jumu'ah time remain explicit and
  data-driven;
- donation mode remains network-free and usable with the last-known-good active
  Room schedule, fails closed without one, and keeps a focusable Settings path
  back to normal schedule mode;
- transfer-detail columns can be rendered consistently without parsing an
  operator blob in the display layer;
- clearing the local values restores the signed snapshot projection;
- local identity customization cannot be mistaken for a source or timezone
  edit because those values remain read-only and separately presented;

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

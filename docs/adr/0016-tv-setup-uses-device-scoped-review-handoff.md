# ADR 0016: TV setup uses a device-scoped review handoff

- Status: Accepted
- Date: 2026-08-30

## Context

The Android TV setup flow must search canonical cities, discover all eligible
authoritative schedule choices and record an explicit operator choice. The
existing T039/T040 endpoints are admin surfaces. Shipping an admin bearer in
the APK, persisting it on a TV or broadening a device credential into an admin
credential would break RBAC and mosque isolation.

Selecting a row on the TV is also not religious or publication approval. The
current signed snapshot must remain active until the existing registry review,
approval, publication, signing and assignment pipeline finishes.

## Decision

The API exposes a bounded device setup surface authenticated with the existing
provisioned device bearer:

- the path carries only the authenticated device ID and must exactly match the
  bearer principal;
- mosque identity is always derived from that principal;
- registry revision is selected by private server configuration, never by a
  client parameter;
- no admin bearer, actor or unrestricted setup endpoint is available to the TV;
- city search reads the configured immutable setup revision, or the active
  revision when no staged review revision is configured;
- schedule choices reuse `PolicyAssessment` and `CityScheduleChoiceSet`, so the
  device cannot create alternative precedence, freshness or cardinality logic.

`device_setup_revision_ids` maps a mosque ID to one immutable staged revision
that is ready for operator review. It selects the candidate dataset, not a
religious authority and not a default schedule. All eligible same-tier choices
within that revision remain visible, neutrally ordered and require explicit
selection. An absent mapping exposes the current active choice read-only.

An explicit TV selection appends a `pending_review` proposal to
`device_registry_binding_requests` and an audit event with device, mosque,
canonical city, policy/choice, local date, interaction identity, UTC timestamp
and origin `local_tv_operator`. The table is append-only. A retry with the same
device/interaction and exact selection is idempotent; changed input conflicts.

The repository serializes the proposal against registry activation and
rechecks that the revision is still staged. It cannot write the active registry
pointer, approval/publication evidence, signed snapshots or device assignments.
The Android client does not write this pending state into the Room prayer
snapshot tables. Last-known-good remains the only display input.

## Consequences

- a stolen device credential remains limited to its own device and mosque;
- revocation immediately removes setup access through the existing device-auth
  path;
- deployment must explicitly expose a staged review revision per mosque when a
  new choice is intended;
- the runtime database role gains only `SELECT`/`INSERT` on the new append-only
  proposal table, not registry or authority write privileges;
- rollback from schema v8 to v7 removes unreviewed device proposals and must be
  preceded by backup/export; active registry state and signed snapshots remain
  unchanged.

## Rejected alternatives

- embedding or provisioning an admin bearer on the TV;
- accepting `mosque_id` or `revision_id` from the device;
- treating the first schedule choice as a default;
- updating Room or the active snapshot immediately after UI selection;
- reusing the admin-only request row while pretending the device is an admin;
- allowing device-originated approval, publication, activation, signing or
  assignment.

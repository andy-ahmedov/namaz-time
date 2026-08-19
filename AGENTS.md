# AGENTS.md

## Mission

Build a reliable mosque prayer-time display. Schedule correctness, provenance, offline behavior and recoverability are more important than feature count or visual similarity to a competitor.

## Evidence labels

Use exactly these labels in research, issues and PRs:

- `CONFIRMED_PUBLIC` — official public page/store documentation or a supplied screenshot.
- `CONFIRMED_STATIC` — clean-room static analysis of the user-supplied APK; not proof of runtime execution.
- `CONFIRMED_RUNTIME` — reproduced on a controlled physical device/emulator and recorded.
- `INFERENCE` — supported hypothesis, but not proven behavior.
- `PROPOSAL` — our own design decision.
- `UNKNOWN` — unresolved.

Never silently promote `CONFIRMED_STATIC` to `CONFIRMED_RUNTIME` or an inference to a fact.

## Non-negotiable product invariants

1. Never label calculated, scraped, inferred, stale or manually entered data as official unless stored provenance and mosque approval support that claim.
2. Every published schedule must be traceable to source, authority, geographic scope, timezone, effective range, retrieval/import timestamp, raw hash, parser version and approval state.
3. Adhan and iqamah are distinct. Iqamah is mosque-local unless an explicit approved source says otherwise.
4. The TV UI reads only from local persistence. Network code cannot be called from composables or display state reducers.
5. A failed sync must leave the last-known-good snapshot active.
6. Store IANA timezone IDs; never rely only on numeric UTC offset or device locale.
7. Date rollover, leap years, DST/timezone changes, high-latitude rules, Ramadan periods, Jumu'ah sessions and one-off overrides are first-class test cases.
8. Never silently change source strategy when data becomes stale. Any fallback must be configured and pre-approved.
9. Snapshots delivered to devices must be versioned, hashed and cryptographically signed. Encryption alone is not source authenticity.
10. Do not add broad Android permissions, analytics or identifiers without a documented requirement and threat review.

## Clean-room restrictions

- Do not copy IslamApp source, decompiled code, package-private names, full datasets, proprietary backgrounds, icons, branding, wording or pixel-perfect layouts.
- Do not commit the uploaded APK, decrypted competitor data, embedded secrets, raw decompilation output or extracted proprietary resources.
- Do not bypass authentication, TLS pinning, anti-tamper, store protection or access controls.
- Static findings may inform requirements and independent interfaces; implementation must be written from this repository's specifications.
- Use only synthetic fixtures in Git unless an authoritative source has granted usage rights.

## Prayer-time source rules

Allowed provider kinds:

- `official_api`
- `official_file`
- `official_html`
- `mosque_calendar`
- `calculation_profile`
- `manual_import`

Every provider must implement:

1. raw artifact capture or response metadata;
2. deterministic normalization;
3. validation and diffing;
4. fail-closed schema drift behavior;
5. approval before publication unless an ADR explicitly permits auto-publish;
6. reproducible fixture tests.

The TV client must never scrape authority websites.

## Repository map

- `apps/tv-android/` — Kotlin Android TV client.
- `cmd/api/` — Go control-plane API.
- `cmd/ingestor/` — Go ingestion command/jobs.
- `internal/domain/` — source-independent domain model.
- `internal/providers/` — adapters, parsers and normalizers.
- `web/admin/` — operator/approver UI.
- `contracts/` — versioned schemas and OpenAPI.
- `examples/` — synthetic fixtures only.
- `research/evidence/` — sanitized summaries, never raw competitor content.
- `docs/adr/` — architectural decisions.

Directory-specific `AGENTS.md` files may add constraints but cannot weaken this file.

## Required workflow for every task

1. Read `START_HERE.md`, relevant specifications, current `PLANS.md` and applicable ADRs.
2. Restate the exact scope and list correctness-sensitive unknowns.
3. Inspect existing code before editing.
4. Make the smallest coherent change.
5. Add tests before claiming completion.
6. Run narrow tests, then repository-wide checks.
7. Update docs/contracts only when behavior or decisions changed.
8. Update `PLANS.md` progress/status.
9. Report commands executed, results, remaining risks and changed files.

## Stable commands

Prefer repository commands over ad-hoc commands:

```bash
make docs-check
make test
make lint
```

If a command does not exist yet, create it in the task that introduces the relevant toolchain and document it.

## Go rules

- Keep domain types free of HTTP, SQL and provider-specific names.
- Use explicit constructors/validation for time-affecting entities.
- Preserve raw source data separately from normalized/published data.
- Propagate `context.Context` across I/O boundaries.
- Wrap errors with operation and stable identifiers; never include secrets.
- Use table-driven tests and golden fixtures where appropriate.
- Parser schema drift must fail closed.

## Android TV rules

- Compose for TV, D-pad/select/back first; no touch-only interactions.
- UI reads immutable local state and remains renderable offline.
- Display local time using the mosque timezone, not blindly the device timezone.
- Test 720p, 1080p and 4K density/layout behavior on target hardware.
- Keep screen-on only while display mode is active.
- Normal boot receiver behavior is best effort. Label managed kiosk/device-owner as a separate deployment mode.
- Do not request contacts, advertising ID or precise location for local/manual setup.

## Definition of done

A task is complete only when:

- acceptance criteria pass;
- tests and checks were run and recorded;
- no source/provenance invariant was weakened;
- no competitor artifact or secret entered Git;
- docs/contracts match behavior;
- rollback or migration impact is addressed;
- `PLANS.md` is updated.

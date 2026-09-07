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

1. Official-source claims require verified first-party Muslim authority, exact scope, current reproducible timetable or sufficient verified official calculation policy. NamazTime source qualification is not external endorsement; neither mosque approval, a named external approver nor written partnership is required for qualified public first-party sources (ADR 0019). Never promote generic calculations, inferred data or stale rows to official current data.
2. Every published schedule must be traceable to source, authority, geographic scope, timezone, effective range, retrieval/import timestamp, raw/content hash, parser version, validation and qualification decision (or retained legacy manual approval).
3. Adhan and iqamah are distinct. Regional onset data never supplies mosque iqamah implicitly; iqamah stays explicitly mosque/device-local, and Jumu'ah requires its own applicable evidence.
4. The TV UI reads only from local persistence. Network code cannot be called from composables or display state reducers.
5. A failed sync must leave the last-known-good snapshot active.
6. Store IANA timezone IDs; never rely only on numeric UTC offset or device locale.
7. Date rollover, leap years, DST/timezone changes, high-latitude rules, Ramadan periods, Jumu'ah sessions and one-off overrides are first-class test cases.
8. Never silently change source strategy when data becomes stale. A city without a qualified applicable first-party timetable or verified official calculation policy is unavailable; generic, neighboring-city, subject-capital and inferred fallbacks are forbidden.
9. Snapshots delivered to devices must be versioned, hashed and cryptographically signed. Encryption alone is not source authenticity.
10. Do not add broad Android permissions, analytics or identifiers without a documented requirement and threat review.

## Clean-room restrictions

- Product-owner-supplied visual references may be reproduced accurately in
  layout, styling, decorative patterns, icons and wording when the product
  owner explicitly confirms that the project may use them. Record that basis
  in the relevant UI/evidence documentation. This permission does not extend
  to source code, decompiled code, package-private names, full datasets or
  APK-extracted resources.
- Do not commit the uploaded APK, decrypted competitor data, embedded secrets, raw decompilation output or extracted proprietary resources.
- Do not bypass authentication, TLS pinning, anti-tamper, store protection or access controls.
- Static findings may inform requirements and independent interfaces; implementation must be written from this repository's specifications.
- Use synthetic/sanitized fixtures in Git unless redistribution rights support retaining the real artifact. Ordinary public first-party research, qualification and operational use do not require separate written permission. Respect explicit restrictive terms, authentication, anti-bot and other access controls; never bypass them. Keep substantial raw PDFs/databases/assets outside Git when redistribution rights are unclear, retaining canonical URL, retrieval metadata, hash and parser version instead.

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
5. machine-verifiable source qualification before public first-party publication (ADR 0019), or the retained manual/mosque approval path where applicable; adapters cannot self-publish or fabricate an external approver;
6. reproducible fixture tests.

The TV client must never scrape authority websites.

Source qualification and optional external endorsement/partnership are separate
records. Research all applicable authorities independently; prefer more specific
evidence only within a confirmed authority chain, never to suppress another
qualified authority. Return all applicable choices without top-N or religious
ranking. Synthetic sources belong only in tests or unmistakably explicit evidence
scenarios, not normal interactive setup. See SOURCE_PARTNERSHIP_CHECKLIST.md.

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

## Workflow by task

Read `START_HERE.md` for unfamiliar product/deployment context and the relevant
`PLANS.md` entry when continuing tracked work. Read specification sections and
ADRs that govern the requested change; do not load the whole document stack
for an unrelated edit. Inspect affected code before changing it.

- **Explain, audit, review or diagnose:** inspect and report evidence; do not
  modify files, add tests, update plans or implement fixes unless requested.
  Relevant read-only diagnostics are allowed.
- **Documentation, skills or mechanical edits:** validate affected links,
  commands and packaging with `make docs-check` and, for skills, `make test-skills`.
  Add regression coverage for changed executable behavior. Application builds
  are not required unless their inputs or behavior changed.
- **Implementation:** identify acceptance criteria and correctness-sensitive
  unknowns, make the smallest coherent change, and add/update affected tests.
  Run narrow checks, fix regressions caused by the change and rerun them;
  then run `make test` and `make lint`. Apply additional signing, database or
  device gates from `TEST_STRATEGY.md` when those boundaries are touched.
- Update affected docs/contracts when behavior or decisions change. Record
  substantive implementation/instruction changes in `PLANS.md`; do not create
  plan entries for read-only answers or incidental typo fixes.

Continue safe, in-scope local implementation and verification through the
acceptance criteria, not just the first draft. Ask only when a missing choice,
authority or permission materially blocks progress. Audit authorization does
not authorize fixes; implementation authorization does not authorize publication,
pushes, deployment, external endorsement, signing-key changes or data deletion.
Source-onboarding work may qualify public sources autonomously under ADR 0019;
local activation/materialization requires task authorization (explicit in T049).
Check test targets before running commands that can access external systems or
modify an existing device/database; do not assume every fixture is disposable.

Report changed files, checks actually run, failures/skips and remaining risks.

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

## Definition of done for changes

A task is complete only when:

- acceptance criteria pass;
- task-appropriate tests and checks were run and recorded;
- no source/provenance invariant was weakened;
- no competitor artifact or secret entered Git;
- docs/contracts match behavior;
- rollback or migration impact is addressed;
- `PLANS.md` is updated for substantive work as described above.

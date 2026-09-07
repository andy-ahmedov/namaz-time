# Public source qualification and optional partnership

Policy: [ADR 0019](docs/adr/0019-public-first-party-source-qualification.md),
accepted by the product owner for T049 on 2026-09-08. This supersedes the old
mandatory-contact/written-permission/external-approver checklist. Public-source
qualification can be performed autonomously; do not contact organizations or
wait for their responses in T049.

## Required source qualification

- [ ] Record canonical organization name and first-party evidence of publisher
  ownership/affiliation; a Muslim-themed website or aggregator is insufficient.
- [ ] Record exact locality/district/subject applicability from source evidence;
  a capital-city table is not a subject-wide table. Bind canonical catalog IDs.
- [ ] Identify exact timetable versus official calculation policy. For policy,
  require sufficient published parameters, rounding/seasonal/high-latitude rules
  and reproducible comparisons to first-party prayer values across seasons.
- [ ] Record canonical source URL, source type, retrieval time/status/content
  type, raw/content SHA-256 and parser/normalizer version. Keep operational raw
  bytes outside Git when redistribution rights are unclear.
- [ ] Review publicly stated restrictive terms and transport/access limits.
  Absence of separate written permission does not fail this check. Explicit
  restrictions or technical blocks must be respected; use another legitimate
  first-party transport/policy or mark unavailable. Do not bypass controls.
- [ ] Record effective range, IANA timezone and exact covered localities. Never
  turn daily, monthly or Ramadan-only coverage into an annual schedule.
- [ ] Identify onset versus recommended performance/iqamah/Jumu'ah fields.
  Unpublished Asr/madhhab or Fajr/Isha methodology may remain UNKNOWN for an
  exact table; it cannot be guessed to create a calculation policy.
- [ ] Validate actual dates, missing/duplicate/gap/order/timezone conditions,
  relevant seasonal transitions, deterministic normalization and schema drift.
  Store a reproducible diff and resolve validation failures before qualification.
- [ ] Record cadence/version if discoverable, evidence date, currentness checks
  and a bounded coverage/freshness policy. Do not invent an organization SLA.
- [ ] Bind the qualification decision to exact evidence, source/scope, raw and
  normalized hashes, parser and validation. An enum or URL alone is not proof.
- [ ] Independently qualify each parallel authority. Do not truncate choices,
  infer religious precedence, average rows or silently select another source.

## Activation and recovery

- [ ] Verify the qualification record and exact materialized artifact binding
  before publication. Preserve versioned, immutable, hashed, signed snapshots.
- [ ] Record the NamazTime qualification identity honestly; never put an invented
  person or organization into `approved_by`. Signing attests NamazTime artifact
  integrity, not endorsement of NamazTime by the source organization.
- [ ] Show source authority, type, exact scope, range and provenance summary.
  Missing qualified coverage is unavailable, not a demo or generic replacement.
- [ ] Test stale/unavailable behavior, failed-sync last-known-good, signature/hash
  rejection and rollback. Keep iqamah device/mosque-local unless independently
  evidenced; no regional onset-to-iqamah inference.

## Optional external endorsement / partnership

Record only when actually supplied: contact/role, written license or permission,
required attribution, mosque preference, correction channel, partnership dates
and scope. These facts supplement qualification; their absence neither disproves
source authority nor blocks ordinary public first-party operational use.
Do not represent source availability as a partnership or a mosque preference.

Legacy pilot example: [Ulyanovsk 2026 checklist](fixtures/pilot/ulyanovsk-2026/partnership-checklist.md).
Its actual named approval, authorized retained artifacts and mosque-local rules
remain valid evidence for that pilot, not prerequisites for unrelated sources.

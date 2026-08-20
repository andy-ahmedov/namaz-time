---
name: prayer-times-provider
description: Implement or review NamazTime prayer-time source adapters with explicit authority, provenance, deterministic normalization, validation, and fail-closed publication boundaries.
---

# Prayer-times provider skill

Use this skill when implementing or reviewing a source adapter.

## Required reading

- `AGENTS.md`
- `PRAYER_TIMES_DATA.md`
- `SOURCE_PARTNERSHIP_CHECKLIST.md`
- `DATA_MODEL.md`
- `TEST_STRATEGY.md`

## Workflow

1. Confirm source kind, authority, scope, permission and cadence. Stop if unknown.
2. Add a source definition/decision record.
3. Add sanitized raw fixture plus hash metadata; never use a live site in normal CI.
4. Implement retrieval separately from parse.
5. Pin parser version and fail closed on schema drift.
6. Normalize into candidate rows only.
7. Add missing/duplicate/gap/timezone/order/delta tests.
8. Produce deterministic diff against approved fixture.
9. Do not add approval bypass or direct publication.
10. Update docs and `PLANS.md`; run repository gates.

## Completion report

State evidence label, source permission status, exact scope, fixtures, parser warnings, tests, remaining unknowns and why the provider is authoritative for the configured mosque.

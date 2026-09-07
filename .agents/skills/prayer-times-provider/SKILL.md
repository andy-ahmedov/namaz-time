---
name: prayer-times-provider
description: "Implement or review prayer-time source adapters, normalization and provenance; not display styling or device schedule selection."
---

# Prayer-time providers

Follow the source and publication invariants in repository AGENTS.md.

## Context by concern

Read relevant sections of PRAYER_TIMES_DATA.md for source semantics, DATA_MODEL.md
for records, SOURCE_PARTNERSHIP_CHECKLIST.md and ADR 0019 for qualification, and
TEST_STRATEGY.md for provider/validation tests. Paths are repository-relative.

## Authority boundary

Qualify public first-party sources autonomously from evidence: real Muslim
organization ownership, exact geographic scope, current timetable or sufficient
official calculation policy, reproducible values and permitted transport.
External endorsement, named human approver, mosque approval and written
partnership are optional separate facts, not public-source onboarding gates.
Respect restrictive terms/access controls; no bypass or unrequested contact.
Unknown authority/scope or unverified calculation parameters means unavailable,
not generic/neighboring fallback. Record unknown cadence without inventing it.

## Adapter work

- Preserve raw artifact/hash metadata separately from normalized candidates.
- Keep retrieval separate from deterministic parsing; normal CI uses local fixtures.
- Pin parser version and fail closed on schema drift.
- Validate missing/duplicate/gap/timezone/order/delta cases affected by the change.
- Produce a reproducible diff and hash-bound qualification decision; do not
  publish from the adapter or fake a human approval. Retain legacy pilot receipts.
- Keep substantial raw artifacts outside Git unless redistribution is supported;
  retain URL, retrieval/hash/parser evidence and sanitized/synthetic test fixtures.
- Keep independent qualified authorities as separate complete choices. Scope
  specificity applies within one evidenced authority chain, not across them.

For reviews, report findings only. For implementation, complete affected tests and
repository gates, update changed contracts/decisions and tracked progress.
Report qualification, optional endorsement, warnings and unresolved scope
separately. Official calculations require sufficient first-party parameters and
multi-season first-party value comparisons; a library/method name is not proof.

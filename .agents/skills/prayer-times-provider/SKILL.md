---
name: prayer-times-provider
description: "Implement or review prayer-time source adapters, normalization and provenance; not display styling or device schedule selection."
---

# Prayer-time providers

Follow the source and publication invariants in repository AGENTS.md.

## Context by concern

Read relevant sections of PRAYER_TIMES_DATA.md for source semantics, DATA_MODEL.md
for records, SOURCE_PARTNERSHIP_CHECKLIST.md for authority/permission, and
TEST_STRATEGY.md for provider/validation tests. Paths are repository-relative.

## Authority boundary

Establish source kind, authority, geographic/mosque scope, reuse permission and
retrieval cadence. Unknown authority or permission blocks real-data ingestion/use
where authorization is needed, and always blocks approval/publication.
It does not prevent in-scope public research or parser work on synthetic fixtures.
Record missing cadence as unknown; do not invent freshness or fallback policy.

## Adapter work

- Preserve raw artifact/hash metadata separately from normalized candidates.
- Keep retrieval separate from deterministic parsing; normal CI uses local fixtures.
- Pin parser version and fail closed on schema drift.
- Validate missing/duplicate/gap/timezone/order/delta cases affected by the change.
- Produce a reproducible diff; do not bypass approval or publish from the adapter.
- Use only fixtures whose use/redistribution is authorized, or synthetic fixtures.

For reviews, report findings only. For implementation, complete affected tests and
repository gates, update changed contracts/decisions and tracked progress.
Report permission and approval states, warnings and unresolved scope explicitly.
For calculated/manual sources, do not claim religious authority merely because
a provider exists; official status still requires stored evidence and approval.

# Repository documentation manifest

## Orientation

- `README.md` — project overview.
- `START_HERE.md` — decisions and first vertical slice.
- `AGENTS.md` — permanent Codex rules.
- `PLANS.md` — phases and execution log.
- `CODEX_WORKFLOW.md` — day-to-day agent workflow.
- `CODEX_TASKS.md` — first bounded implementation tasks.

## Research

- `RESEARCH_REPORT.md` — consolidated conclusions.
- `APK_RESEARCH_ISLAMAPP_1_6_2.md` — clean-room static APK analysis.
- `PRAYER_TIME_SOURCE_PATTERNS.md` — data acquisition patterns and scraping answer.
- `MAWAQIT_API_RESEARCH.md` — current official authenticated API vs third-party scraping.
- `COMPETITOR_RESEARCH.md` — product comparison.
- `BLACK_BOX_VALIDATION_PLAN.md` — remaining runtime experiment plan.
- `ANDROID_TV_RUNTIME_SETUP_WSL.md` — WSL/ADB setup and safe evidence handoff.
- `SOURCES.md` — public references and retrieval date.
- `research/evidence/islamapp-1.6.2-static-summary.json` — sanitized machine-readable findings.
- `research/README.md` — research evidence policy.

## Product and design

- `PRODUCT_REQUIREMENTS.md`
- `UI_UX_SPEC.md`
- `DECISIONS.md`

## Data and architecture

- `PRAYER_TIMES_DATA.md`
- `DATA_MODEL.md`
- `ARCHITECTURE.md`
- `API_CONTRACT.md`
- `contracts/openapi.yaml`
- `contracts/prayer-snapshot.schema.json`
- `contracts/source-record.schema.json`
- `examples/synthetic-prayer-snapshot.json`
- `fixtures/pilot/ulyanovsk-2026-08/` — authorized raw August 2026 image,
  provenance/source record and controlled transcription; unapproved.
- `fixtures/synthetic/manual-annual-2025.csv` — static 365-day parser/publication golden.
- `fixtures/verification/` — signed synthetic snapshot plus public test key only.
- `docs/adr/0001-source-authority-and-signed-snapshots.md`
- `docs/adr/0002-tv-offline-first.md`
- `docs/adr/0003-canonical-snapshot-signatures.md`

## Technical scaffold

- `go.mod` — Go module identity and language baseline.
- `cmd/api/` — private-configured device pairing/manifest/snapshot HTTP runtime.
- `internal/devices/` — strict pairing fixture, bearer-scoped manifest/snapshot
  service and signed immutable registry validation.
- `cmd/ingestor/` — local manual-fixture inspection CLI; it cannot approve or publish.
- `internal/domain/` — source-independent snapshot types, validation and contract fixtures.
- `internal/providers/manual/` — strict raw-artifact + manual CSV candidate provider.
- `internal/publication/` — deterministic diff, approval gate, canonical signing and verification.
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/` — Android Gradle build and wrapper.
- `apps/tv-android/` — Compose for TV shell, D-pad/UI tests, DataStore
  preferences, strict bundled-snapshot bootstrap, Room v1 baseline and tested
  v1→v2→v3 source-flags/provenance migrations, and the responsive offline main
  prayer display with a mosque-timezone next-event/iqamah/Jumu'ah engine and a
  locally generated, lifecycle-validated optional QR campaign panel. Signed
  production snapshots require cross-platform Ed25519 authenticity evidence
  before the atomic Room importer can accept them. T009 adds encrypted device
  provisioning, paired-mosque binding, same-origin bounded HTTPS,
  provisioning-scoped durable stage/quarantine/checkpoint, WorkManager
  scheduling and authenticated rollback/recovery tests.
- `.github/workflows/ci.yml` — documentation, Go and Android CI gates.

## Quality, security and operations

- `TEST_STRATEGY.md`
- `SECURITY_PRIVACY.md`
- `OPERATIONS.md`
- `SOURCE_PARTNERSHIP_CHECKLIST.md`
- `CONTRIBUTING.md`
- `scripts/docs-check.sh`
- `scripts/android-tv-evidence.sh` — read-only ADB evidence helper for owned test hardware.
- `Makefile`
- `SHA256SUMS.txt`

## Codex skills

- `.agents/skills/prayer-times-provider/SKILL.md`
- `.agents/skills/android-tv-screen/SKILL.md`

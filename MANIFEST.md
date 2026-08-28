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
- `fixtures/pilot/ulyanovsk-2026/` — authorized raw annual 2026 Ulyanovsk PDF,
  strict controlled transcription, extraction provenance and August conflict
  ledger; unapproved.
- `fixtures/synthetic/manual-annual-2025.csv` — static 365-day parser/publication golden.
- `fixtures/verification/` — signed synthetic snapshot plus public test key only.
- `docs/adr/0001-source-authority-and-signed-snapshots.md`
- `docs/adr/0002-tv-offline-first.md`
- `docs/adr/0003-canonical-snapshot-signatures.md`
- `docs/adr/0004-production-pairing-and-fleet-scope.md`
- `docs/adr/0005-admin-rbac-idempotency-and-assignments.md`
- `docs/adr/0006-heartbeat-is-latest-only-and-non-authoritative.md`
- `docs/adr/0007-canary-rollouts-are-bounded-device-assignments.md`
- `docs/adr/0008-support-bundles-are-bounded-current-state.md`
- `docs/adr/0009-fleet-backups-restore-into-a-clean-database.md`

## Technical scaffold

- `go.mod` — Go module identity and language baseline.
- `cmd/api/` — private-configured device/admin HTTP runtime with explicit
  PostgreSQL pairing, RBAC and idempotency-key-ring wiring.
- `cmd/migrate/` — short-lived explicit-target PostgreSQL schema migration
  command; schema-owner credentials never enter the API process.
- `internal/devices/` — strict pairing fixture, production pairing/admin
  managers, PostgreSQL v1/v2/v3/v4/v5 migrations/repositories, mosque-scoped fleet
  administration, bearer-scoped manifest/snapshot service and signed immutable
  registry validation, latest-only privacy-safe device health and bounded
  canary rollout cohorts, plus a no-store bounded support projection.
- `cmd/ingestor/` — local version-dispatched manual/official-file inspection CLI; it cannot approve or publish.
- `internal/domain/` — source-independent snapshot types, validation and contract fixtures.
- `internal/providers/manual/` — strict raw-artifact + manual CSV candidate provider.
- `internal/providers/sourceconfig/` — source-registry fields shared by strict
  provider adapters without sharing provider trust policy.
- `internal/providers/controlled/` — source-independent validation shared by
  controlled, human-reviewed transcription adapters.
- `internal/providers/officialpdf/` — strict source-specific Ulyanovsk 2026
  official-PDF controlled-transcription provider.
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
  scheduling and authenticated rollback/recovery tests. T013 adds the strict
  best-effort heartbeat client without display-state coupling.
- `.github/workflows/ci.yml` — documentation, Go and Android CI gates.

## Quality, security and operations

- `TEST_STRATEGY.md`
- `SECURITY_PRIVACY.md`
- `OPERATIONS.md`
- `BACKUP_RESTORE_RUNBOOK.md`
- `docs/reviews/2026-08-28-engineering-security-review.md` — independent
  repository-wide findings, remediation evidence and readiness verdict.
- `SOURCE_PARTNERSHIP_CHECKLIST.md`
- `CONTRIBUTING.md`
- `scripts/docs-check.sh`
- `scripts/android-tv-evidence.sh` — read-only ADB evidence helper for owned test hardware.
- `scripts/test-postgres.sh` — disposable PostgreSQL pairing/RBAC/admin integration gate.
- `scripts/test-postgres-restore.sh` — disposable clean-database backup/restore gate.
- `scripts/fixtures/postgres-backup-restore-seed.sql` — synthetic linked fleet
  recovery fixture; contains no production data or plaintext production secret.
- `Makefile`
- `SHA256SUMS.txt`

## Codex skills

- `.agents/skills/prayer-times-provider/SKILL.md`
- `.agents/skills/android-tv-screen/SKILL.md`

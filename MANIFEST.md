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
- `docs/adr/0001-source-authority-and-signed-snapshots.md`
- `docs/adr/0002-tv-offline-first.md`

## Technical scaffold

- `go.mod` — Go module identity and language baseline.
- `cmd/api/` — compilable control-plane API entry-point placeholder.
- `cmd/ingestor/` — compilable ingestion entry-point placeholder.
- `internal/domain/` — source-independent domain package boundary.
- `internal/providers/` — provider adapter package boundary.
- `settings.gradle.kts`, `build.gradle.kts`, `gradle/` — Android Gradle build and wrapper.
- `apps/tv-android/` — minimal Kotlin Android TV launcher and local unit test.
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

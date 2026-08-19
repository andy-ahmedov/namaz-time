# CONTRIBUTING.md

## Before changing code

Read:

1. [AGENTS.md](AGENTS.md)
2. [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md)
3. [ARCHITECTURE.md](ARCHITECTURE.md)
4. the relevant contract/ADR/task.

## Branch and commit discipline

- one bounded task per branch/PR;
- small reviewable commits;
- no generated/build artifacts unless documented;
- no competitor APK, extracted data/assets/secrets or personal credentials;
- synthetic fixtures by default;
- real source fixture only with recorded permission and sanitization policy.

Suggested commit prefixes:

```text
feat: fix: docs: test: refactor: build: chore:
```

## Required checks

Run the stable repository checks:

```bash
make docs-check
make test-go
make test-contracts
make test-android-unit
make lint
```

Use `make format` for Go formatting. Android builds use the checked-in Gradle
wrapper, JDK 17 and Android SDK 35. A PR description lists exact commands
actually executed; do not claim instrumented/device coverage from local unit
tests.

`make test-android-unit` includes the Robolectric Compose focus test. Commit
Room schema exports under `apps/tv-android/schemas/` whenever the schema changes;
future versions must add and test an explicit migration rather than use a
destructive fallback. The v1→v2→v3 path is exercised by opening a v1 database
and validating it through the real migrations. The command also fails when
Room compilation rewrites a tracked schema export.

## Provider contribution requirements

A new prayer-time provider must include:

- source definition and scope;
- permission/attribution reference;
- sanitized fixture and raw hash metadata;
- parser contract tests;
- malformed/schema-drift fixture;
- validation and minute-diff tests;
- stale/update policy;
- ADR/decision stating why source is authoritative;
- no automatic publication path.

## UI contribution requirements

- D-pad-only test;
- explicit focus behavior;
- 720p/1080p/4K layout evidence where affected;
- contrast and long/RTL text review;
- no copied competitor assets/layout;
- main prayer display cannot depend on a network request.

## Documentation

Update docs only when behavior/contract/decision changes. Do not claim future behavior as implemented. Update `PLANS.md` task status and record known risks.

## Review checklist

- [ ] Source/provenance invariants preserved.
- [ ] Adhan and iqamah remain separate.
- [ ] Timezone behavior covered by tests.
- [ ] Last-known-good cannot be destroyed by failure.
- [ ] No secret or proprietary evidence committed.
- [ ] New permission/dependency justified.
- [ ] Tests executed and reported.
- [ ] Docs/contract/ADR updated where needed.

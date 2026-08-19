# CODEX_WORKFLOW.md

## Environment

The expected development setup is Windows 11 with WSL Ubuntu 24.04. Repositories live inside WSL, for example:

```bash
cd /home/andy/github.com/andy-ahmedov/mosque-prayer-tv
code .
```

Run Codex from the VS Code WSL terminal:

```bash
codex
```

Keep Git, Go, Java/Gradle, Android SDK and Node tooling inside a consistent WSL/Windows arrangement. Android emulator performance may require Windows-hosted Android Studio; physical-device ADB can be bridged to WSL or run from Windows. Record the chosen setup in `DECISIONS.md` once tested.

## Before the first Codex task

```bash
make docs-check
git init
git add .
git commit -m "docs: bootstrap mosque prayer TV repository"
```

Resolve at least the pilot mosque, source and hardware decisions before implementing production data.

## Task shape

Give Codex one bounded task from [CODEX_TASKS.md](CODEX_TASKS.md). A task must state:

- goal;
- in-scope files/modules;
- explicit non-goals;
- invariants from `AGENTS.md`;
- acceptance commands;
- expected documentation updates.

Do not say “implement the whole app.”

## Recommended prompt template

```text
Implement task T00X from CODEX_TASKS.md.

Before changing files:
1. Read AGENTS.md, PLANS.md and the task's linked specs.
2. Inspect the current repository and report any conflict with the task.
3. Keep the task bounded; do not start later tasks.

Required:
- preserve all source/provenance/offline invariants;
- use only synthetic fixtures unless an approved source is already committed;
- add tests and run the acceptance commands;
- update PLANS.md and affected docs truthfully;
- finish with changed files, test results, remaining risks and commit-ready summary.
```

## Execution loop

1. **Preflight:** clean tree, branch/remote status, tool versions.
2. **Inspect:** read existing code/tests/specs before proposing changes.
3. **Plan:** short task-local plan only.
4. **Implement:** smallest coherent diff.
5. **Verify:** run targeted tests, then repository gates.
6. **Review:** inspect diff for scope creep and invariants.
7. **Document:** update plan/contracts/README only where behavior changed.
8. **Checkpoint:** commit when green.

## Permission handling

Codex may request permission for commands outside its sandbox. Grant only actions needed for the current task. Broad permission does not remove the requirement to stay in task scope or avoid destructive commands.

Never authorize:

- uploading the competitor APK or extracted data;
- publishing secrets/credentials;
- bypassing third-party protection;
- destructive production operations;
- changing official schedule data without the approval workflow.

## Useful commands

Initially:

```bash
make docs-check
```

Later, the repository should stabilize these commands:

```bash
make fmt
make lint
make test
make test-go
make test-android-unit
make build
```

Codex should prefer repository commands over undocumented one-off invocations.

## Working with long tasks

For research or provider work, use explicit phases with stop conditions:

```text
Phase 1: inspect source contract and fixtures; stop if permission/schema unknown.
Phase 2: implement parser only; stop if golden fixture differs.
Phase 3: integrate candidate validation; no publication.
Phase 4: add approval/publication in a separate task.
```

This is useful task decomposition, not micromanagement: each phase protects a correctness boundary.

## After a task

Require a result report containing:

- changed files;
- behavior implemented;
- exact tests/commands and outcomes;
- assumptions/evidence labels;
- docs updated;
- unresolved risks;
- suggested next single task.

Never accept “tests should pass” without executed evidence.

# Controlled emulator process-death recovery

Date: 2026-09-19. Scope: explicit synthetic debug evidence on Android TV API 36,
`emulator-5554`; no physical-TV or power-loss claim.

## Mechanism and isolation

PROPOSAL: `ProcessRecoveryEvidenceActivity` exists only in the debug source set
and runs in `:recoveryEvidence`. Each invocation uses a fresh UUID directory
under `files/recovery-evidence/`, containing an isolated Room database,
DataStore preferences and durable sync journal. The host uploads only existing
synthetic fixtures and their public verification key. Production Room/import,
signature verification, sync recovery and preference repository code are reused.
No actual source, schedule, provisioning or signing key is changed.

The activity first imports the baseline, commits English language and 35%
transparency preferences, and stages a signed synthetic successor through a
fake transport. It writes a durable ready marker at one of two boundaries:

1. after successor rows are inserted, inside the Room transaction, before the
   active pointer is changed;
2. after the Room transaction commits, before sync completes its checkpoint.

The host verifies the PID belongs to the dedicated process, sends `SIGKILL`
using debug `run-as`, waits for termination, then launches a fresh process.
Resume checks the expected pre-recovery pointer/row presence, retained baseline
row count and both committed preferences. A transport that fails on every
request ensures recovery uses the durable staged file. Final checks cover
active/previous selection, two retained snapshots, cleared pending manifest,
accepted checkpoint, SQLite integrity and foreign keys.

## Recorded result

CONFIRMED_RUNTIME: both boundaries pass on API 36. Recovery runs in different
PIDs with zero transport calls. The baseline remains active after an uncommitted
transaction is killed; the successor remains active after a committed
transaction is killed. Both resume to the successor with the baseline retained
as previous. Committed preferences survive both kills.

CONFIRMED_RUNTIME: byte hashes of the ordinary debug database, its WAL and
operator preferences are identical before/after the complete controlled run.
The separate pilot package is not targeted. Raw evidence stays outside Git at
`/tmp/namaztime-process-recovery-20260919-final/`; `report.json` pins the installed
APK SHA-256, Git base/dirty state, device SDK, PIDs and protected-file hashes.

An initial launch raced the APK installation and did not start the activity.
The next attempt exposed `adb exec-out`'s missing remote exit status handling:
an absent error marker was mistaken for an error. The runner now uses non-PTY
`adb shell -T` with exit status, and both complete subsequent runs pass. These
were harness failures, not evidence of a production recovery defect.

UNKNOWN: physical power loss, storage failure, OEM process policies, killing
the ordinary display process during real network delivery, and arbitrary
mid-download boundaries. Committed DataStore preferences were checked; an
in-flight DataStore write was not interrupted. No production bug fix was
needed or inferred from this test.

## Repository validation

`make test lint test-android-all` PASS, including strict dependency verification,
debug/release APK identity and unchanged Room schema. Android: 636 cases,
632 passed, four optional external-fixture skips. Skills: two existing skips.
Manifest and DEX inspection finds the evidence activity in debug and excludes
it from release. Final docs and whitespace checks pass. Full gate output stays
at `/tmp/namaztime-process-recovery-gates.log`. Ordinary debug MainActivity was
restored with a successful cold launch after the evidence round.

## Repeat

Install a debug APK containing this activity into an explicitly selected
emulator first; wait for installation success. The existing debug app must
already have its ordinary Room/WAL and operator preferences initialized.
Run from the repository with a new outside-repository output directory:

```bash
ANDROID_SERIAL=emulator-5554 \
RECOVERY_EVIDENCE_OUTPUT=/tmp/namaztime-process-recovery-new \
make test-android-process-recovery
```

The command rejects physical devices, an already-running evidence process,
missing ordinary app state and reused output paths. It does not install APKs,
clear data or kill the main/pilot process. Evidence files remain on the emulator
for inspection; no cleanup of existing user data is performed. On failure,
inspect the reported evidence directory/process before retrying.

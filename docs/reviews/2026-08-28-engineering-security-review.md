# NamazTime independent engineering and security review

Date: 2026-08-28

Review baseline: `edd3f34` plus its parent history. Remediation checkpoints:
`888f534`, `7a6e5b8`, `26cfbd0`, `2346fb8`, and follow-up Android toolchain
checkpoint `a1e05d8`. Post-review offline-pilot readiness checkpoints are
`27bf040` (permanent identity) and `7767f46` (signed pilot variant).

## Verdict

**CONDITIONALLY READY** for the product-owner-selected offline USB pilot.

**NOT READY** for the separate remote-managed production mode.

The locally testable backend, database, contracts and Android components have no
unblocked `CRITICAL` or `HIGH` defect in the selected offline mode. D-015 now
defines that mode as a signed APK with an approved signed bundled snapshot,
manually installed and updated by USB. It requires no domain, API, pairing,
Google Play or cloud KMS. Follow-up T031/T032 accepted permanent application ID
`ru.namaztime.tv`, generated the offline key outside Git/APK and added a
fail-closed signed pilot variant plus artifact verification. The remaining
immediate actions are two operator-controlled offline key backups and
physical-TV acceptance.

The release application still has no remote provisioning/sync composition, and
the repository cannot prove a separately deployed remote signer boundary. Those
remain release-critical only if remote-managed operation is enabled.

## Scope and method

The review independently read the repository instructions, plans/tasks, ADRs,
contracts, runbooks, schemas, migrations, Go commands/packages, Android source,
tests, build configuration and Git history. It performed separate passes over:

- publication, signing, trust, rollback and provider custody;
- pairing, authentication, RBAC, mosque isolation, fleet and PostgreSQL;
- Android Room/DataStore/WorkManager/network/files/intents/Compose lifecycle;
- resource bounds, parser differentials, concurrency, cancellation and recovery;
- CI, release artifacts, dependency provenance, vulnerabilities, secrets and
  unfinished-code indicators;
- the remediated state, without treating prior `DONE` labels as evidence.

Business and religious authority choices recorded by the product were accepted
as constraints. Their implementation and cryptographic enforcement were not.

## Finding summary

| Severity | Count | Locally closed | External/mitigated |
|---|---:|---:|---:|
| CRITICAL | 0 | 0 | 0 |
| HIGH | 4 | 2 | 2 |
| MEDIUM | 12 | 10 | 2 |
| LOW | 2 | 2 | 0 |
| CLEANUP | 2 | 2 | 0 |

`Locally closed` means the regression and available repository gates pass. It
does not promote emulator/static evidence to physical-device or production
deployment evidence.

## HIGH findings

### H-01 — publication could reach a generic signer without authenticated approval proof

- **Location:** `internal/publication/signing.go` (`PrepareSigning`,
  `FinalizeSigning`, `PublishWithSigner`), `cmd/publisher/main.go`,
  `contracts/publication-signing-request.schema.json`.
- **Problem:** the original prepare/finalize artifacts bound publisher-supplied
  approval metadata and hashes but did not carry the exact signed approval
  receipt and trust chain. A generic `Signer.Sign` implementation could not
  independently distinguish an approved snapshot from publisher-selected bytes.
- **Scenario:** an operator skipped `assemble`, forged approval metadata, called
  `prepare`, and presented the resulting raw payload to a KMS/HSM adapter that
  signed arbitrary bytes.
- **Evidence:** `CONFIRMED_STATIC` for the repository bypass; production signer
  enforcement remains `UNKNOWN` because it is outside this repository.
- **Remediation/status:** `7a6e5b8` makes the production request and isolated
  signing request carry canonical Base64 proof, re-verifies the receipt and
  direct trust transition at prepare/finalize, and adds tamper/bypass tests.
  A remote production signer must still pin approval trust independently,
  verify this proof, and enforce exclusive ledger CAS before key use. D-015's
  offline USB pilot does not call that boundary, so it is a deferred remote-mode
  prerequisite rather than a pilot blocker.

### H-02 — reachable vulnerabilities in the Go production toolchain and modules

- **Location:** baseline `go.mod`/`go.sum`: Go 1.24 family,
  `github.com/jackc/pgx/v5@v5.8.0`, `golang.org/x/text@v0.29.0`.
- **Problem:** the baseline contained reachable TLS/HTTP/parser denial-of-service
  advisories, a pgx placeholder-confusion SQL-injection advisory, and an x/text
  infinite-loop advisory.
- **Scenario:** production network/database input reached affected standard
  library or driver code while normal CI remained green because no vulnerability
  gate existed.
- **Evidence:** `CONFIRMED_RUNTIME` from baseline `govulncheck` reachability.
- **Remediation/status:** `888f534` moves the project to Go 1.26.7, pgx 5.9.2
  and x/text 0.39.0; `2346fb8` advances x/mod to 0.40.0. Current
  `govulncheck ./...` and OSV `go.mod` scans report no issues.

### H-03 — remote PostgreSQL accepted CA validation without hostname validation

- **Location:** `internal/devices/postgres_pairing.go`,
  `validatePostgresEndpointTransport`.
- **Problem:** the original policy accepted pgx `sslmode=verify-ca`, whose TLS
  configuration validates a CA chain but deliberately skips server-name binding,
  despite the documented `verify-full` requirement.
- **Scenario:** an attacker holding any certificate from a trusted database CA
  impersonated the configured remote PostgreSQL host.
- **Evidence:** `CONFIRMED_STATIC`; a regression reproduced acceptance of the
  pgx `verify-ca` configuration.
- **Remediation/status:** `888f534` rejects every remote TLS configuration with
  `InsecureSkipVerify`, including all fallbacks. Local Unix/loopback development
  endpoints remain explicitly allowed.

### H-04 — remote-managed release Android has no provisioning/sync composition

- **Location:** `apps/tv-android/src/main/AndroidManifest.xml`,
  `MainActivity.kt`, `sync/SnapshotSyncWorker.kt`, and constructor/call sites for
  `DevicePairingClient`, `SnapshotSynchronizer`, `DeviceHeartbeatClient` and
  `SnapshotSyncScheduler`.
- **Problem:** release disables the bundled pilot source, declares no custom
  `Application`, implements no `SnapshotSyncRunnerProvider`, schedules no sync,
  and exposes no production pairing composition. The defensive components exist
  only as unassembled building blocks.
- **Scenario:** the release APK starts with empty Room state and can never obtain
  an authenticated schedule; operators may mistake a successful build for a
  deployable display.
- **Evidence:** `CONFIRMED_STATIC`; release APK inspection confirms no pilot
  schedule/trust asset. A physical release deployment was not available.
- **Remediation/status:** the worker was made fail-closed in `888f534`. D-015
  selects an offline signed-APK/bundled-snapshot path for the first mosque, so
  this is not on that runtime path and is not its blocker. It remains a blocker
  before any claim of remote pairing, sync or fleet-managed production.

## MEDIUM findings

### M-01 — snapshot collection amplification was not contract-bounded

- **Location:** `contracts/prayer-snapshot.schema.json`,
  `internal/domain/validation.go`, Android `SnapshotDecoder.kt`.
- **Problem/scenario:** a signed payload below the 5 MiB byte limit could contain
  extreme counts of days, rules, overrides, sessions, campaigns or flags,
  amplifying validation and Room work on a long-running TV.
- **Evidence:** `CONFIRMED_STATIC`.
- **Remediation/status:** `888f534` adds aligned schema/Go/Kotlin limits and
  boundary/over-limit regressions.

### M-02 — Room retained every historical snapshot

- **Location:** Android `SnapshotImporter.kt` and `SnapshotDao.kt`.
- **Problem/scenario:** each publication advanced the active/previous pointers
  but never deleted the displaced rollback generation, causing unbounded private
  storage growth over years.
- **Evidence:** `CONFIRMED_STATIC`; a three-generation regression reproduced
  three retained snapshots.
- **Remediation/status:** `888f534` transactionally retains only active plus the
  immediate authenticated rollback snapshot and tests multi-generation rollback.

### M-03 — ingestor filenames could escape custody and reads were unbounded

- **Location:** `cmd/ingestor/main.go`, original manifest/source/policy/
  transcription reads.
- **Problem/scenario:** a malicious import directory could use `../`, a symlink,
  a non-regular file or an oversized file to hash/misattribute data outside the
  selected custody root or exhaust the ingestion process before parser limits.
- **Evidence:** `CONFIRMED_STATIC`.
- **Remediation/status:** `7a6e5b8` introduces bounded contained regular-file
  reads and traversal/symlink/oversize regressions.

### M-04 — duplicate JSON members enabled parser/reviewer differentials

- **Location:** provider source/effective-policy decoders, ingestor manifest,
  API request bodies, `internal/domain.DecodeSnapshot`, `internal/strictjson`.
- **Problem/scenario:** Go's normal decoder accepts the last duplicate value.
  Security-sensitive fields such as paths, pairing codes or snapshot identity
  could therefore be interpreted differently by review tools and runtime code.
- **Evidence:** `CONFIRMED_STATIC`.
- **Remediation/status:** `7a6e5b8` and `2346fb8` apply recursive duplicate-member
  rejection at every identified boundary and add nested/API/snapshot regressions.

### M-05 — Android pairing did not mirror server resource limits

- **Location:** Android `DevicePairingClient.kt` and
  `HttpUrlConnectionDeviceSyncTransport.kt`; OpenAPI `DeviceInfo.capabilities`.
- **Problem/scenario:** a caller could serialize arbitrarily many capabilities
  or an oversized body, wasting TV memory/network before the server's 16 KiB
  rejection.
- **Evidence:** `CONFIRMED_STATIC`.
- **Remediation/status:** `888f534` enforces 128 capabilities and a 16 KiB
  encoded request before I/O; OpenAPI now declares the same bound.

### M-06 — CI and build provenance omitted release-critical gates

- **Location:** baseline `.github/workflows/ci.yml`, `Makefile`, Gradle wrapper
  and dependency configuration.
- **Problem/scenario:** mutable action tags and absent race, vulnerability,
  contract, PostgreSQL restore, Android release and checksum/secret gates could
  admit a release-only regression or dependency substitution while CI stayed
  green.
- **Evidence:** `CONFIRMED_STATIC`.
- **Remediation/status:** `26cfbd0` and `2346fb8` pin Actions, Go tools and the
  PostgreSQL image, add strict Gradle SHA-256 verification, Go race/vulnerability,
  contract, database/restore, Android debug/release and complete-history secret
  gates, and disable build caches for the Kotlin advisory.

### M-07 — PostgreSQL schema v4 drift weakened provenance and resource hygiene

- **Location:** `internal/devices/migrations/000002_*`, rollout operations added
  later, and `000005_admin_request_provenance.*`.
- **Problem/scenario:** rollout-group operations were persisted as legacy
  `assign_device`; direct database writes could store more than 128 capabilities;
  expired idempotency evidence had no bounded pruning path. Audit queries were
  ambiguous and long-running installations could grow dead JSONB indefinitely.
- **Evidence:** `CONFIRMED_STATIC`; migration regressions reproduced the wrong
  operation names.
- **Remediation/status:** `26cfbd0` adds transactional schema v5 with exact
  operation backfill, a database capability-count constraint and explicit
  privilege-only deletion after seven days beyond expiry while preserving
  update/truncate/recent-delete protection. Upgrade, downgrade and restore tests
  cover v5.

### M-08 — missing Android sync runner was reported as successful work

- **Location:** Android `SnapshotSyncWorker.kt`.
- **Problem/scenario:** an incorrectly composed scheduled worker returned
  `Result.success()`, suppressing retries and diagnostics even though no sync ran.
- **Evidence:** `CONFIRMED_STATIC`; the old branch was reproduced by a worker
  test.
- **Remediation/status:** `888f534` returns bounded failure code
  `sync_runner_unavailable`; release composition remains H-04.

### M-09 — Android build/plugin classpath is materially stale

- **Location:** root `build.gradle.kts` (`AGP 8.6.1`, Kotlin 2.0.21), Gradle
  verification metadata.
- **Problem/scenario:** OSV reports vulnerable protobuf, Netty, Commons,
  Bouncy Castle, jose4j, JDOM and Kotlin plugin artifacts in the build/plugin
  graph. They are absent from `releaseRuntimeClasspath`, but malicious or
  corrupted build inputs/cache metadata can still target the build environment.
- **Evidence:** `CONFIRMED_RUNTIME` from OSV and Gradle dependency graphs;
  release-runtime absence is also `CONFIRMED_RUNTIME`.
- **Remediation/status:** dependency checksums are strict, caches are disabled,
  Robolectric/test Bouncy Castle moved to fixed versions, and no affected package
  ships in the APK. After explicit product-owner authorization, `a1e05d8`
  migrates to AGP 9.3.2, Gradle 9.5.0, built-in Kotlin and KSP; unit tests and
  the AGP migration gates pass. Closed.

### M-10 — a raw visual reference was tracked against repository custody policy

- **Location:** historical root `design.png`; `scripts/docs-check.sh`.
- **Problem/scenario:** a clone or source archive redistributed the supplied raw
  reference despite documentation that such inputs remain outside Git.
- **Evidence:** `CONFIRMED_STATIC`; the tracked object hash was independently
  recorded before removal.
- **Remediation/status:** `26cfbd0` removes it from the current tree, adds a
  specific ignore and a docs-check regression. On 2026-08-29 the product owner
  confirmed rights to retain the historical object and explicitly chose no
  history rewrite. Accepted/closed; this is not a release blocker.

### M-11 — campaign URL validators accepted HTTPS userinfo

- **Location:** `internal/domain/validation.go` and Android
  `SnapshotDecoder.kt`.
- **Problem/scenario:** `https://trusted.example@attacker.example/` passed the
  snapshot validators and created confusing signed data. The downstream campaign
  engine rejected it, limiting present phishing impact but producing inconsistent
  fail-closed behavior.
- **Evidence:** `CONFIRMED_STATIC` including the downstream mitigation.
- **Remediation/status:** `888f534` rejects userinfo in both runtimes with parity
  tests.

### M-12 — restore drill had a deterministic readiness race

- **Location:** `scripts/test-postgres-restore.sh`, `database_ready`.
- **Problem/scenario:** the probe connected to the entrypoint's temporary Unix-
  socket init server, declared readiness, then hit its normal shutdown. Recovery
  verification therefore failed intermittently and gave little diagnostic data.
- **Evidence:** `CONFIRMED_RUNTIME`; container logs reproduced the exact
  init-server startup/shutdown sequence.
- **Remediation/status:** `2346fb8` probes TCP `127.0.0.1`, which only the final
  server exposes, and prints bounded state/logs on timeout. Three consecutive
  drills plus the full PostgreSQL gate passed after the fix.

## LOW findings

### L-01 — strict JSON trailing-value errors could reflect input content

- **Location:** `internal/strictjson/strictjson.go`.
- **Problem/scenario:** an error formatted the unexpected token value. If a
  caller logged an input containing a credential-like trailing value, the value
  could enter logs.
- **Evidence:** `CONFIRMED_STATIC`; no committed production secret exposure was
  found.
- **Remediation/status:** `2346fb8` emits a stable non-reflective error and adds a
  regression asserting that a synthetic secret is absent.

### L-02 — OpenAPI did not match runtime cache/error behavior

- **Location:** `contracts/openapi.yaml` pair/heartbeat/admin responses and the
  stale manifest `403` response.
- **Problem/scenario:** clients and gateways generated from the contract could
  miss `Cache-Control: no-store` on credential/support responses or expect an
  unreachable authorization status.
- **Evidence:** `CONFIRMED_STATIC`; runtime handlers already emitted `no-store`.
- **Remediation/status:** `2346fb8` advances OpenAPI to 0.6.1, declares the
  reusable header on sensitive success responses and removes stale `403`.

## CLEANUP findings

### C-01 — Staticcheck was absent and the baseline was not clean

- **Location:** baseline `internal/devices/http.go`,
  `internal/publication/mosque_policy.go`, Makefile/CI.
- **Problem/scenario:** three low-risk diagnostics reduced signal for future
  correctness issues.
- **Evidence:** `CONFIRMED_RUNTIME`.
- **Remediation/status:** `7a6e5b8` fixes the diagnostics; `26cfbd0` pins and runs
  Staticcheck in repository gates.

### C-02 — repository documentation claimed empty code and an existing admin UI

- **Location:** `ARCHITECTURE.md` repository map and `README.md` architecture
  summary.
- **Problem/scenario:** a handoff team could plan against a nonexistent
  `web/admin/` implementation and treat implemented code as a docs-only scaffold.
- **Evidence:** `CONFIRMED_STATIC`.
- **Remediation/status:** current documentation states that the Go/Android code
  exists, the admin API exists, and browser UI is blocked by D-008.

## Important clean results

- `CONFIRMED_STATIC` — no SQL interpolation/injection path or cross-mosque RBAC
  bypass was found; service methods re-authorize scope inside PostgreSQL
  transactions and queries bind mosque identifiers.
- `CONFIRMED_STATIC` — pairing codes are short-lived and single-use; device/admin
  bearer tokens are hashed, scoped and compared without public existence leaks.
- `CONFIRMED_STATIC` — snapshot authenticity, environment/key binding, minimum
  trust revision, revocation and manifest monotonicity fail closed; the TV never
  calls network code from composables or display reducers.
- `CONFIRMED_STATIC` — image pickers perform bounded one-shot reads, validate
  decoded type/dimensions, normalize into fixed app-private names and do not keep
  external URI permissions. Donation/QR values are bounded and backup-excluded.
- `CONFIRMED_RUNTIME` — the merged release manifest has only the launcher as an
  application-owned exported component. Exported library services/receivers are
  protected by platform signature permissions; cleartext and backup are disabled.
- `CONFIRMED_RUNTIME` — the release APK contains no bundled pilot schedule,
  private key, trust asset or raw visual reference.
- `CONFIRMED_STATIC` — cancellation is rethrown/propagated across reviewed
  coroutine/network paths; display wake state is restored on Compose disposal;
  schedule time uses the mosque's IANA timezone.
- `CONFIRMED_STATIC` — the TODO/FIXME/HACK sweep found no unfinished production
  branch beyond explicitly documented release integration/product blockers.

## Verification evidence

All commands below completed successfully on the remediated tree unless marked
as external or advisory:

```text
make docs-check
make format-check
make lint-go
make test-contracts
make test-go
make test-go-race
make security-go
make secret-scan
make test-android-all
make test-postgres
actionlint .github/workflows/ci.yml
osv-scanner scan source --lockfile=go.mod
git diff --check
```

Android verification includes debug unit/Compose tests, debug and release lint,
debug and release assemble, strict Gradle dependency checksums and disabled build
caches. The final uncached rerun executed all 114 requested Gradle tasks and 295
unit/Compose tests. PostgreSQL verification includes migrations, integration tests,
least-privilege checks, corrupted-archive rejection and clean-database restore.
The final restore archive was 39,060 bytes with SHA-256
`d7684240c55f39e4678f9862cfc89e0e7f642b00bcc3a764cfb265c9e065e60b`.
Gitleaks scanned all 51 commits with no non-allowlisted leak; `govulncheck` and
the focused OSV Go scan both reported no vulnerabilities.

Follow-up verification on 2026-08-29 migrated the Android build to AGP 9.3.2,
Gradle 9.5.0, built-in Kotlin and KSP. `./gradlew help`,
`./gradlew build --dry-run` and `make test-android-all` pass with strict
dependency verification; the latter executed 110 tasks including unit/Compose
tests, debug/release lint and debug/release assemble. The current debug APK was
installed in-place and D-pad-smoked on the API 36 TV emulator; hashes and
observations are recorded in the emulator evidence log.

The broad OSV scan of `gradle/verification-metadata.xml` at the original review
checkpoint remained non-zero because
that file deliberately inventories build/plugin/test artifacts. Manual Gradle
graphs prove the reported packages are absent from `releaseRuntimeClasspath`;
M-09 records the remaining build-tool risk rather than suppressing scanner output.

## Offline-pilot actions and deferred remote risks

1. `PROPOSAL` — before the first retained mosque install, choose D-005 and create
   one backed-up offline APK signing key. This is the only unresolved local
   packaging decision; it does not require a domain or KMS.
2. `UNKNOWN` — no physical-TV evidence exists for OEM boot/relaunch, picker,
   real-phone QR distance, power loss, wrong RTC/timezone, 720p/1080p/4K memory
   soak, kiosk/device-owner mode or seven-day offline operation.
3. `CONFIRMED_STATIC` — remote Android composition/provisioning is absent (H-04),
   and remote signer/KMS enforcement is not deployed (H-01). They are deferred
   remote-mode prerequisites, not D-015 pilot blockers.
4. `UNKNOWN` — source operational contacts/SLA and an approved live signed QR
   destination remain optional deployment inputs. Device-local QR remains usable.
5. `CONFIRMED_STATIC` — `design.png` remains in old Git objects. The product
   owner confirmed retention rights and accepted the history as-is on 2026-08-29.

## Second independent pass

The post-remediation pass re-read signer/publication admission, PostgreSQL TLS and
RBAC transactions, Android production construction/call sites, merged manifest,
release APK contents, release dependency graph, file/URI bounds, Compose resource
lifecycle, TODO markers and documentation claims. It found no new `CRITICAL` or
unblocked `HIGH` defect. It did find and close M-12 and C-02, and it separated
M-09's build-only risk from the clean release runtime graph.

# Production publication and signing runbook

Last locally verified: 2026-08-20. This runbook implements accepted D-013 and
[ADR 0011](docs/adr/0011-production-signing-uses-isolated-ed25519-keys.md).

Evidence labels:

- `CONFIRMED_RUNTIME` — the repository tests exercise synthetic/test keys only.
- `PROPOSAL` — the commands and lifecycle below are the accepted deployment
  procedure until a real protected signer and pilot rollout execute them.
- `UNKNOWN` — signer vendor, production key IDs, named security operators and
  recovery quorum are deployment inputs and are not invented here. KMS is the
  accepted custody class, but its concrete provider is not selected. The pilot
  approval root is now the separate Ed25519 key identified by
  `ulyanovsk-approver-akhmedov-2026-01`; only its public bundle is committed.

## Roles and protected inputs

- Religious approver: reviews candidate/diff/warnings and issues the exact
  approval record. This person cannot operate the signer for the same event.
- Signer operator: authenticates to the protected signer and signs only the
  canonical request. This person cannot alter the approval.
- Release operator: finalizes, verifies, stores immutable artifacts and runs the
  canary. Prefer a third person; at minimum this role must not possess key material.
- Security owner: owns KMS/HSM policy, public trust-bundle releases, rotation,
  revocation and recovery quorum.

The KMS/HSM credential and key never enter this repository, `cmd/publisher`,
the API process, an Android package or ordinary CI logs. Signing requests,
responses, public keys, snapshots and receipts are not secrets, but remain
integrity-sensitive release evidence.

## Normal publication

Preconditions: source permission is granted; the effective inspection has no
blocking error; every warning is acknowledged; a named religious approval
binds the exact hashes and mosque policy; D-009 is represented by that policy;
the selected snapshot key is `active` in the production trust bundle.

1. Recreate inspection from immutable sources:

   ```bash
   go run ./cmd/ingestor inspect-effective \
     --baseline-dir fixtures/pilot/ulyanovsk-2026 \
     --override-dir fixtures/pilot/ulyanovsk-2026-08 \
     > /secure/release/inspection.json
   ```

2. Verify the signed religious approval before any KMS request. Initial key
   generation is a one-time custody operation outside Git:

   ```bash
   go run ./cmd/approver keygen \
     -identity approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly \
     -key-id ulyanovsk-approver-akhmedov-2026-01 \
     -generated-at 2026-08-20T14:30:00Z \
     -private-key-out /secure/approver/ulyanovsk-approver.private.pem \
     -trust-bundle-out /secure/release/approver-trust-bundle.json

   go run ./cmd/approver sign \
     -private-key /secure/approver/ulyanovsk-approver.private.pem \
     -key-id ulyanovsk-approver-akhmedov-2026-01 \
     -trust-bundle /secure/release/approver-trust-bundle.json \
     -decision /secure/release/approval-decision.json \
     -inspection /secure/release/inspection.json \
     -prayer-policy /secure/release/mosque-prayer-policy.json \
     -out /secure/release/approval-receipt.json

   go run ./cmd/approver verify \
     -receipt /secure/release/approval-receipt.json \
     -trust-bundle /secure/release/approver-trust-bundle.json \
     -inspection /secure/release/inspection.json \
     -prayer-policy /secure/release/mosque-prayer-policy.json
   ```

   Never regenerate the key to reproduce a receipt. Rotation uses a new key ID
   and a monotonic approval-trust revision. For revision 2 and later, pass the
   exact directly preceding accepted bundle to `sign` and `verify` as
   `-previous-trust-bundle`; production `publisher assemble` requires the same
   predecessor as `-previous-approval-trust-bundle`. Revision 1 rejects an
   unexpected predecessor. Back up the current private approval
   key through a protected operator-controlled channel; mode `0600` alone is
   not a disaster-recovery plan.

3. Assemble a publication request. Outputs are exclusive-create and never
   overwrite earlier evidence:

   ```bash
     go run ./cmd/publisher assemble \
     -inspection /secure/release/inspection.json \
     -approval-receipt /secure/release/approval-receipt.json \
     -approval-trust-bundle /secure/release/approver-trust-bundle.json \
     -prayer-policy /secure/release/mosque-prayer-policy.json \
     -snapshot-id ulyanovsk-second-cathedral-2026-v1 \
     -generated-at 2026-08-20T12:00:00Z \
     -signing-key-id prod-schedule-2026-01 \
     -out /secure/release/publication-request.json
   ```

   For approval trust revision 2 and later, add
   `-previous-approval-trust-bundle` with the exact preceding bundle.

4. Prepare exact signer input:

   ```bash
   go run ./cmd/publisher prepare \
     -request /secure/release/publication-request.json \
     -trust-bundle /secure/release/production-trust-bundle.json \
     -test-trust-bundle /secure/release/test-trust-bundle.json \
     -staging-trust-bundle /secure/release/staging-trust-bundle.json \
     -out /secure/release/signing-request.json
   ```

5. The signer operator checks request ID, key ID, canonical SHA-256, snapshot
   identity, mosque, coverage, provenance hashes and approval identity. The
   protected service signs two domain-separated messages with the same active
   Ed25519 key: the decoded `canonical_payload_base64` bytes and the publication
   attestation returned by `BuildAttestationPayload`. The attestation binds the
   exact signing-request SHA-256, approver receipt/trust/policy hashes,
   approver/signer principals, times and exactly
   one audit predecessor or the one-time ledger genesis reason. It returns
   strict `publication-signing-response.schema.json` JSON. Signer policy must
   permit genesis only while initializing a new environment ledger. Never
   export its key.

   Before signing, the protected KMS wrapper must independently verify the
   Base64-decoded `approval_receipt_base64` against the separately pinned
   approval trust root/revision/hash, validate the embedded current and (when
   present) direct-predecessor approval bundles, and match
   its immutable principal, candidate/diff, warning and prayer-policy hashes to
   the request. `cmd/publisher assemble` performs the same check on the release
   host, and `prepare`/`finalize` repeat it, but neither is a substitute for
   signer-side authorization. A generic KMS raw-sign API exposed to the release
   host does not satisfy this control.

6. Finalize. For every publication after the first, pass the complete prior
   receipt so its signer attestation is verified before extending the chain.
   The signer response must contain the identical `published_at` and
   `previous_receipt_sha256`. For the first publication only, replace
   `-previous-receipt` with `-chain-genesis-reason '<approved initialization>'`
   and return the identical reason in the signer response:

   ```bash
   go run ./cmd/publisher finalize \
     -request /secure/release/publication-request.json \
     -signing-request /secure/release/signing-request.json \
     -signer-response /secure/release/signing-response.json \
     -trust-bundle /secure/release/production-trust-bundle.json \
     -test-trust-bundle /secure/release/test-trust-bundle.json \
     -staging-trust-bundle /secure/release/staging-trust-bundle.json \
     -published-at 2026-08-20T12:05:00Z \
     -previous-receipt /secure/release/prior-publication-receipt.json \
     -ledger-head /secure/release/publication-ledger-head.json \
     -out-receipt /secure/release/publication-receipt.json \
     -out-snapshot /secure/release/snapshot.json
   ```

7. Verify independently before registry admission:

   ```bash
   go run ./cmd/publisher verify \
     -snapshot /secure/release/snapshot.json \
     -receipt /secure/release/publication-receipt.json \
     -previous-receipt /secure/release/prior-publication-receipt.json \
     -trust-bundle /secure/release/production-trust-bundle.json \
     -test-trust-bundle /secure/release/test-trust-bundle.json \
     -staging-trust-bundle /secure/release/staging-trust-bundle.json
   ```

Expected result is exit code zero and no output. Store raw sources, inspection,
approval, request, signer response, snapshot, receipt and deployed trust-bundle
hash as one immutable release evidence set. Every production registry entry
must set `receipt_file`; non-genesis entries also set `previous_receipt_file`.
Registry startup authenticates the receipt and its direct chain link against
the exact public trust-bundle revision and SHA-256. Canary assignment and rollback use the existing
monotonic manifest workflow.

`publication-ledger-head.json` is mutable release-control state, not evidence.
Finalize acquires an exclusive sibling lock directory, compares the supplied
predecessor with the durable head, writes immutable artifacts, then atomically
advances and directory-syncs the head. A repeated genesis or two children of
one predecessor therefore fail locally. Preserve/backup this file with the
immutable receipt archive; a stale `.lock` after a crash requires named
operator diagnosis, never blind deletion. The protected signer must enforce
the same one-genesis/compare-and-swap rule in its own authorization state so a
bypassed release host cannot create a signed fork.

## Planned rotation

1. Generate a disjoint Ed25519 key inside protected production custody; record
   its owner, recovery policy and key ID without exporting private material.
2. Increment the bundle revision by exactly one and add only its public key as
   `scheduled`; retain the directly preceding bundle. Go/API and Android reject
   key rebinding, public-material reuse, live-key removal, revision gaps or
   rollback and revoked-key resurrection.

   ```bash
   go run ./cmd/publisher verify-trust \
     -current /secure/release/production-trust-bundle-v2.json \
     -previous /secure/release/production-trust-bundle-v1.json \
     -test-bundle /secure/release/test-trust-bundle.json \
     -staging-bundle /secure/release/staging-trust-bundle.json
   ```
3. Authentically distribute a bundle containing old `active` plus new
   `scheduled`. API configuration supplies `previous_trust_bundle_file` for
   transition validation; the Android production verifier receives the same
   directly preceding bytes. Raise the minimum revision and confirm hashes.
4. Distribute the next bundle with both keys `active`; do not switch the signer
   until the canary fleet confirms that bundle.
5. Publish one canary snapshot with the new key, verify receipt and execute a
   manifest rollback to a still-verifiable old snapshot.
6. Switch normal signing. Set the old key's `not_after` to its last snapshot
   generation time and mark it `retired`.
7. Retain the public retired key through the maximum device last-known-good and
   rollback window. Then remove it in a reviewed trust-bundle release.

Trigger this at least annually and whenever custody, personnel or algorithms
change. Roll back a rotation by selecting the prior still-active signer and
deploying a newer reviewed bundle; never roll back past a revocation.

## Emergency revocation

Trigger on suspected key export, unauthorized signature, signer-policy bypass
or unexplained publication receipt.

1. Freeze publication and rollout writes; preserve requests, responses,
   receipts, signer audit and deployed bundle hashes.
2. Increment the bundle revision and mark the key `revoked` with UTC time and
   bounded reason. Security owner and
   release operator review the new bundle out of band.
3. Authentically deploy the revocation to API and TV trust configuration.
   Registry startup, new activation and Android cold-start selection of an
   already persisted production snapshot reject that key. If no non-revoked
   last-known-good exists, the display fails closed until replacement.
4. Generate/activate a replacement key through the staged procedure. Rebuild a
   known-good snapshot from retained raw provenance and approval; re-approve if
   the approval policy or content changed, sign, canary and publish it.
5. Do not use an artifact signed only by the revoked key as rollback. Use a
   separately signed known-good replacement with a newer manifest version.
6. Rotate affected signer credentials, investigate scope, record incident and
   update this runbook.

## Failure and rollback triggers

Stop and quarantine the release if any hash/binding differs, signer response
does not verify, key is not active, receipt chain breaks, API rejects registry
startup, Android rejects canary activation, displayed date/time differs from
the approved effective schedule, or last-known-good cannot be restored.

The publisher file-syncs the receipt and its parent directory before it creates
and file-syncs the snapshot and parent directory, so a process failure
cannot leave an apparently releasable snapshot without audit evidence. If only
one output exists, quarantine it and restart with new output paths after
diagnosis; do not overwrite or edit evidence in place.

## Quarterly validation checklist

1. Run repository checks and a staging prepare/sign/finalize/verify cycle.
2. Exercise active→retired verification, scheduled rejection and revoked rejection.
3. Execute canary assignment and monotonic rollback with disjoint staging keys.
4. Confirm API and Android consume the identical trust-bundle hash.
5. Confirm named owners, KMS/HSM policy, recovery quorum and evidence retention.
6. Record date, participants, artifact hashes, outcome and follow-up owner.

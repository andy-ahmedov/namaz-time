# ADR 0011: Production signing uses isolated Ed25519 keys and versioned public trust bundles

- Status: Accepted
- Date: 2026-08-20
- Decision: D-013

## Context

The snapshot signature is the final authority boundary between an approved
schedule and a TV activation. A private key in Git, an APK, the API process or
a general CI worker would let compromise of any one of those systems mint
apparently authentic prayer times. A bare map of public keys also cannot state
whether a key is being staged, may sign now, is retained only for rollback, or
was revoked.

## Decision

Production uses Ed25519. The private key is generated and retained only by a
KMS, HSM or isolated signer; the repository publisher accepts no production
private-key bytes. Schedule approval and signing are separate roles. The
signer signs the exact canonical snapshot bytes and a domain-separated
publication attestation produced from an approval bound to candidate, raw
artifact, transcription, normalized output, parser and diff hashes. The
attestation also binds stable approver/signer principals, signing/publication
times, the exact trust-bundle hash/revision and the audit predecessor or the
one explicitly authorized ledger genesis.
The protected signer must authenticate the approval principal and approval
receipt independently; string inequality in publisher tooling is a structural
guard, not identity proof. The pilot uses a separate Ed25519 approval root with
stable principal
`approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly`. The receipt binds the
candidate/diff/warnings and canonical mosque prayer policy, and is independently
verified by both the release host and KMS wrapper. Approval trust has its own
monotonic lifecycle and cannot authorize snapshot signatures.

Public trust is a strict bundle with a monotonically pinned positive revision,
an environment and, for each
key, a unique key ID, Ed25519 public key, status and validity window. Statuses
mean:

- `scheduled`: distribution/preflight only; it cannot sign or verify a snapshot;
- `active`: it may sign and verify snapshots generated inside its window;
- `retired`: it cannot sign, but may verify historical snapshots inside its
  closed window so an authorized rollback remains possible;
- `revoked`: it never verifies, including historical snapshots.

Production snapshots require a production bundle in the Go registry and
Android verifier. The legacy plain-key map remains test-only and rejects
production data. Trust is evaluated against signed `generated_at`, not the
device wall clock; revocation status comes from the authenticated deployed
bundle. Trust-bundle delivery is part of the signed application/deployment
configuration. An unsigned network response may not replace it. API and TV
production configuration pin a minimum accepted revision so an accidental
bundle rollback cannot silently erase a revocation; the receipt also binds the
exact bundle revision and SHA-256 used for publication.
Every non-genesis bundle is validated against its directly preceding accepted
revision. Existing key material and validity origins are immutable, live keys
cannot disappear, revision gaps are rejected, and a retired or revoked key
cannot return to active. Environment staging rejects key-ID or public-material
reuse across test, staging and production.

Rotation is staged: generate the new key in protected custody, distribute and
validate it as `scheduled`, distribute a bundle where it is `active` alongside
the old active key, confirm that bundle on the rollout cohort, then change the
signer selection. After the overlap, close the old key's window and mark it
`retired`; remove it only after the maximum retained snapshot/rollback period.
Planned rotation is at least annual. Suspected compromise triggers immediate
revocation, replacement signing and canary rollout.

Publication is two-person and two-step. `publisher prepare` emits non-secret
canonical bytes, complete binding metadata, and the exact Base64-encoded signed
approval receipt plus current/direct-predecessor public approval-trust bundles
for an isolated signer. Prepare and finalize reverify that proof against the
candidate, diff and prayer-policy bindings on every production invocation. The
signer signs both the snapshot and the domain-separated attestation.
`publisher finalize` reconstructs the request from the original
candidate/diff/approval, verifies both signatures
under the active public policy, and creates immutable snapshot bytes plus a
hash-chained audit receipt. Receipts bind source/candidate/diff/approval,
approver, signer identity, signing/publication time, snapshot raw/canonical
hashes and key ID. Rollback snapshots use the same production signing path;
assignment of a prior artifact still increments the manifest version.
The API registry requires an authenticated receipt for every production
artifact; a receipt self-hash alone is not signer evidence.

Test, staging and production use disjoint keys and trust bundles. Production
private material, recovery shares and KMS credentials are never committed,
logged or embedded in application/runtime configuration.

## Consequences

- The repository can implement and test the full public protocol without a
  production secret or vendor choice.
- A real publication still needs a provisioned protected snapshot signer, a
  distinct named security/signer operator, authenticated distribution of the
  production snapshot trust bundle and a canary/rollback drill. The named pilot
  approver and approval-signature root are complete locally.
- The signer wrapper must pin the accepted approval-trust root/revision/hash and
  reject arbitrary raw signing requests. Carrying public proof in the request
  makes independent verification possible; it does not make a publisher-
  supplied trust root authoritative.
- Revoking a compromised key intentionally makes artifacts signed only by that
  key unavailable for new serving/activation; operators must publish a known-
  good replacement under a non-compromised key.
- Public bundle rollback is security-sensitive. Deployment controls must not
  allow an older bundle to erase a revocation.

## Rejected alternatives

- a private key in Git, CI variables exposed to the publisher, API config or APK;
- one shared key across test, staging and production;
- automatic provider approval/signing;
- accepting unknown keys or using TLS/download hashes as source authenticity;
- deleting an old key immediately and thereby destroying authorized rollback;
- remotely replacing the trust bundle without a separately authenticated root.

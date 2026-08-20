# ADR 0003: Canonical snapshot signatures

- Status: Accepted
- Date: 2026-08-20

## Context

TLS, a downloaded-file checksum and encryption do not authenticate a prayer
schedule's publisher. Go publication and Android verification also need to
derive identical bytes even when JSON whitespace or object-member order
changes. Signing arbitrary serializer output would make otherwise identical
snapshots unverifiable across implementations.

## Decision

Phase 1 signs an Ed25519 signature over this canonical payload:

1. decode one strictly valid UTF-8 JSON object and reject malformed/trailing
   data (replacement of invalid bytes is forbidden);
2. remove the root `integrity` member in full;
3. sort every object's ASCII contract keys in ascending lexical order;
4. preserve array order;
5. emit strings as UTF-8, escaping only quote, backslash and JSON control
   characters; control `\u` escapes use lowercase hexadecimal;
6. preserve the deterministic JSON number spelling emitted by the publisher;
7. emit no insignificant whitespace.

The envelope stores SHA-256 of those canonical bytes, a signing-key ID and the
base64 Ed25519 signature. Verification requires a configured raw 32-byte public
key for that exact key ID, recomputes the canonical hash, verifies the
signature, then runs strict snapshot decoding/domain validation. Only that
authenticated result, or the explicitly synthetic bundled bootstrap fixture,
can reach the Room importer.

Approval binds to candidate ID, raw-artifact SHA-256, transcription SHA-256,
normalized-candidate SHA-256, parser version and deterministic diff SHA-256.
Every parser-warning code must be explicitly acknowledged. The provider can
only create `needs_review` or `validation_failed`; it cannot approve or publish.

The committed Phase 1 public verification key is a test-only trust fixture.
Its private key was generated ephemerally and discarded. It is not included in
the APK trust configuration and is not a production trust anchor.

## Consequences

- Go and Android can verify the same signed fixture and tolerate only
  whitespace/member-order reformatting.
- Both runtimes reject explicit nulls, malformed UTF-8 and invalid typed
  optional children; a valid signature never relaxes schema/domain checks.
- Payload, provenance, iqamah overrides and optional configuration are covered
  by one signature; the integrity envelope cannot authenticate itself.
- Alternate number spellings are rejected even when numerically equivalent.
- Production key custody, distribution, rotation and revocation are fixed by
  [ADR 0011](0011-production-signing-uses-isolated-ed25519-keys.md).
- T009 must preserve downloaded raw bytes for diagnostics, but activation
  still depends on this verifier and the existing atomic Room transaction.
- T009's Go serving registry also verifies this signature and strict snapshot
  contract before exposing bytes. Android independently verifies again and
binds manifest raw hash/length, snapshot ID and signing-key ID.
Both serving and activation also bind the signed mosque ID and IANA timezone to
the paired/provisioned mosque; a valid signature for another mosque is rejected.

## Rejected alternatives

- TLS or SHA-256 without a publication signature;
- signing indented JSON serializer output;
- committing a test private key or embedding a production private key;
- allowing providers or the TV client to manufacture approval.

# Phase 1 signature verification fixture

This directory contains one explicitly synthetic snapshot signed by the T008
publication prototype and its raw 32-byte Ed25519 public key. The corresponding
private key was generated only in memory for fixture creation and was not
retained or committed.

- Snapshot SHA-256:
  `5b55f00294077efcae22e4ff48fc44aca352f25a26abc68a819caa3593fa674d`.
- Signing key ID: `phase1-fixture-key-2026-08`.
- Data classification: `synthetic`; the times are test data.
- Purpose: cross-platform Go/Android valid, tampered and unknown-key tests.

This is not a production trust anchor and must never be promoted or reused as
one. D-013 remains open for the real signer/KMS and key-rotation policy.

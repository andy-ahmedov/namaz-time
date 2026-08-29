# Pilot-local Android bootstrap evidence

Evidence label: `PROPOSAL` for the build mechanism; local JVM/Robolectric
execution is not physical-TV or emulator `CONFIRMED_RUNTIME` evidence.

This directory records the public audit side of the T022 build-only Android
pilot fixture. The snapshot itself is packaged only from
`apps/tv-android/src/pilot/assets/pilot-local-ulyanovsk-2026-snapshot.json`.
Debug tests consume the same directory explicitly; the ordinary release source
set does not package these assets.
It was assembled from the exact effective inspection, signed approval receipt
and mosque prayer policy already retained in the parent fixture directory.

The original signing key `pilot-local-schedule-2026-01` was generated for the
v1 local fixture and discarded. T033 rotates through public trust revisions 2
and 3 to `pilot-local-schedule-2026-02`; its private key is retained only in
the protected local operator store outside Git/APK for recoverable signed
pilot updates. The three environment
bundles contain public keys only and prove test/staging/production material
separation to the Android verifier. Their environment names are required by
the shared trust contract; they do not turn this local artifact into a KMS
publication or authorize API registry admission.

Fingerprints:

- snapshot raw SHA-256:
  `78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`;
- publication receipt raw SHA-256:
  `ddcba5fe5cd99d12553b189e4dc689210432052f8718c7df65482451ae5291be`;
- production-named local trust bundle raw SHA-256:
  `2fc9b7a34cbba4bf57ba5242aec6b782d41877ff6863006e83a3d40bc34eb085`;
- staging local trust bundle raw SHA-256:
  `db4d936d6894d6ff60bfa422c12a886bfc0f553b47a7e1201fa9e2560b25d0bd`;
- test local trust bundle raw SHA-256:
  `82c7e7e943f5796ab689265a2d24862fc1f869f5ea574f7a940d2adaf74c1f77`.

Do not treat these public bundles as private credentials or as authorization
for the remote API registry. D-015 allows the offline mosque pilot to package
an approved successor and public trust transition in a separately signed APK;
the successor private key remains offline and outside Git/APK. ADR 0011 KMS,
remote trust deployment, publication ledger and T009 delivery apply when the
project enables the separate remote-managed mode.

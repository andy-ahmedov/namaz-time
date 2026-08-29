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

The signing key `pilot-local-schedule-2026-01` was generated for this one local
fixture, used through the same protected-signer publication interface and then
discarded. No private key is retained in Git or on disk. The three committed
bundles contain public keys only and prove test/staging/production material
separation to the Android verifier. Their environment names are required by
the shared trust contract; they do not turn this local artifact into a KMS
publication or authorize API registry admission.

Fingerprints:

- snapshot raw SHA-256:
  `92ad801095cdb4e300c56b3e9dc48993179a891a228754a83c5421010edfe3e4`;
- publication receipt raw SHA-256:
  `6950c19403e0602db77714b9e48ec41ff6d2e35a378d2040e8978ee961125a7c`;
- production-named local trust bundle raw SHA-256:
  `2c12da79bc405e129284522d526965b408230de672ee829f6093a6b4b27709e2`;
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

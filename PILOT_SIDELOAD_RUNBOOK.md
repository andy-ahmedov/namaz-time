# Offline pilot installation and updates

This runbook is for the first mosque pilot selected in D-001. It deliberately
uses a signed APK copied by USB and an approved schedule bundled in that APK.
It needs no domain, public API, Google Play release, remote administration or
cloud KMS.

## One-time choices before the first mosque installation

1. Use the accepted application ID `ru.namaztime.tv`. Do not change it after
   installing the first retained mosque copy: a different ID is a different
   Android app.
2. Generate one Android APK signing keystore outside this repository. Keep its
   password outside shell history and Git. Store two offline backups in
   different places. Every later APK must use this same key.
3. Configure a dedicated signed `pilot` build. Do not deploy the
   ordinary debug key as the long-term mosque identity.
4. Retain the generated versioned handover manifest and checksum. They record
   the APK certificate and file SHA-256, application ID, version/code, exact
   clean Git commit, build variant/state and schedule snapshot identity/hash.
   Add the target and installation date to the operator handover record.

The APK key is not the prayer-schedule key. Android uses the APK key to decide
whether a new file is allowed to update the installed app. The app uses an
Ed25519 schedule key to prove that the bundled schedule is the reviewed one.
For this offline pilot both keys may be files kept offline; neither requires a
cloud KMS. Neither private key may be committed or copied into the APK.

The current build workstation stores the generated keystore and its protected
Gradle properties outside the repository under `/home/andy/.config/namaztime/`.
Its public certificate SHA-256 is
`da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9`.
This is `CONFIRMED_RUNTIME` local workstation evidence, not evidence that two
offline backups exist. Before the first mosque installation, copy the keystore
and properties file together to two access-controlled offline locations.

Build and verify the installable APK with:

```bash
NAMAZTIME_PILOT_SIGNING_PROPERTIES=/home/andy/.config/namaztime/pilot-signing.properties \
make build-android-pilot
```

The verified handover output is an outside-repository directory named
`../namaztime-artifacts/android/namaztime-<version>-code<code>-<commit>/` by
default. It contains the versioned APK, a SHA-256 file suitable for
`sha256sum -c`, and a JSON identity manifest. An operator may set
`ANDROID_PILOT_ARTIFACT_DIR` to an explicit approved outside-repository or
handover-media path. The intermediate Gradle APK remains under
`apps/tv-android/build/outputs/apk/pilot/`. Packaging fails closed when the
external signing file is absent, the Git tree is dirty, or embedded identity,
certificate, snapshot or trust assets differ. Debug uses the separate
`ru.namaztime.tv.debug` application ID and debug key, so it cannot accidentally
occupy the retained mosque application's identity.

The current handover version is `0.5.0-pilot.1` with `versionCode=4`. Version
values come only from `apps/tv-android/version.properties`; do not hand-edit an
APK filename or manifest to simulate another build.

## Device-local image selection

Appearance and Donation first use a resolvable Android document picker, then
the system Photo Picker. On a TV with neither, NamazTime opens a D-pad image
browser backed only by MediaStore. The last path may request read access after
the operator presses the image action: legacy image/media read through API 32,
or `READ_MEDIA_IMAGES` on API 33+. Denial leaves the current image unchanged;
Back/cancel is silent. Never grant or expect write/all-files access. A selected
JPEG/PNG/WebP is validated and copied into app-private storage, so removing the
USB drive or revoking the source URI does not remove the displayed local copy.

## First installation

1. Commit the intended source and documentation, and confirm the working tree
   is clean. Run `make build-android-pilot`; it verifies the application ID,
   Android version/build identity, signature, certificate fingerprint and all
   four authenticated schedule/trust assets.
2. Run `sha256sum -c` against the generated `.sha256` file. Copy the exact APK,
   `.sha256` and `.manifest.json` files to the USB handover media.
3. For the first install, confirm that `ru.namaztime.tv` is not already present.
   For an intentional update, keep the existing app and use the update flow
   below.
4. Open NamazTime, verify the mosque name, timezone, snapshot ID, coverage end,
   approval state and several known dates against the approved source.
5. Reboot the TV/box and confirm that the local schedule still renders without
   network access. Record the physical device model and Android version.

## Updating the app or schedule

1. Preserve the application ID and APK signing key.
2. Increase `versionCode` for every delivered APK and update the base semantic
   version or pilot sequence according to ADR 0017. Android upgrade ordering is
   controlled only by the integer code.
3. For schedule changes, produce a new approved, signed immutable snapshot and
   bundle its public trust data. Use a new snapshot ID beginning with
   `ulyanovsk-second-cathedral-pilot-local-` and a later signed `generated_at`.
4. Build and verify the new signed pilot APK.
5. Install it as an update. Do **not** uninstall the old app and do not clear its
   data: uninstalling destroys Room/DataStore state and removes the opportunity
   to verify the in-place upgrade.
6. Confirm that Android reports an update rather than a separate app, local
   settings remain, and the expected snapshot ID is active. In Diagnostics,
   compare version/code and variant/commit/state with the handover manifest.

The client replaces only a known predecessor or this pilot snapshot family,
requires the same timezone (and, after the initial synthetic migration, the
same mosque), and rejects a snapshot whose signed generation time is not newer.
Any rejection leaves the last-known-good Room snapshot active.

## What is deferred

Pairing, a public API/domain, remote schedule delivery, remote app updates,
Google Play and production KMS custody are a different deployment mode. They
are not prerequisites for this offline pilot. Revisit them only when remote
operation or wider distribution is actually requested.

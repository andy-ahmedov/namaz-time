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
4. Record the APK certificate SHA-256, APK SHA-256, application ID,
   `versionCode`, schedule snapshot ID and installation date in the handover
   record.

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

The verified output is
`apps/tv-android/build/outputs/apk/pilot/tv-android-pilot.apk`. The build fails
closed when the external signing file is absent. Debug uses the separate
`ru.namaztime.tv.debug` application ID and debug key, so it cannot accidentally
occupy the retained mosque application's identity.

## First installation

1. Run `make build-android-pilot`; it verifies the application ID, Android
   signature, certificate fingerprint and all four authenticated schedule/trust
   assets, then prints the APK SHA-256.
2. Copy that exact APK to the USB drive.
3. For the first install, confirm that `ru.namaztime.tv` is not already present.
   For an intentional update, keep the existing app and use the update flow
   below.
4. Open NamazTime, verify the mosque name, timezone, snapshot ID, coverage end,
   approval state and several known dates against the approved source.
5. Reboot the TV/box and confirm that the local schedule still renders without
   network access. Record the physical device model and Android version.

## Updating the app or schedule

1. Preserve the application ID and APK signing key.
2. Increase `versionCode` for every delivered APK.
3. For schedule changes, produce a new approved, signed immutable snapshot and
   bundle its public trust data. Use a new snapshot ID beginning with
   `ulyanovsk-second-cathedral-pilot-local-` and a later signed `generated_at`.
4. Build and verify the new signed pilot APK.
5. Install it as an update. Do **not** uninstall the old app and do not clear its
   data: uninstalling destroys Room/DataStore state and removes the opportunity
   to verify the in-place upgrade.
6. Confirm that Android reports an update rather than a separate app, local
   settings remain, and the expected snapshot ID is active.

The client replaces only a known predecessor or this pilot snapshot family,
requires the same timezone (and, after the initial synthetic migration, the
same mosque), and rejects a snapshot whose signed generation time is not newer.
Any rejection leaves the last-known-good Room snapshot active.

## What is deferred

Pairing, a public API/domain, remote schedule delivery, remote app updates,
Google Play and production KMS custody are a different deployment mode. They
are not prerequisites for this offline pilot. Revisit them only when remote
operation or wider distribution is actually requested.

# ADR 0017: Android artifacts have a traceable build identity

- Status: Accepted
- Date: 2026-08-31

## Context

An APK named only `pilot-local` cannot tell an operator which application
changes it contains. Android also decides whether an installed package may be
upgraded from `versionCode` and its APK-signing certificate, while the prayer
schedule has a separate immutable snapshot identity and Ed25519 signature.
Conflating those identities would make either application upgrades or schedule
provenance unsafe.

The offline pilot needs a reproducible handover artifact without requiring a
GitHub Release, Google Play or a remote application updater.

## Decision

`apps/tv-android/version.properties` is the single checked-in source for the
base semantic version, Android `versionCode` and pilot/remote prerelease
sequences. Gradle and repository packaging commands validate and consume that
file. The variants are visibly distinct:

- debug: `<base>-dev` with the debug application ID and signing key;
- remotely managed release: `<base>-remote.<sequence>`;
- signed offline pilot: `<base>-pilot.<sequence>`.

Every APK embeds its exact 40-character Git commit, build variant and
clean/dirty state in both `BuildConfig` and manifest metadata. Diagnostics
shows the application version/code and a compact variant/commit/state identity.
Lexical or chronological ordering of `versionName` never controls Android
upgrades: every APK delivered for the retained application ID must have a
strictly increasing integer `versionCode`.

The signed-pilot packaging command fails closed unless:

- an external signing configuration is provided;
- the Git working tree is clean;
- embedded package, version, variant, commit and clean state match expectations;
- the APK certificate and four authenticated pilot assets pass their existing
  checks.

It emits a versioned handover directory outside the repository containing the
APK, SHA-256 file and canonical JSON manifest. The output root may be overridden
explicitly for approved handover media. The manifest binds application ID,
version/code, variant, exact commit, APK hash, certificate hash and bundled
signed-snapshot identity/hash. An APK can therefore be mapped to a source
checkpoint without committing the binary to Git.

Application identity and prayer-data identity remain independent. Advancing an
APK version must not rewrite, approve or re-sign a schedule. A schedule update
continues to require its existing provenance, approval and publication flow.

## Consequences

- the T041 application changes advance the pilot from
  `0.4.0-pilot-local` (3) to `0.5.0-pilot.1` (4);
- the T043 Android operator/runtime changes advance the next pilot artifact to
  `0.5.1-pilot.1` (5) without changing its signed prayer snapshot;
- the T044 next-prayer watermark runtime change advances the pilot artifact to
  `0.5.2-pilot.1` (6), still without rewriting its signed prayer snapshot;
- dirty debug/release builds remain useful locally and identify themselves as
  dirty, but cannot become signed pilot handover bundles;
- a documentation-only commit after an APK checkpoint does not change that
  already built artifact: its embedded commit and manifest continue to name
  the exact clean source checkpoint;
- changing only prayer rows still requires a new signed snapshot and a new
  delivered APK code; changing only application code leaves existing signed
  snapshot bytes untouched;
- Git tags, GitHub Releases, Play distribution and remote update policy remain
  separate future decisions.

## Rejected alternatives

- leaving all APKs at one `versionName`/`versionCode` and identifying them by
  filename;
- including only a short or manually supplied commit with no consistency check;
- allowing a dirty signed-pilot artifact;
- placing the Git commit in signed prayer-snapshot bytes;
- writing APK handover bundles into the repository or committing private
  signing material to Git.

# T041 Android TV setup runtime evidence

## Scope and classification

`CONFIRMED_RUNTIME` — these captures were reproduced on a controlled Android
TV emulator on 2026-08-30. They prove the rendered setup states and the
recorded D-pad/IME interactions on that emulator only. They do not prove
physical-TV, production-server or production-deployment behavior.

The renderer is `DeviceSetupEvidenceActivity`, which exists only in the Android
`debug` source set and composes the production `DeviceScheduleSetupScreen` with
explicitly synthetic cities, authorities, sources and active-schedule text.
Nothing in these fixtures represents a real religious organization or a new
regional source. The release variant must not contain this activity.

Environment:

- emulator: `sdk_google_atv64_x86_64`;
- Android 16 / API 36;
- display: 1920×1080, density 320;
- package: `ru.namaztime.tv.debug`;
- version: code `3`, name `0.4.0-pilot-local`;
- debug APK SHA-256 for this capture run:
  `e579c0325789e0c4e9370fac9f9206e86fa4a0d7eac5d51d9a45ce61fbcdd629`.

The APK is a local build artifact and is intentionally not stored in Git.

## Reproduction

The controlled run built and installed the debug variant, then launched the
debug-only scenario activity with `adb`:

```bash
./gradlew --no-daemon --no-build-cache --dependency-verification=strict \
  :apps:tv-android:assembleDebug
adb -s emulator-5554 install -r \
  apps/tv-android/build/outputs/apk/debug/tv-android-debug.apk
adb -s emulator-5554 shell am start \
  -n ru.namaztime.tv.debug/ru.namaztime.tv.presentation.DeviceSetupEvidenceActivity \
  --es scenario duplicates
```

The other scenario values are `search`, `one-choice`, `multiple-choices`,
`unavailable` and `pending`. `KEYCODE_DPAD_CENTER`, `KEYCODE_DPAD_DOWN` and
`KEYCODE_BACK` were injected as TV-remote events; `uiautomator dump` recorded
the focused node where noted below.

## Observations

- [City search](city-search.png) shows the production field with a Cyrillic
  query and the system TV IME. No custom keyboard was added.
- [Duplicate results](duplicates.png) retain two canonical `Киров` candidates
  with different federal subjects, settlement types and IANA timezones. No row
  is activated by rendering it.
- [One choice](one-choice.png) identifies its synthetic authority, scope,
  source, effective range and policy/source identity. The gold surface means
  D-pad focus; it is not a preferred authority or an automatic selection.
- [Multiple choices](multiple-choices.png) renders the complete five-item
  synthetic set in neutral order. [Scrolled choices](multiple-choices-scrolled.png)
  records focus on the third item after two `Down` presses and shows the next
  items retained below it. The automated acceptance test separately traverses
  all eight synthetic choices.
- [Unavailable](unavailable.png) says that no approved schedule is available;
  it offers no generic calculation or neighboring-region fallback.
- [Pending review](pending.png) names the explicit synthetic choice and states
  that the previous signed schedule remains active until review, approval and
  publication finish.
- `Center` on the focused search field opened the system IME. The first `Back`
  hid the IME without finishing the activity; the UI hierarchy still reported
  the `Киров` `android.widget.EditText` as `focused="true"`. The resulting
  [focus-restored frame](ime-back-focus.png) is recorded separately.

The scenario activity does not exercise live HTTP. Device bearer isolation,
strict response decoding, payload bounds, cancellation, server authorization,
append-only `pending_review` persistence and the unchanged Room snapshot are
covered by the repository's client, Go/PostgreSQL and Android integration
tests.

## Artifact hashes

| File | SHA-256 |
|---|---|
| `city-search.png` | `6b9b7bc57793a68182e661c24239111d619676c1d200c4b9e1312358427387a9` |
| `duplicates.png` | `5acfb0cba8b9b9c527c14e4eeb8ceb8c4a2e19e7b47384765e270a26dd87ef98` |
| `ime-back-focus.png` | `dd49e6eeced832a9cebdf33c6d352e50e7b20f3f21ebd906af6afd05d12c3559` |
| `one-choice.png` | `61ca35c2614a5fc8049314c53da9986cdd2b78d8871a525cd96a1d07f449cbd7` |
| `multiple-choices.png` | `e10a339614900cc4f19d77fa95d9fa49688231a7ff7482db89206e66fb802f61` |
| `multiple-choices-scrolled.png` | `1a058a04ec38ed7bc6ad52617e6d0f3deb2f8ff43e11a471e46378159a00007b` |
| `unavailable.png` | `7d8e53c57100180ed5248b9be80cbd13dcbae9c18f2b40244a82e1fd7d7b50b7` |
| `pending.png` | `336c1ecd8db21c15770205890ce07fb39ac7ffef52c7f6d85d0021b7654992fd` |

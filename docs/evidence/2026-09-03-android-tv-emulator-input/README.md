# Android TV emulator directional-input incident

Date: 2026-09-03

## Scope and evidence boundary

This investigation covers incorrectly routed directional input in the running
`Television_1080p` emulator after the operator signed in to Google Play. It
does not change NamazTime source code, application data, the signed prayer
schedule, or the emulator's persisted user data.

The product-owner-supplied recording
`android_tv_emulator_trouble.mp4` has SHA-256
`ff9dc977c96e5f2d30094221b863b51a080f9f2ca9786907768125463c47ed4e`.
The raw recording remains outside Git because it can contain operator or
third-party account details. Only this sanitized finding is retained.

`CONFIRMED_RUNTIME`: the supplied recording reproduces unexpected directional
behavior from both Android Emulator Extended Controls and the host keyboard.

`UNKNOWN`: the recording establishes that the symptom appeared after Google
Play sign-in, but it does not prove that sign-in caused the emulator input
failure.

## Controlled environment

`CONFIRMED_RUNTIME`: the affected instance was `emulator-5554`, model
`sdk_google_atv64_x86_64`, Android 16 / API 36, at 1920x1080 and 320 dpi. Its
build fingerprint was
`google/sdk_google_atv64_x86_64/emu64xa:16/BT2A.260319.001/15058170:user/dev-keys`.
The Windows host used Android Emulator 37.1.11.0, build 15917651.

`CONFIRMED_STATIC`: the AVD configuration specifies `hw.dPad=yes`,
`hw.keyboard=yes`, `PlayStore.enabled=false`, and the API 36 `android-tv/x86_64`
system image. A separate `google_apis_playstore/x86_64` image is installed but
is not the image selected by this AVD.

`CONFIRMED_RUNTIME`: Android reported no enabled accessibility service, touch
exploration, input filter, pointer capture, or custom key remapping. The
`qwerty2` input device exposed both keyboard and D-pad sources through
`/dev/input/event2`.

## Isolation results

`CONFIRMED_RUNTIME`: direct guest-side key injection worked before the recovery
action. On the TV launcher, `adb shell input keyevent 22`, `20`, and `19` moved
focus right, down, and up to the expected bounds. In NamazTime, injected Center
opened Settings and injected Down moved focus to the next settings item while
`ru.namaztime.tv.MainActivity` retained window focus.

This rules out the NamazTime Compose focus graph, the TV launcher focus graph,
and Android's guest-side `DPAD_*` dispatch as the common cause of the recorded
host-input symptom.

The `dumpsys input` gesture records with flag value `1` were cancelled gesture
candidates, not evidence of a stuck Back key. Android's public AOSP source
defines flag `1` as `FLAG_CANCELLED`.

## Recovery and verification

The emulator process was stopped and started with Quick Boot snapshot loading
disabled:

```text
C:\Users\Andy\AppData\Local\Android\Sdk\emulator\emulator.exe \
  -netdelay none -netspeed full -avd Television_1080p -no-snapshot-load
```

No wipe, uninstall, sign-out, or application-data deletion was performed.

`CONFIRMED_RUNTIME`: after this full cold start, invoking the four live
Extended Controls D-pad segments produced the following raw guest events in
the requested order, each with a Down/Up pair:

```text
KEY_UP
KEY_RIGHT
KEY_DOWN
KEY_LEFT
```

`CONFIRMED_RUNTIME`: after closing Extended Controls and foregrounding the
emulator window, a native Windows `VK_RIGHT` event on the host-keyboard path
produced `KEY_RIGHT DOWN` / `KEY_RIGHT UP` on `/dev/input/event2` and moved
launcher focus to the next application card. The emulator was left running
with its account, installed applications, and data retained.

## Finding and residual uncertainty

`INFERENCE`: the failure was in transient Android Emulator host-input or Quick
Boot state, rather than in NamazTime or Android guest-side focus handling. The
recovery combined a complete emulator-process restart with
`-no-snapshot-load`, so this evidence cannot distinguish stale process state
from a bad saved snapshot.

`UNKNOWN`: the exact internal emulator defect and the causal role, if any, of
Google Play sign-in remain unresolved. Google's public tracker has a closely
matching assigned API 36 / Windows 11 / `Television_1080p` issue in which
physical keyboard and mouse input are not accepted. That report does not prove
that both incidents have the same root cause.

`CONFIRMED_PUBLIC`: Google documents AVD cold boot controls and emulator
troubleshooting, and its emulator release history includes a prior fix for a
D-pad-buttons problem. Relevant public sources:

- [matching Android Emulator issue](https://issuetracker.google.com/issues/500069743);
- [manage AVDs and cold boot](https://developer.android.com/studio/run/managing-avds);
- [Android Emulator troubleshooting](https://developer.android.com/studio/run/emulator-troubleshooting);
- [Android Emulator release notes](https://developer.android.com/studio/releases/emulator);
- [AOSP `KeyGestureEvent.FLAG_CANCELLED`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/input/KeyGestureEvent.java).

## If the symptom returns

`PROPOSAL`: first use Device Manager's **Cold Boot Now**, or fully stop the AVD
and start it with the command above. For diagnostic automation, bypass the host
UI with `adb shell input keyevent 19|20|21|22|23` and compare focus movement.

`PROPOSAL`: if cold starts repeatedly become necessary, preserve this AVD and
create a separate AVD from the installed official Google Play API 36 x86_64
image for comparison. Do not wipe the current AVD until its account and local
state have been backed up or deliberately discarded.

## Commands and results

- `ffprobe` and `ffmpeg` inspected the 19.033-second recording and generated
  temporary contact sheets outside the repository: passed.
- `adb shell getprop`, `wm size`, `wm density`, `dumpsys input`,
  `dumpsys accessibility`, and package inspection captured the controlled
  environment and eliminated guest configuration remapping: passed.
- `adb shell input keyevent ...` plus `uiautomator dump` checked launcher and
  NamazTime focus transitions: passed.
- `adb shell getevent -lt /dev/input/event2` captured live Extended Controls
  direction events and the Windows host-keyboard route after recovery: all four
  panel directions and host Right passed.
- `make docs-check`: passed.
- `GOCACHE=/tmp/namaz-time-go-cache GRADLE_USER_HOME=/tmp/namaz-time-gradle
  make lint test`: passed, including Go format/vet/staticcheck/tests, Python
  tests, Android lint/unit tests, Room schema stability, and Android build
  identity. Earlier sandboxed attempts were invalidated by read-only Gradle
  home and denied loopback-socket access; neither was a project test failure.
- `git diff --check`: passed.

No production file, Room/PostgreSQL schema, contract, permission, source
provenance, or signed snapshot changed. There is therefore no application
migration or rollback action.

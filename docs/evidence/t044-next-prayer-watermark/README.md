# T044 next-prayer watermark runtime evidence

Date: 2026-08-31

## Scope and evidence boundary

The product-owner-supplied `new_reference.png` is the authorized visual
contract under ADR 0014. Its recorded SHA-256 remains
`0df8e3b5ac25e7f65c975d354e19b3e652b20da33e8d9bea10602fbf5b6e6c27`;
the raw reference is excluded from Git. T044 changes only the decorative
`NextPrayerWatermark` behind the next-event foreground.

`CONFIRMED_RUNTIME`: every screenshot in this directory was captured on the
controlled Google Android TV emulator `emulator-5554`, Android 16 / API 36,
1920×1080 at 320 dpi. This is the repository's normalized 960×540 dp
composition. Files `00`–`06` are 620×440 physical-pixel crops from complete
1920×1080 captures so the next-event card can be compared directly. File `07`
is the complete final signed-pilot screen.

`UNKNOWN`: these captures do not establish physical-TV overscan, panel
response, viewing-distance subtle-decoration visibility or hall readability.

## Root cause and normalized geometry

`CONFIRMED_STATIC`: before T044 the Canvas occupied `0.55 × 0.90` of the
card. Its arch used local `left=0.13`, `right=0.46`, `apexX=0.27`,
`apexY=0.17` and `shoulderY=0.44`. Converted to complete-card coordinates,
that was approximately:

| Landmark | Before | T044 final |
|---|---:|---:|
| left edge | 7.15% | 4.0% |
| right edge | 25.30% | 27.0% |
| arch width | 18.15% | 23.0% |
| apex X | 14.85% | 15.5% |
| apex Y | 25.30% | 20.5% |
| shoulder Y | 49.60% | 40.0% |
| bottom Y | 100% | 100% |

The old coordinate nesting made the silhouette narrow and delayed its
shoulders until about half-card height. The final Canvas uses the complete card
as its normalized coordinate space. Symmetric cubic Bézier segments form a
pointed upper arch, rounded shoulders and nearly vertical bases. The arch uses
only existing semantic champagne tokens with a low-alpha main stroke and soft
glow.

## Screenshot iterations

1. `01-arch-only.png`: full-card coordinates and a 24-percent arch were
   introduced with no lantern, isolating silhouette quality.
2. `02-refined-arch.png`: width became 23 percent, apex/shoulders were
   rebalanced and stroke/glow opacity was reduced. The right shoulder still
   approached the divider too closely.
3. `03-filled-lantern.png`: the final arch moved left to `4%..27%`; the old
   wireframe lantern was replaced with an original filled 8.5-percent-wide
   object containing a chain, dark metal canopy/body, three amber panes, soft
   glow and a lower finial.
4. `04`–`06`: final pane warmth was increased without changing geometry, then
   the three acceptance scenarios were recaptured.

The filled lantern was retained after screenshot review: it reads as a small
warm suspended object, remains wholly inside the arch, and does not touch the
countdown. Removing it remains preferable to reverting to the old weak
wireframe, but no fallback removal was necessary in this implementation.

## Acceptance cases

- `CONFIRMED_RUNTIME` — `04-acceptance-asr-golden.png`: short `Аср` on
  Golden Dusk; the arch is broad, the apex and shoulders are distinct, and the
  divider/countdown remain clear.
- `CONFIRMED_RUNTIME` — `05-acceptance-fajr-tomorrow-golden.png`: long
  `Фаджр · завтра` remains complete and high-contrast. The watermark stays
  behind the text and no foreground anchor was moved.
- `CONFIRMED_RUNTIME` — `06-acceptance-asr-blue-hour.png`: Blue Hour provides a
  materially cooler/darker built-in background; the silhouette remains visible
  without becoming a foreground accent.

The debug-only deterministic renderer supplies synthetic prayer/QR content and
is excluded from release/pilot source sets. Compose regressions separately
prove that the watermark Canvas equals the card bounds and that the long title
remains in bounds at 720p, 1080p-density and 4K-density profiles. A pure
geometry regression pins the semantic normalized bounds rather than fragile
Bézier control points.

## Signed pilot installation

`CONFIRMED_RUNTIME`: the clean code checkpoint
`5ebf02359328927fa36c9cb84845429f713ae937` produced and installed:

- application ID: `ru.namaztime.tv`;
- version: `0.5.2-pilot.1`;
- versionCode: `6`;
- APK SHA-256:
  `0b27df4f451b7a39fc35afcdbd61427c07a42840b9a6bce497b397a53cf68e09`;
- certificate SHA-256:
  `da463b2e623024c49c833a1f23c289fd64753e83d3d1e5eea46e973838472be9`.

`adb install -r` upgraded the retained code-5 package without uninstalling it.
`firstInstallTime=2026-08-29 10:32:25` remained unchanged; the installed
package reports `lastUpdateTime=2026-08-31 19:00:33`. File
`07-signed-pilot-main.png` records the actual retained Ulyanovsk pilot with the
long next-prayer title and the final watermark.

`CONFIRMED_STATIC`: the release manifest continues to bind snapshot
`ulyanovsk-second-cathedral-2026-pilot-local-v2` at raw SHA-256
`78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b`.
T044 does not modify Room, source/approval state, schedule rows or snapshot
signature bytes.

## Verification

`CONFIRMED_RUNTIME`: the signed APK passed `make verify-android-pilot` with the
expected clean commit, application ID, version, certificate and APK hashes.
The following repository gates also passed on 2026-08-31:

- `make docs-check`;
- `make test`;
- `make lint`;
- `make test-postgres`, including backup/restore;
- `make test-android-all`, including strict dependency verification and
  debug/release identity checks;
- `go test -race ./...`;
- `make security-go`;
- `make secret-scan`.

No Room or PostgreSQL schema changed, so no migration or rollback path was
introduced by T044.

## Screenshot hashes

| File | SHA-256 |
|---|---|
| `00-baseline-current.png` | `e88edb812b43c6acc11cca3e5a7caed93d521e11eb2a43993dd8191879022d76` |
| `01-arch-only.png` | `8dbc263546e89fb7a4e92ba463b8b7dad1cc02c62916a8909d229acfb422eabe` |
| `02-refined-arch.png` | `839df5b81dc425d18052a2a72087adcc219fd92f72b2da1f002d44e5be2182df` |
| `03-filled-lantern.png` | `2ecfe2ad0f1b6f4fd385f501bb83c47031b0872fb1d8d0f4a94ca0d9186b81dd` |
| `04-acceptance-asr-golden.png` | `8aeea071a105158180e88ad90c6a72afd8e9840f19f97051f6a2bc6b8484fbab` |
| `05-acceptance-fajr-tomorrow-golden.png` | `a77470f212b1fc6e2a4636a238abe2543f18587cde87ca10caadf371208a0e94` |
| `06-acceptance-asr-blue-hour.png` | `0efb953d596a15b088cc51c07e908a57d5adffadeb1e54bd25080f7a2e515f45` |
| `07-signed-pilot-main.png` | `5e49b9bed261f3ab35dab5cdb0aea5b500216b8777d3c2fdec54ee225f205f8a` |

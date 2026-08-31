# T043 image-selection capability and operator contract

Date: 2026-08-31

## Evidence

- `CONFIRMED_PUBLIC`: the owner-supplied `choose_image.png` records the system
  dead end reached on a TV without an `ACTION_OPEN_DOCUMENT` provider. Its hash
  and non-redistribution treatment are recorded in `ROOT_CAUSE.md`.
- `CONFIRMED_STATIC`: NamazTime now resolves the exact document intent before
  launch, checks system Photo Picker availability, and otherwise selects the
  permission-gated MediaStore route.
- `CONFIRMED_STATIC`: the fallback queries `MediaStore.Images` in deterministic
  pages of 32 (hard transport ceiling 100), groups by bucket, and exposes an
  explicit “load more” action without top-N loss.
- `CONFIRMED_STATIC`: manifest/source tests pin legacy read to API 32, pin
  image-only read on API 33+, and reject write/all-files permissions.
- `CONFIRMED_STATIC`: coordinator, pager, importer and Compose tests cover API
  28/32/33/35 routing, provider absence, Photo Picker, permission grant/denial/
  revocation, cancellation, both image slots, focus restoration, JPEG/PNG/WebP,
  all stable failure results and app-private persistence failure.
- `UNKNOWN`: the exact picker/provider set, permission presentation and D-pad
  behavior on a physical pilot television remain pending its acceptance run.

## Operator behavior

1. Open Appearance or Donation and activate the custom-image action.
2. If Android supplies a document or photo picker, use that system surface.
3. If NamazTime opens its image browser, grant image-read access when prompted;
   no prompt appears when a system picker is usable.
4. Select a JPEG, PNG or WebP. NamazTime validates and copies it to app-private
   storage; a success or bounded localized reason is shown on return.
5. Back/cancel leaves the existing image unchanged and restores focus to the
   invoking action without showing an error.

No selected URI, filesystem path, permission result or raw exception is shown
on the public prayer display or persisted as prayer/source provenance.

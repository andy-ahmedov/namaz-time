# ADR 0018: TV image selection is capability-gated

- Status: Accepted
- Date: 2026-08-31
- Amends: ADR 0012 and the device-local image portion of ADR 0013

## Context

The original device-local image flow always launched `ACTION_OPEN_DOCUMENT`.
Some Android TV images have no document-provider activity, so an explicit
operator action ended in a system “no application can perform this action”
message. Adding unrestricted filesystem access would make that dead end a
privacy and security regression rather than solve it.

Appearance and Donation use the same bounded importer. Any selected content is
untrusted until its MIME type, byte length, decoded type, dimensions and pixel
count pass validation and a normalized copy is atomically stored under the
app-private directory. The display never depends on the external content URI.

## Decision

Every explicit image-selection request re-evaluates platform capabilities and
uses this order:

1. launch `ACTION_OPEN_DOCUMENT` only when its exact openable image intent is
   currently resolvable; allow only JPEG, PNG and WebP;
2. otherwise use the Android system Photo Picker when the platform reports it
   available, requesting images only and no media-library permission;
3. otherwise open NamazTime's bounded D-pad MediaStore browser. It exposes
   image rows grouped by MediaStore bucket, loads explicit 32-item pages and
   never scans arbitrary filesystem paths.

The MediaStore fallback requests permission only after that explicit operator
action and only when the current grant check requires it:

- API 28–32: `READ_EXTERNAL_STORAGE`, capped with `maxSdkVersion=32`;
- API 33+: `READ_MEDIA_IMAGES`;
- never `WRITE_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`.

Permission denial, revocation, a failed query and every importer result map to
bounded localized application feedback. Cancellation has no error message.
Appearance and Donation restore focus to the action that opened the picker.
Failures do not change the selected style or active app-private image.

The system picker intents are app-created requests, not redirected nested
intents. T043 adds no exported component, provider, pending intent, arbitrary
intent forwarding, startup media query or background filesystem scan.

## Consequences

Positive:

- a TV without a document provider still has a usable image-only path;
- the privacy-preserving system pickers remain preferred and require no broad
  media grant;
- fallback permission scope and timing are explicit and regression-tested;
- imported assets retain the existing bounded, atomic, offline semantics.

Costs and limits:

- the last-resort MediaStore browser is deliberately not a general file
  manager;
- OEM picker presence, permission wording and physical-TV focus/readability
  remain device acceptance evidence rather than static facts;
- revocation can make a later fallback request ask again, by design.

## Rejected alternatives

- launch an unresolvable picker and rely on the system error;
- request media permission at startup or when a system picker is usable;
- add write or all-files access;
- retain external URIs as display dependencies;
- crawl arbitrary storage paths or display non-image files.

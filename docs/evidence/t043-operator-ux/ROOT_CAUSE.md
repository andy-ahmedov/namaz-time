# T043 owner-reported defects: pre-implementation root cause

Date: 2026-08-31

The four raw owner reference screenshots remain untracked at repository root.
They are product acceptance inputs under ADR 0014, not Android assets. Their
observed defect evidence is `CONFIRMED_PUBLIC`:

| Reference | SHA-256 | Observation |
|---|---|---|
| `text_cropping.png` | `ab661c8ac90cbea835105ac13144db072442417525003785dcd02f3c28ee7858` | main QR motivation ends with an ellipsis |
| `ikamat.png` | `fa4babf8e2ffbebf44e6c083c9847451f591e25a14acd6b8c18eedc82d12a46a` | Isha is outside the visible editor and “По расписанию” wraps vertically |
| `ikamat_referense.png` | `a15a5ea1d022fe3a2b9426cda0c64e473a51cddc01d988822338a724cbf2f94b` | density reference only; its visual style is not copied |
| `choose_image.png` | `f81d9564265020ddda902baaac14b8bc1ea585628442f3484cf912951d90b7ce` | Appearance action reaches Android’s “no application can perform this action” dead end |

Repository inspection identifies these direct causes:

1. `QrCampaignPanel` gives the compact subtitle three lines and explicitly
   uses `TextOverflow.Ellipsis`, while `QrSettingsEditor` accepts up to 500
   characters and persistence validates only campaign/QR generation. Preview
   and public rendering therefore share the truncation, not a fit contract.
2. `IqamahOffsetRow` spends 116 dp at compact size on a wrapping status plus
   two 52×40 dp buttons and 16 dp gaps. Five rows, two heading lines, a separate
   Save button and a separate Return button compete in one fixed-height panel.
   Non-Dhuhr decrement to zero stores integer `0`; only Dhuhr maps its approved
   base back to `null`, and no explicit reset focus target exists.
3. `NamazTvApp` registers only `ActivityResultContracts.OpenDocument` and
   launches it without checking `PackageManager` resolution. Both import
   callbacks ignore every non-`Imported` result, so the system dead end and
   importer failures have no bounded application feedback or explicit focus
   restoration.
4. Donation collection link is a first-class field in
   `OperatorDonationConfiguration`, `hasAnyTransferDetail`, validation,
   DataStore reads/writes and legacy parsing; Settings gives it a focus node and
   `DonationDetailsRows` always reserves a fifth 24 dp row. Visual-only removal
   would therefore leave active product and persistence semantics unchanged.

These causes are confined to device-local presentation/operator preferences.
No prayer row, authority, approval, registry binding, signed snapshot or Room
schema is involved.

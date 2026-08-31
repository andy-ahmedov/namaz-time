# T043 donation collection-link removal

Date: 2026-08-31

## Current contract

`CONFIRMED_STATIC`: the active donation configuration contains one HTTPS QR
target and four optional transfer-detail fields:

1. recipient;
2. bank;
3. card number;
4. SBP/phone.

The separate historical collection-link field is absent from the Kotlin model,
validation, Settings state/focus graph, RU/EN resources and public display.
The existing donation QR URL remains the QR payload and is not rendered as a
fifth transfer row.

The fixed 120-dp details region now renders exactly four 30-dp rows, using the
released vertical space without changing the surrounding rail/card/QR anchors.

## Upgrade behavior

`CONFIRMED_STATIC`: `operator_donation_collection_url` is a tombstoned key. It
is never read into the active model, never counts as a transfer detail, and is
removed during the next normal donation-configuration save. A legacy labelled
`Ссылка на сбор`, `Collection link` or `Fundraiser link` line is recognized and
ignored so it cannot be reinterpreted as unlabelled recipient text. Other
structured legacy fields still migrate deterministically.

If the removed link was the only detail, donation mode fails closed to the
schedule and unrelated operator preferences remain readable. No Room or prayer
snapshot migration is involved.

`CONFIRMED_RUNTIME`: controlled API 36 screenshots show Donation Settings
without the removed field and the standalone display with exactly recipient,
bank, card number and SBP/phone rows. See `RUNTIME_VALIDATION.md` screenshots
`07` and `08`.

# T043 QR subtitle fit contract

Date: 2026-08-31

`PROPOSAL`: accepted operator QR subtitle text must fit the narrowest supported
compact panel before it can be persisted. The shared contract is:

- reference text width: 140 dp;
- maximum subtitle height: 78 dp;
- typography candidates: 12/15 sp, 11/14 sp, then 10/13 sp;
- maximum six rendered lines;
- maximum 256 Unicode code points and no control characters other than newline;
- the first candidate that fits is used, so 10 sp is a lower readability bound;
- no ellipsis or clipping is allowed for an accepted value.

The policy combines Android glyph measurement with a conservative
density-independent lower bound because some TV/vendor font stacks do not
produce complete metrics until a view is attached. `QrCampaignPanel` then uses
the same fit result in Settings preview and the main display and checks the
actual Compose `TextLayoutResult` for visual overflow. The QR matrix size and
error-correction behavior are unchanged.

`CONFIRMED_STATIC`: both preview and public display call the same
`QrCampaignPanel`; the Settings Save action uses the same fit policy as
DataStore validation. Existing persisted configurations whose subtitle no
longer fits retain their valid URL/title while only the unsafe subtitle is
projected as empty. Other operator preferences are left intact.

`CONFIRMED_RUNTIME` is reserved for the controlled emulator audit at the final
T043 checkpoint. Physical-TV readability and overscan remain `UNKNOWN` until
tested on that hardware.

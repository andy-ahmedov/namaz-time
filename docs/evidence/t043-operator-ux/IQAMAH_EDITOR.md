# T043 compact Iqamah editor and reset semantics

Date: 2026-08-31

`PROPOSAL`: each editable prayer uses the same compact TV row:

`Prayer | Use schedule | − | value | +`

The reset is an ordinary 36 dp-high D-pad target. In RU/EN the schedule label
is forced to one line and actual Compose layout overflow is exposed to tests.
The value column shows a local `+N min` or Dhuhr `HH:mm`; an em dash denotes
that the signed schedule is active. The approved Dhuhr label includes its
effective time (for the pilot, `По расписанию · 13:15`).

`CONFIRMED_STATIC`: reset calls `withEditorValue(prayerId, null)`, so it clears
only that prayer's Preferences DataStore projection. Fajr/Asr/Maghrib/Isha
therefore return to their signed `adhan + offset` policy and Dhuhr/Jumu'ah
returns to its signed approved fixed time. Increment/decrement remain bounded
one-minute operations; Sunrise remains absent.

Robolectric API 35 checks prove all five rows, Isha, Save and Return remain
inside the panel at the 720p, 960×540/xhdpi and 4K-density profiles. The same
tests traverse reset, decrement and increment in every row using only D-pad
events, persist Fajr/Dhuhr resets as `null`, and verify RU/EN schedule labels
have no visual overflow.

`CONFIRMED_RUNTIME`: the controlled Android 16 / API 36, 1920×1080 emulator
shows all five rows plus both actions; a D-pad change to Fajr `+1 мин` and its
explicit return to `По расписанию` are captured in screenshots `02`–`04` and
described in `RUNTIME_VALIDATION.md`. Physical-TV behavior remains `UNKNOWN`.

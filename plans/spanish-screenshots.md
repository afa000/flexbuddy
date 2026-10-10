# Plan: read Flex screenshots taken with the app in Spanish

Planned against `origin/main` at 5de206b. **This plan doesn't depend on
the translation plans.** It only changes the screenshot parser, so it can
ship before them. If it does, `plan/spanish` doesn't need its "works best
in English" note under Import.

## What the Spanish screenshot gives us

You sent a screenshot of a scheduled block on the Flex app's
**Programación** screen, with the app in Spanish. I ran it through
Tesseract 5 locally with **only the English model** (`-l eng --psm 4`),
the same model the server uses. The text it read:

```
= amazonFLex o
13 MARTES OCTUBRE
Inicio Pagar
16:45 $121.50
Terminar en
21:15
Ubicacion y otra informacion
North Haven CT (VEA7/BDL3) - Sub
Same-Day
409 Washington Avenue
ee
= EE
Actualizaciones Programaci6n
```

- **The English model reads every field we need.** It only drops or
  mangles accents ("Ubicacion", "Programaci6n"), and none of the fields
  depend on them. Adding the Spanish model (`-l eng+spa`) only restored
  the accents. **No new OCR data is needed.**
- **Today's `ShiftScreenshotParser`** gets this from that text (run
  against the compiled class on 5de206b):

  | Field | Result |
  | --- | --- |
  | station | `VEA7` |
  | pay | `121.50` |
  | **date** | **missing**: `13 MARTES OCTUBRE` matches neither the numeric `M/D` nor the English `Weekday, Mon D` pattern |
  | **start, end** | **missing**: the times are on separate lines under "Inicio" and "Terminar en", while the parser only knows the English range `15:15 - 19:15` |
  | tips | defaulted to 0 (correct for a scheduled block) |

- **Different screen.** The English screenshots the parser was built
  for come from Flex's **Schedule Details** screen
  (`parse_extractsScheduleDetailsFields`: "Sunday, 9/6",
  "15:15 - 19:15 (4 hr)"). This one is the **Programación** card, so
  the parser should handle both layouts in both languages.
- **A date-order risk:** if Spanish Schedule Details shows a numeric
  date the way Spanish usually writes it, day first ("6/9" for 6
  September), today's `NUMERIC_DATE_PATTERN` (month/day) would read it
  as **June 9**, with no error. This plan treats numeric dates as day
  first when the text is clearly Spanish (open question 1 asks for a
  screenshot to confirm).

## 1. Goal and out-of-scope

**Goal:**
- **Date:** read Spanish dates in the forms Flex uses or is likely to
  use:
  - `13 MARTES OCTUBRE`;
  - `martes, 13 de octubre`;
  - `13 de oct.` and `mar., 13 oct.`;
  - all of these in any case, with or without accents, as English OCR
    returns them.
- **Times:** read start and end times shown under labels, in Spanish
  ("Inicio" / "Terminar en", "Fin", "Termina") and English ("Start" /
  "End"), on the label's own line or the next one. 12-hour times with
  `a. m.`, `p. m.`, `am` or `pm` are accepted, as well as 24-hour times.
- **Tips:** read a `Propinas $12.50` line the same way `Tips $12.50` is
  read today.
- **Numeric dates:** read `D/M` instead of `M/D` when the screenshot is
  Spanish.
- **No regressions:** every existing English parser test passes
  unchanged.

**Out of scope:**
- Adding `spa.traineddata`, which isn't needed (see above).
- Translating FlexBuddy's own screens, which is `plan/spanish`.
- Other languages.
- Layouts we have no sample of. The review form still lets drivers fix
  any field.

## 2. Data model and migration

None.

## 3. Backend files

### `service/ShiftScreenshotParser.java`

**A Spanish-text check:**
`static boolean looksSpanish(List<OcrLine> lines)` returns true when any
line, after accent stripping (`Normalizer.normalize(text, NFD)` with the
`\p{M}` marks removed), contains a whole word from:
- the Spanish months;
- the Spanish weekdays;
- `inicio`, `terminar`, `propinas`, `programacion`, `ubicacion`,
  `actualizaciones`;
- `programaci6n` (how English OCR reads "Programación").

**Spanish named dates:**

```java
private static final Pattern SPANISH_DATE_PATTERN = Pattern.compile(
        "\\b(3[01]|[12]?[0-9])\\s+(?:de\\s+)?(?:[\\p{L}0-9]{4,10}\\.?,?\\s+)?(?:de\\s+)?"
        + "(ene(?:ro)?|feb(?:rero)?|mar(?:zo)?|abr(?:il)?|may(?:o)?|jun(?:io)?|jul(?:io)?|ago(?:sto)?"
        + "|sep(?:t(?:iembre)?)?|set(?:iembre)?|oct(?:ubre)?|nov(?:iembre)?|dic(?:iembre)?)\\.?\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
private static final Pattern SPANISH_WEEKDAY_FIRST = Pattern.compile(
        "\\b[\\p{L}0-9]{4,10}\\.?,?\\s+(3[01]|[12]?[0-9])\\s+(?:de\\s+)?(…same month group…)\\.?\\b", …);
```

- **The optional `[\p{L}0-9]{4,10}` word** is the weekday ("MARTES"),
  allowed between the day number and the month. It's kept generic
  because English OCR turns "miércoles" and "sábado" into variants
  ("MIERCOLES", "S4BADO").
- **Why the `\b` after each month alternative matters:** without it,
  "MARTES" would match `mar` and be read as **March**. `13 MARTES OCTUBRE`
  must give 13 October. `13 MARZO` must give 13 March.
- **The month map:** `SPANISH_MONTHS` maps the first three letters to a
  `Month`. Add `set` for setiembre.
- **The weekday-first form** (`martes, 13 de octubre`) is handled by
  `SPANISH_WEEKDAY_FIRST`.
- **`parseDate`:** try the existing patterns first, then the Spanish ones.
  - When `looksSpanish(lines)` is true, the numeric pattern is read
    **day/month**. That's a new `NUMERIC_DATE_DAY_FIRST` with the
    groups swapped, used instead of the existing one.

**Labelled times:**

```java
private static final String TIME = "([01]?\\d|2[0-3]):([0-5]\\d)\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?";
private static final Pattern TIME_TOKEN = Pattern.compile("\\b" + TIME, Pattern.CASE_INSENSITIVE);
private static final Pattern START_LABEL = Pattern.compile("\\b(inicio|empieza|start|starts)\\b", CASE_INSENSITIVE);
private static final Pattern END_LABEL = Pattern.compile("\\b(terminar en|termina|fin|end|ends)\\b", CASE_INSENSITIVE);
```

- In `parseTimes`, **keep the range pattern first**, unchanged. Only if
  no range is found:
  1. **The start time** is the first `TIME_TOKEN` on the `START_LABEL`
     line after the label. Failing that, it's the first time token on
     the next line, or on the line after that.
  2. **The end time** is found the same way from `END_LABEL`.
  3. **If both labels are missing** but the whole text has exactly two
     time tokens, use the first as start and the second as end, each
     with its own line.
- **`toLocalTime(hour, minute, meridiem)`:**
  - `p` with an hour of 1–11 adds 12;
  - `a` with hour 12 gives 0;
  - 24-hour times ignore the meridiem.
- **Line confidence:** `ParsedField.found(time, line)` uses the line the
  time was on, which keeps per-field confidence correct.

**Tips:** extend `TIPS_PATTERN` to
`^\s*(?:Tips|Propinas)\s*:?\s*\$\s*([0-9]+…)`.

**Pay is unchanged.** On this screen, the first `$` amount is the block
pay ("Pagar $121.50"). `PAY_PATTERN` already takes the first `$` amount,
so a tips line further down doesn't affect it.

### `service/ImportWarningRules.java`

No change. Existing warnings (missing field, end before start and low
confidence) apply as before.

## 4. Frontend files

None. The import review form already shows whatever the parser found,
and lets drivers fix the rest.

## 5. Ripple list

- **English screenshots:** unchanged. The range and English date
  patterns run first, and every existing test stays.
- **The day/month switch** only applies when the text looks Spanish, so
  an English `9/6` is still read as September 6.
- **Year handling:** unchanged. The parser still uses the year passed in
  and the December-in-January rollover rule.
- **No new OCR model,** so no image size or memory change. The import
  memory limits plan is unaffected.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes. There's
  no new account data. `sw.js` is unchanged.
- **Phone layout:** none.

## 6. Tests to add

In `service/ShiftScreenshotParserTest`, using the `parse(String, int)`
helper like the existing tests:

- **`parse_readsSpanishProgramacionCard`:** the exact OCR text above
  (including the noise lines `= amazonFLex o`, `ee` and `= EE`), with
  year 2026, gives:
  - station `VEA7`;
  - date **2026-10-13**;
  - start **16:45** and end **21:15**;
  - pay **121.50** and tips **0**.
- **`parse_readsSpanishDateForms`** (parameterised):

  | Input | Date |
  | --- | --- |
  | `martes, 13 de octubre` | Oct 13 |
  | `13 de oct.` | Oct 13 |
  | `mar., 13 oct.` | Oct 13 |
  | `5 MIERCOLES MARZO` | Mar 5 |
  | `1 S4BADO NOVIEMBRE` (OCR noise in the weekday) | Nov 1 |
  | `13 MARTES OCTUBRE` | Oct 13, **not** March |

- **`parse_readsSpanishNumericDateDayFirst`:** `domingo, 6/9` with
  `Inicio` on another line gives **September 6**. The same `6/9` in the
  existing English fixture still gives **June 9**. Add that English case
  next to the Spanish one, so the switch is pinned both ways.
- **`parse_readsLabelledTwelveHourTimes`:** `Inicio` / `4:45 p. m.` /
  `Terminar en` / `9:15 p. m.` gives 16:45 and 21:15. `12:30 a. m.`
  gives 00:30.
- **`parse_readsEnglishLabelledTimes`:** `Start` / `4:45 PM` / `End` /
  `9:15 PM` gives 16:45 and 21:15.
- **`parse_readsPropinas`:** `Propinas $12.50` gives tips 12.50.
- **`parse_prefersRangeOverLabels`:** text with both `15:15 - 19:15` and
  `Inicio 16:45` uses the range, 15:15 to 19:15. This pins that the
  existing behaviour wins.
- **The existing tests** (`parse_extractsScheduleDetailsFields`,
  `parse_extractsNamedDateAndWarnsWhenStationIsNotShown`,
  `parse_extractsTipsWhenTheyAreShown`, …) pass unchanged.

`ShiftImportServiceTest` needs no change. It mocks OCR lines and goes
through the same parser.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

### Desktop (1280×800), after the deploy

1. Import the Spanish Programación screenshot you sent. The review form
   fills in:
   - Station `VEA7`;
   - Date Oct 13;
   - Start 4:45 PM and End 9:15 PM;
   - Base pay $121.50;
   - Status Scheduled (it's in the future).

   There's no "missing" warning on date or times.
2. Import an English Schedule Details screenshot. It fills in exactly as
   before.

### Phone (375×667, then a real Android phone with Flex in Spanish)

3. With Flex set to Spanish, take a screenshot of a scheduled block and
   share it to FlexBuddy (Android share sheet). Import reads the date,
   times, station and pay.
4. Open a **completed** block in Flex in Spanish and import its
   screenshot. Check the tips line is read. If tips is 0 but the
   screenshot shows tips, note the label Flex uses (open question 2).
5. With the phone's clock in 12-hour mode, check that a "p. m." time
   reads correctly.

## 8. Commit message

```
feat(import): read flex screenshots taken with the app in spanish

Drivers with the Flex app in Spanish got only the station and pay
from a screenshot, because the date reads like 13 MARTES OCTUBRE and
the start and end times sit on their own lines under Inicio and
Terminar en. The English OCR model already reads all of that text,
so no new model is needed.

The parser now reads Spanish dates with or without the weekday and
with full or short month names, without mistaking MARTES for March,
and takes start and end times from their labels in Spanish or
English, in 24 hour or a.m. and p.m. form. Propinas is read as tips,
and when a screenshot is in Spanish a numeric date is read day first.
Existing English screenshots are read exactly as before.
```

## 9. Open questions

1. **Spanish Schedule Details.** Could you send one more screenshot: tap
   into a block in Flex (in Spanish) to open its full details, the
   screen that in English shows "Sunday, 9/6" and "15:15 - 19:15
   (4 hr)"? That confirms whether Spanish shows the date as `6/9` (day
   first), which this plan assumes.
2. **A completed block in Spanish,** showing tips, to confirm the label
   is "Propinas". The plan reads "Propinas" already. If Flex uses another
   word, it's a one-word change.

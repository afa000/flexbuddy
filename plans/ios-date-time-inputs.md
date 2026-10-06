# Plan: keep date and time boxes inside their column on iPhone

Planned against `origin/main` at 6e56508.

## What goes wrong today

On an iPhone, the Date, Start time and End time boxes on the Import
screen's review form are **wider than the other boxes** and run past the
right edge.

The cause is how Safari on iOS draws `<input type="date">` and
`<input type="time">`:
- It renders them as native controls, with their own minimum width taken
  from the formatted value ("Sep 28, 2026", "4:00 AM"), and ignores
  `width: 100%` when that minimum is wider.
- It centres the text inside the box through the
  `::-webkit-date-and-time-value` part.
- `.field` is a grid item with the default `min-width: auto`, so the
  column grows to fit the input instead of the input shrinking to the
  column.

At ≤620px `.preview-form` becomes a single `1fr` column
(`styles.css` around line 1617). `1fr` means `minmax(auto, 1fr)`, so
one wide input widens the whole column, and the page with it.

Chrome and Android don't do this, which is why it only shows on iPhone.
The same inputs appear in five other places, and all get the same fix:
- the edit dialog: date, start, end, started and finished;
- the finish-block sheet: started and finished;
- the expense form date;
- the standing date;
- the From and To filters on Reports and Expenses.

I couldn't reproduce this here: only Chromium is installed, and it
doesn't have the bug. The fix uses the standard WebKit overrides, and
step 1 of the manual checks confirms it on a real iPhone.

## 1. Goal and out-of-scope

**Goal:**
- On iPhone, every date and time box is exactly as wide as the other
  boxes in its column.
- The text is left-aligned like the other inputs.
- The page never scrolls sideways.
- Tapping still opens iOS's native date or time wheel.

**Out of scope:**
- Custom date pickers.
- Changing input types, for example to text with a pattern.
- Layout changes to the review form. Fields stay one per row on phones,
  because you said they look too wide, not too stacked.
- Cancel and choose-again, which are in `plan/import-cancel`.

## 2. Data model and migration

None.

## 3. Backend files

None.

## 4. Frontend files

### `static/css/styles.css`

**1. Let grid fields shrink.** After the `.field` rule (line 1049), add:

```css
.field,
.filter-field { min-width: 0; }
```

**2. Normalise WebKit's date and time controls.** After the
`.field input, .field select` block (ends around line 1097), add:

```css
/* iOS draws date and time inputs as native controls with their own minimum width and centred text; these
   rules make them size and align like every other input while still opening the native picker on tap. */
.field input[type="date"],
.field input[type="time"],
.filter-field input[type="date"],
.filter-field input[type="time"] {
    min-width: 0;
    max-width: 100%;
    display: block;
    -webkit-appearance: none;
    appearance: none;
    text-align: left;
}

.field input[type="date"]::-webkit-date-and-time-value,
.field input[type="time"]::-webkit-date-and-time-value,
.filter-field input[type="date"]::-webkit-date-and-time-value,
.filter-field input[type="time"]::-webkit-date-and-time-value {
    margin: 0;
    text-align: left;
}
```

`appearance: none` removes the iOS pill styling that forced the minimum
width. The existing `.field input` rules still set the height (47px),
padding, border, background and radius, so the boxes look like the
Station box. Chrome ignores `::-webkit-date-and-time-value`; it's
WebKit-only.

**3. Stop the single phone column from growing.** In the ≤620px block,
change `.preview-form { grid-template-columns: 1fr; }` (around line
1617) to `minmax(0, 1fr)`. Also change the desktop rule
`.preview-form { grid-template-columns: 1fr 1fr; }` (line 1045) to
`minmax(0, 1fr) minmax(0, 1fr)`, so Start and End can't push each other
on a narrow desktop window either.

**4. Check the empty state.** iOS Safari can collapse an empty date input
to a shorter height. The fixed `height: 47px` on `.field input` (and
`43px` on `.filter-field input`) already prevents that. Don't remove
those heights.

No HTML or JS changes. The edit dialog, finish sheet, expense form,
standing form and filters already use `.field` or `.filter-field`.

### `static/sw.js`

No change. The stylesheet is already in `VERSIONED_ASSETS`, and the new
build id refreshes the cache.

## 5. Ripple list

- **Every date and time input in the app** changes on iPhone: the review
  form, edit dialog, finish sheet, expense date, standing date, and the
  Reports and Expenses filters. Chrome and Android look the same as
  before. I rendered a date input with these rules in Chromium at 375px:
  it keeps its calendar icon and full width, identical to one without
  them. The value already sits on the left there. Step 7 checks this.
- **Dark and light themes:** the inputs keep the `.field input` colours.
  On iOS with `appearance: none`, check that the value text uses
  `var(--text)` in both themes (step 6).
- **`min-width: 0` on `.field`** also affects text and number fields. It
  only lets them shrink to their column, which is already their width, so
  nothing else visibly changes. It matches the existing rule that grid
  children get `min-width: 0`.
- **Tap targets:** heights stay at 47px and 43px, both over 44px or within
  the existing filter design. There are no new controls.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes. There's
  no new account data, and the JS is unchanged.

## 6. Tests to add

This is CSS only, and the bug is WebKit-specific, so there's no unit test
that can see it. To guard the rule from being removed by accident, add to
`StaticAssetsTest`:

- **`dateAndTimeInputsAreNormalisedForIos`:** read `static/css/styles.css`
  from the classpath and assert that it contains:
  - `input[type="date"]::-webkit-date-and-time-value`;
  - `-webkit-appearance: none` within the same rule block as
    `.field input[type="date"]`. Find the block with a regex from
    `.field input[type="date"]` to the next `}`;
  - `.preview-form` with `minmax(0, 1fr)`.

  The test name and message explain why: "iOS draws date and time inputs
  wider than their column without these rules."

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

### iPhone, the real device (Safari, and the Home Screen app if installed)

1. **The reported bug:** Import → choose a screenshot → on the review
   form, Date, Start time and End time are exactly as wide as Station and
   Base pay. Their right edges line up, and the page doesn't move
   sideways when you drag left.
2. The values read left-aligned, like `Sep 28, 2026` and `4:00 AM`, not
   centred.
3. Tap Date: the native date wheel opens. Tap Start time: the time wheel
   opens. Change a value: it shows in the box, and it's saved when you
   add the shift.
4. Clear the Date (Reset in the wheel). The empty box stays the same
   height as the others.
5. Open a block from Recent blocks → Edit. Date, Start, End and, under
   Actual times, Started and Finished, are all the same width as the
   other fields. Do the same in the Finish-block sheet, the Expenses form
   date, and the Reports filter From/To.
6. Switch to light mode and back. The date and time text is readable in
   both, with the same colour as the Station text.

### Desktop and Android

7. **Chrome desktop at 1280×800:**
   - On the Import review, Start and End sit side by side at equal width.
   - The date input still shows its calendar icon, and clicking opens
     Chrome's date picker.
8. **Chrome dev tools at 375×667 and 430×932:** on the review form and
   the edit dialog,
   `document.documentElement.scrollWidth === innerWidth`.
9. **Android, in the Play app:** the review form's date and time boxes
   look as before and open Android's pickers.

## 8. Commit message

```
fix(ui): keep date and time boxes inside their column on iphone

Safari on iOS draws date and time inputs as native controls with a
minimum width taken from the value and centres the text, and a grid
field grows to fit its content by default. On the import review form
the Date, Start time and End time boxes came out wider than the
other boxes and ran past the edge of the screen.

Date and time inputs inside form fields and filters now drop the
native appearance, may shrink to their column and align their value
to the left, and the review form's columns can no longer grow past
the screen. Tapping a box still opens the native date or time wheel.
The same rules fix the edit dialog, the finish sheet, the expense
form and the report filters, which use the same inputs.
```

## 9. Open questions

1. **Side-by-side times on phones.** Start and End time could share a row
   at phone width (two columns of about 170px each), which shortens the
   form. That's a separate design change, and I left it out because you
   described the boxes as too wide rather than the form as too long. Say
   if you want it.
2. **Your iOS version.** If step 1 still shows wide boxes after this
   ships, send a screenshot and the iOS version (Settings → General →
   About). Older iOS releases have a different date-input bug that needs
   one more rule.

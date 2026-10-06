# Plan: keep screenshot import within Render's memory

Planned against `origin/main` at 6e56508.

## What goes wrong today

I reproduced this locally on 6e56508. The jar ran with `-Xmx128m`, which
is what the JVM gives itself by default on Render's 512 MB starter
instance (25% of RAM). I uploaded three images to
`POST /shifts/import-preview`:

| Image | Result |
| --- | --- |
| 1170×2532 PNG, a normal iPhone screenshot (102 KB) | decoded and preprocessed fine |
| 4032×3024 JPEG, a phone camera photo (619 KB) | **`OutOfMemoryError: Java heap space`** in `ScreenshotPreprocessor.grayscale` |
| 6000×5000 JPEG (1.1 MB) | **`OutOfMemoryError`** while `ShiftImportService` decoded it |

The new error alerts reported both. Both files are far under the 5 MB
upload limit and under the 30 MP `flexbuddy.import.max-pixels` limit, so
the server accepts them and then runs out of memory.

The memory goes on these steps:
1. `ShiftImportService.readWithinLimits` decodes the **full** image with
   `reader.read(0)`. A 12 MP JPEG is about 36 MB as 3-byte RGB, and a
   30 MP one about 90 MB.
2. `ScreenshotPreprocessor.upscale` makes a full **copy** even when the
   scale is 1.0. That's a second 36–120 MB image.
3. `grayscale`, `invertIfDark`, `threshold` and `pad` each allocate
   another image.
4. Tess4J copies the prepared image off-heap to native memory, and
   Tesseract's own memory grows with the pixel count. This part counts
   towards Render's 512 MB container limit, not the Java heap. A large
   image that gets past the heap can still push the instance over 512 MB,
   and Render restarts it ("memory limit exceeded").
5. Two imports at the same moment double all of the above.

There is one more path to a huge image, through narrow inputs. `upscale`
scales a short side under 1200 px up by as much as 3×. A long scrolling
screenshot such as 585×5000 becomes 1200×10256, which is 12.3 MP, after
preprocessing.

**The fix works.** Decoding the same three files with subsampling,
capped at 5 MP, then running the real `ScreenshotPreprocessor.prepare`,
all three completed inside a **48 MB** heap:
- 12 MP decoded at 2016×1512;
- 30 MP decoded at 2000×1667;
- the normal screenshot was unchanged at 1170×2532.

## 1. Goal and out-of-scope

**Goal:**
- Any image the server accepts is processed at no more than 5 MP, from
  decoding through Tesseract.
- Only one OCR runs at a time.
- A wrong or oversized photo gives the driver a normal "couldn't read
  this" result, or a clear message, instead of an out-of-memory error or
  a restarted server.
- Normal phone screenshots keep their current resolution, so read
  quality doesn't change. iPhone screenshots are about 3 MP, and
  high-resolution Android ones (1440×3200) are 4.6 MP.

**Out of scope:**
- Shrinking images in the browser before upload. Possible later, to
  save mobile data, but the server must protect itself anyway.
- Changing JVM memory flags (see open question 1).
- Changing the 5 MB upload limit or the 30 MP rejection limit.
- Cancelling an import from the browser. That's `plan/import-cancel`.
- Accuracy changes to the parser.

## 2. Data model and migration

None.

## 3. Backend files

### `service/ShiftImportService.java`

- Add `static final long DECODE_MAX_PIXELS = 5_000_000;`. It's a
  constant, not a property, so the constructor and every test that builds
  the service stay unchanged.
- In `readWithinLimits`, keep the `MAX_SIDE` and `maxPixels` checks
  exactly as they are, with the same messages. Then replace
  `reader.read(0)` with a subsampled read:

  ```java
  ImageReadParam param = reader.getDefaultReadParam();
  int factor = subsampleFactor(width, height);
  if (factor > 1) param.setSourceSubsampling(factor, factor, 0, 0);
  BufferedImage image = reader.read(0, param);
  ```

- Add a package-private static helper:

  ```java
  /** The smallest whole-number step that brings the image to at most DECODE_MAX_PIXELS. */
  static int subsampleFactor(int width, int height) {
      long pixels = (long) width * height;
      if (pixels <= DECODE_MAX_PIXELS) return 1;
      return (int) Math.ceil(Math.sqrt((double) pixels / DECODE_MAX_PIXELS));
  }
  ```

  Subsampling happens inside the JPEG and PNG decoders, so the full-size
  image is never held in memory.
- Leave the rest of `createPreview` as it is.

### `service/ScreenshotPreprocessor.java`

- Add `static final long MAX_OUTPUT_PIXELS = 5_000_000;`.
- In `upscale`:
  - When the scale is 1.0, **return `source` itself** instead of
    `copy(...)`. `grayscale` draws from it into a new grey image anyway,
    so the copy only cost memory.
  - When upscaling, cap the scale so the result stays within the limit:
    `scale = Math.min(scale, Math.sqrt((double) MAX_OUTPUT_PIXELS / ((long) source.getWidth() * source.getHeight())))`.
    If that makes the scale ≤ 1.0, return `source`.

  The 585×5000 strip then becomes about 765×6535 (5 MP) instead of
  12.3 MP.
- `copy(...)` loses its last caller. Delete it.
- Every other step (`grayscale`, `invertIfDark`, `threshold` and `pad`)
  stays the same.

### `service/TesseractScreenshotTextExtractor.java`

- Add `private final Semaphore ocrPermit = new Semaphore(1, true);` and a
  constructor parameter
  `@Value("${flexbuddy.import.ocr-wait:20s}") Duration ocrWait`.
- Wrap the body of `extract`, including `preprocessor.prepare`, so the
  preprocessing images are covered too:

  ```java
  if (!ocrPermit.tryAcquire(ocrWait.toMillis(), TimeUnit.MILLISECONDS)) {
      throw new ScreenshotBusyException("Another screenshot is being read. Try again in a moment.");
  }
  try { ...existing body... } finally { ocrPermit.release(); }
  ```

  Handle `InterruptedException` by restoring the interrupt flag and
  throwing the same `ScreenshotBusyException`.

### `exception/ScreenshotBusyException.java` (new)

A `RuntimeException` with a message constructor.

### `exception/GlobalExceptionHandler.java`

Add a handler for `ScreenshotBusyException` that returns
`503 SERVICE_UNAVAILABLE` with the message as the body. Don't log it at
ERROR: a busy server is expected, so it must not send an alert email.

### `src/main/resources/application.properties`

After `flexbuddy.import.max-pixels`, add:

```
# Screenshot text extraction runs one at a time; a second upload waits this long before being told to try again.
flexbuddy.import.ocr-wait=20s
```

### `src/test/resources/application-test.properties`

No change. The default of 20s applies, but no test holds the permit
except the one below, which sets its own wait.

## 4. Frontend files

None. `processScreenshot` in `app.js` already shows
`await response.text()` for a non-OK response, so the 503 message
appears in `#uploadError` as is. The friendlier cancel button comes in
`plan/import-cancel`.

## 5. Ripple list

- **Read quality** for normal screenshots is unchanged: anything at or
  under 5 MP is decoded at full size, and the preprocessing output for a
  1170×2532 screenshot is unchanged at 1200×2597 before padding. Only
  images over 5 MP are decoded at reduced resolution.
- **Text in large photos is smaller after subsampling.** A camera photo
  of a screen will often read badly. That's acceptable, because the
  current behaviour is a crash. The existing low-confidence warnings
  still apply.
- **Concurrency:** the starter instance has one shared CPU, so OCR was
  effectively running one at a time anyway. A second driver now waits up
  to 20 s instead of both slowing down. An import abandoned by the
  browser still finishes on the server before releasing the permit.
- **Error alerts:** OOMs in the import path stop. A `ScreenshotBusyException`
  is not logged at ERROR, so it sends no alert email.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes. There's
  no new account data. `sw.js` is unchanged.

## 6. Tests to add

**`ShiftImportServiceTest`** (Mockito, as now; capture the image passed
to `textExtractor.extract` with an `ArgumentCaptor<BufferedImage>`):
- `photoOverFiveMegapixelsIsDecodedSmaller`:
  - Generate a 4032×3024 JPEG in memory with `ImageIO.write` into a
    `MockMultipartFile` of type `image/jpeg`.
  - The captured image has `width * height <= 5_000_000`, and its aspect
    ratio is within 1% of 4:3.
- `normalScreenshotKeepsFullResolution`: a 1170×2532 PNG reaches the
  extractor as exactly 1170×2532.
- `subsampleFactor` returns:
  - 1 for 1440×3200;
  - 2 for 4032×3024;
  - 3 for 6000×5000;
  - 2 for 2000×10000 (20 MP).
- The existing size-limit tests keep their messages: over 12,000 px a
  side, and over 30 MP.

**`ScreenshotPreprocessorTest`:**
- `prepare_neverOutputsMoreThanFiveMegapixelsBeforePadding`: a 585×5000
  input gives `upscale(...)` at most 5,000,000 pixels.
- `upscale_returnsTheSameImageWhenNoScalingIsNeeded`: for a 1300×2600
  input, `upscale(source)` is the same instance (`isSameAs`).
- The existing `prepare_upscalesSmallImagesAndAddsWhitePadding` passes
  unchanged.

**`TesseractScreenshotTextExtractorTest`** (new, no native library
needed):
- Construct it with `Duration.ofMillis(100)`.
- Use reflection or a package-private accessor to acquire `ocrPermit`
  first, then call `extract`. It throws `ScreenshotBusyException`
  without touching Tesseract.
- Release the permit afterwards.

If constructing it needs the tessdata resource, that's already on the
test classpath under `src/main/resources/tessdata`.

**`ShiftControllerTest`:**
- When the import service throws `ScreenshotBusyException`,
  `POST /shifts/import-preview` returns 503 with the message as the body.

Run `mvn test` and `node --test "src/test/js/*.test.js"`. CI runs both,
plus the Postgres test.

## 7. Manual checks

### Desktop

1. After the deploy, open Import and upload a normal Flex screenshot. The
   preview fills in as before, with the same read quality percentage as
   an import of the same screenshot before the change.
2. Upload a camera photo, for example a photo of your monitor at 12 MP.
   - Before: an error alert email `OutOfMemoryError`, and possibly a
     Render "memory limit exceeded" event and restart.
   - After: either a preview with low-confidence warnings, or "Text could
     not be extracted…". No alert email arrives, and Render Events shows
     no restart.
3. In Render → the web service → **Metrics**, watch memory during step 2.
   It should stay well under 512 MB. Note the peak in the commit's
   follow-up notes.
4. Open Import in two browsers and upload a photo in both within a
   second. One gets its preview. The other either waits and then gets
   its preview, or, after 20 s, shows "Another screenshot is being read.
   Try again in a moment."

### Phone (375px and 430px, then a real iPhone and Android phone)

5. Import a normal screenshot on each phone. The preview appears as
   before, with no layout change.
6. On the iPhone, pick a large camera-roll photo by mistake. The app
   shows a normal "could not be read" result within about 10 s instead
   of spinning indefinitely.

## 8. Commit message

```
fix(import): read screenshots at a bounded size so imports fit in memory

A camera photo picked by mistake ran the server out of memory. The
whole image was decoded at full size, copied again before
preprocessing and then handed to Tesseract, so a 12 megapixel photo
needed more heap than Render's default gives the JVM, and larger ones
could push the instance over its memory limit and restart it.

Images over five megapixels are now decoded with subsampling, so the
full-size picture is never held in memory, and preprocessing no longer
copies an image it does not scale and never scales one past five
megapixels. Normal phone screenshots are under that size and are read
exactly as before. Text extraction now runs one at a time; an upload
that waits more than twenty seconds is told to try again with a 503
instead of competing for memory.
```

## 9. Open questions

1. **JVM memory.** I left the JVM at its default heap of about 128 MB.
   With images capped, an import fits in under 50 MB of heap. Raising the
   heap, for example `-XX:MaxRAMPercentage=50` in `docker-entrypoint.sh`,
   would give more room, but it leaves less of the 512 MB for Tesseract's
   native memory. Check Render's memory graph after this ships before
   changing it. I can plan that one-line change separately if the graph
   shows the heap is the limit.
2. **The 5 MP cap.** It's above every real phone screenshot I know of.
   If testers import screenshots from tablets (2048×2732 is 5.6 MP), they
   would be decoded at half size. Say if any tester uses a tablet, and
   I'll raise the cap to 6 MP.

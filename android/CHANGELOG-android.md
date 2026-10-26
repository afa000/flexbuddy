# Android release history

## Version code 3 — everything since version 2, for the second closed test

- Minimum Android version raised from 5.0 (`minSdkVersion` 21) to 7.0 (24). Play Console refused the
  first version 3 bundle on October 26, 2026: "Play automatic protection requires a minimum SDK
  version of 24 or higher". Phones on Android 5 or 6 can no longer install updates.
- `appVersionCode` and `appVersionName` raised to 3 for the second closed test
  ([closed-test-round-2.md](closed-test-round-2.md)). It brings the web app's changes since September 26,
  2026 to testers as an update Play can see.
- Generated with `bubblewrap update --skipVersionUpgrade`. Afterwards the hand-drawn icons, splash
  images and `store_icon.png` were restored, and so were the Java and XML files whose only change was
  whitespace. `shortcuts.xml` is reset to an empty template by both `update` and `build`; the build
  fills it from `twa-manifest.json`, so the committed copy is kept as it was.
  `res/raw/web_app_manifest.json` now matches the live site's manifest, which adds the share target and
  the Evaluate shortcut to that copy; the launcher shortcuts are still the three in `twa-manifest.json`.
- Built unsigned with Bubblewrap on October 25, 2026. The APK reads `versionCode='3'
  versionName='3'` and has the Import, Expenses and Schedule launcher shortcuts, as version 2 did.
- Signed interactively with the upload key on October 25, 2026; `apksigner` and `jarsigner` verified both
  files with the upload key (`A3:EC:82:…:FE:37`). Emulator check: update from the Play Store, read-only, still to do.
- Release notes (English and Spanish): as written in `closed-test-round-2.md`
- Uploaded to Closed testing - Alpha on October 25, 2026 (Play accepted it once the minimum was Android 7, after
  confirming the drop of 1,921 device models on Android 5–6) and approved by Google on October 25, 2026. Day 0 of the
  second closed test.

## Version code 2 — redrawn app icon and share-to-import

- Share target: FlexBuddy appears in Android's share sheet for PNG and JPEG images. The shared
  screenshot is posted to `/share-import`, where the service worker keeps it on the device and
  opens the import screen with it. Generated with `bubblewrap update --skipVersionUpgrade`; the
  hand-drawn icons were restored afterwards because the update regenerates them from the site

- Launcher, adaptive (maskable), splash, notification, and long-press shortcut icons use the
  F-and-outline mark redrawn from the icon sheet; the web icons already shipped with the site on
  September 14, 2026
- No other shell changes. The white flash between the splash screen and the first page no
  longer occurs: on September 26, 2026, four cold launches on the emulator sampled every
  100–220 ms went straight from the dark splash to the page
- Built unsigned with Bubblewrap on September 26, 2026, signed interactively with the upload key,
  and tested on the emulator: sharing an image from Google Photos opened the import screen with
  it read
- Uploaded to Closed testing - Alpha on September 26, 2026 as `2 (share-to-import and new icon)`
  (version 1 not carried over) and sent to Google for review. Approved: on October 22, 2026 Play Console showed it
  available to testers in 178 of 178 countries, full rollout
- Production access applied for on October 22, 2026, after Play Console showed all three closed-test
  requirements met; answers in `production-access.md`

## Version code 1 — initial internal-test build

- Package: `com.angel.flexbuddy`
- Origin: `https://flexbuddy.onrender.com`
- Wrapper: Bubblewrap 1.25.0
- Status: Android shell generated; signed APK and AAB verified with the upload key
- Uploaded to Internal testing on September 14, 2026 and published to internal testers as
  `1 (initial internal test)`
- Added to the Closed testing - Alpha track on September 14, 2026 as `1 (closed test)`: all 177
  countries / regions, the `testers` email list, and feedback to `flexbuddysupport@gmail.com`.
  Sent for review on September 14, 2026 and approved; the track is active
- Play App Signing enabled with a Google-managed key
- Upload key SHA-256: `A3:EC:82:A5:7E:FD:B3:07:93:B0:C6:88:E2:32:B1:E9:4B:49:53:69:70:46:54:EC:24:29:AB:6A:84:BF:FE:37`
- Play app signing key SHA-256: `2F:43:E9:E2:B6:D2:6C:06:BF:80:03:16:E1:0B:34:DB:68:36:14:5B:7E:DA:C0:ED:E8:96:24:08:46:51:6C:6C`

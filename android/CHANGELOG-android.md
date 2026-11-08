# Android release history

## Version code 5 — the driver experience changes, day 12 of the second closed test

- No shell changes. `appVersionCode`, `appVersionName` and `appVersion` raised to 5 in `twa-manifest.json`
  (`app/build.gradle` and `manifest-checksum.txt` follow). It brings the web changes live on Render since November 6
  and 7, 2026 to testers as an update Play can see: an explicit worked/scheduled choice on import (FB-011), moving a
  block back to scheduled (FB-012), missing-miles net labels (FB-013), what an expense does to net (FB-014), forfeit
  wording (FB-015), report coverage lines (FB-016), deleted-block expenses (FB-017, FB-018), phone layout (FB-019,
  FB-020), several screenshots at once (FB-021), sharing several screenshots and sharing before sign-in (FB-022) and
  Review this block (FB-023).
- Generated with `bubblewrap update --skipVersionUpgrade` on November 7, 2026. Afterwards the icons, splash images,
  `store_icon.png`, `shortcuts.xml`, and the Java and XML files whose only change was whitespace were restored, as
  for version 4.
- Built unsigned with Bubblewrap on November 7, 2026. The APK reads `versionCode='5' versionName='5'`,
  `minSdkVersion 24`, `targetSdkVersion 36`, package `com.angel.flexbuddy`, with the committed launcher shortcuts.
- Release notes (English and Spanish): as written in `closed-test-round-2.md`.
- Bundle signed interactively by the owner with the upload key on November 7, 2026; `jarsigner -verify` passed and the
  certificate is the upload key (`A3:EC:82:…:FE:37`). Its contents match the unsigned build file for file. The APK
  was not re-signed (its step didn't finish), so `app-release-signed.apk` on disk is still version 4's; Play only
  takes the bundle.
- Uploaded to Closed testing - Alpha on November 8, 2026 as `5 (driver experience)`, version 5 (5), API 24+, target
  SDK 36; version 4 not carried over; supported devices unchanged (no device dropped). Release notes entered for
  en-US, es-419 and es-US; rollout 100%. Submitted for review the same day (Publishing overview: "Changes in review",
  quick checks running; managed publishing off). The upload and submission were done in the browser by Claude Code
  with the owner's approval; the owner chose the file.
- To do: record Google's approval date, and update the emulator's Play app to 5 (5) to check it opens full screen.

## Version code 4 — the October 30 web fixes, day 5 of the second closed test

- No shell changes. `appVersionCode`, `appVersionName` and `appVersion` raised to 4 in `twa-manifest.json`
  (`app/build.gradle` and `manifest-checksum.txt` follow). It brings three web changes, live on Render since
  October 30, 2026, to testers as an update Play can see: the duplicate-import warning (FB-008, PR #15), the IRS
  mileage rate for each block's date (FB-010, PR #16) and the missing-miles notes on Reports, Home and Expenses
  (FB-009, PR #17).
- Generated with `bubblewrap update --skipVersionUpgrade` on October 30, 2026. Afterwards the hand-drawn icons, splash
  images, `store_icon.png` and `shortcuts.xml` were restored, and so were `Application.java`, `LauncherActivity.java`,
  `ic_launcher.xml` and `strings.xml` (only whitespace changed) and `DelegationService.java` (only a trailing
  newline).
- Built unsigned with Bubblewrap on October 30, 2026. The APK reads `versionCode='4' versionName='4'`,
  `minSdkVersion 24`, and has the three launcher shortcuts (Import, Expenses, Schedule), as version 3 did.
- Release notes (English and Spanish): as written in `closed-test-round-2.md`, entered for en-US, es-419 and es-US.
- Signed interactively with the upload key on October 30, 2026; `apksigner` verified the APK and `jarsigner` the bundle,
  both with the upload key (`A3:EC:82:…:FE:37`).
- Uploaded to Closed testing - Alpha on October 30, 2026 as `4 (October 30 fixes)`, version 4 (4), API 24+, target
  SDK 36; version 3 not carried over, and no supported devices dropped. Sent for review the same day and approved by
  Google on October 30, 2026 (Publishing overview: nothing in review, last published October 30, 2026).
- Emulator check, October 31, 2026 (read-only, Play Reviewer account): Play had updated the app to version 4 (4)
  overnight. A cold launch opened Home full screen with no URL bar, so the site verification still holds; the page's
  "FlexBuddy updated" notice reloaded into the current web build. Nothing was added or changed.

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

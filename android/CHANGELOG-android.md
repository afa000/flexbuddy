# Android release history

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
  (version 1 not carried over) and sent to Google for review

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

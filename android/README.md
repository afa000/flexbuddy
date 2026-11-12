# FlexBuddy Android shell

This directory contains the Trusted Web Activity (TWA) wrapper for the production
FlexBuddy PWA at `https://flexbuddy.onrender.com`. It has no app logic of its own, so most
changes are web changes and reach the app without a new Android build.

The app is in closed testing on Google Play (track Closed testing - Alpha). Related files:

- [`CHANGELOG-android.md`](CHANGELOG-android.md): every build and Play upload
- [`closed-test-round-2.md`](closed-test-round-2.md): the current closed test, day by day
- [`closed-test-feedback.md`](closed-test-feedback.md): tester feedback, by tester number only
- [`production-access.md`](production-access.md): production access applications and Google's replies
- [`store-listing.md`](store-listing.md), [`play-app-content.md`](play-app-content.md) and
  `store-screenshots/`: the Play listing and declarations

## Fixed application identity

- Package name: `com.angel.flexbuddy`
- Launcher name: `FlexBuddy`
- Production origin: `https://flexbuddy.onrender.com`
- Bubblewrap CLI: `1.25.0`
- Upload-key alias: `flexbuddy-upload`
- Minimum Android version: 7.0 (`minSdkVersion` 24), which Play automatic protection requires
  since version 3

The package name and signing key must remain stable after the first Play upload.

## Prerequisites

- Node.js 14.15 or newer
- JDK 17
- Android SDK configured through Bubblewrap

Validate the environment from the repository root:

```powershell
npx --yes @bubblewrap/cli@1.25.0 doctor
```

## Build an unsigned verification bundle

Run Bubblewrap from this directory so generated files stay under `android/`:

```powershell
cd android
npx --yes @bubblewrap/cli@1.25.0 build --skipSigning
```

This produces `app-release-unsigned-aligned.apk` and
`app/build/outputs/bundle/release/app-release.aab`. Build artifacts are ignored by
Git.

## Build a signed release

The upload keystore is intentionally stored outside this repository. First create the
unsigned artifacts, then sign them with Android and JDK tools in an interactive
PowerShell terminal:

```powershell
cd android
npx --yes @bubblewrap/cli@1.25.0 build --skipSigning

$keystore = Join-Path $env:USERPROFILE ".flexbuddy\signing\flexbuddy-upload.keystore"
$java = (Get-Command java).Source
$jdkBin = Split-Path $java
$apksigner = Join-Path $env:USERPROFILE ".bubblewrap\android_sdk\build-tools\36.1.0\lib\apksigner.jar"

& $java -Xmx1024M -jar $apksigner sign `
  --ks $keystore `
  --ks-key-alias flexbuddy-upload `
  --out .\app-release-signed.apk `
  .\app-release-unsigned-aligned.apk

Copy-Item `
  .\app\build\outputs\bundle\release\app-release.aab `
  .\app-release-signed.aab

& (Join-Path $jdkBin "jarsigner.exe") `
  -keystore $keystore `
  -sigalg SHA256withRSA `
  -digestalg SHA-256 `
  .\app-release-signed.aab `
  flexbuddy-upload
```

Both signing tools prompt for the password securely. Do not add a password argument or
put the password in a script, command history, source file, issue, or chat.

Keep at least two secure backups of the keystore and its password. Losing the upload
key can prevent future app updates.

## Update the wrapper

Change `twa-manifest.json`, raise `appVersionCode` and `appVersionName` (and `appVersion`)
by hand, then apply the configuration and build. `--skipVersionUpgrade` keeps the version you
set:

```powershell
cd android
npx --yes @bubblewrap/cli@1.25.0 update --skipVersionUpgrade
npx --yes @bubblewrap/cli@1.25.0 build --skipSigning
```

`update` regenerates more than the version. Before committing, check `git status` and restore
what it changed without reason:

- the hand-drawn icons and splash images (`app/src/main/res/**/*.png`) and `store_icon.png`,
  which it redraws from the site's icons;
- Java and XML files whose only change is whitespace;
- `app/src/main/res/xml/shortcuts.xml`, which it resets to an empty template; the build fills
  it from `twa-manifest.json`, so the committed copy stays as it is.

Keep the version changes in `twa-manifest.json` and `app/build.gradle`, the new
`manifest-checksum.txt` (otherwise `build` offers to regenerate the project), and any change
you meant to make. Check the built APK before signing, for example:

```powershell
$aapt = Join-Path $env:USERPROFILE ".bubblewrap\android_sdk\build-tools\36.1.0\aapt2.exe"
& $aapt dump badging .\app-release-unsigned-aligned.apk | Select-String "^package:|sdkVersion"
```

Bubblewrap runs `gradlew.bat` from this directory. A shell that sets
`NoDefaultCurrentDirectoryInExePath` (as some automated terminals do) can't find it; run the
build from a normal PowerShell window.

Record every Play upload in `CHANGELOG-android.md`.

Verify both release artifacts before upload:

```powershell
& $java -jar $apksigner verify --verbose --print-certs .\app-release-signed.apk
& (Join-Path $jdkBin "jarsigner.exe") -verify .\app-release-signed.aab
```

## Digital Asset Links

The web origin serves `/.well-known/assetlinks.json` with two SHA-256 fingerprints: this
upload key and the Play app signing key (both are in `twa-manifest.json` and
`CHANGELOG-android.md`). Keep both. Without the Play app signing key, Play-installed copies
open with Chrome's address bar instead of full screen.

## Checking a release on the emulator

The emulator has the Play Store build installed and is signed in to production as the Play
Reviewer account. Use it for read-only checks only: launch, offline start, cached screens and
long-press shortcuts. Never add, edit or delete anything there, and never press Uninstall under
"Installed on all devices" in the Play Store, which removes the app from the owner's own phone.
To check a new version, update it from the Play Store once Play approves it.

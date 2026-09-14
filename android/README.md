# FlexBuddy Android shell

This directory contains the Trusted Web Activity (TWA) wrapper for the production
FlexBuddy PWA at `https://flexbuddy.onrender.com`.

## Fixed application identity

- Package name: `com.angel.flexbuddy`
- Launcher name: `FlexBuddy`
- Production origin: `https://flexbuddy.onrender.com`
- Bubblewrap CLI: `1.25.0`
- Upload-key alias: `flexbuddy-upload`

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

Change `twa-manifest.json`, increase `appVersionCode`, then apply the configuration and
build:

```powershell
cd android
npx --yes @bubblewrap/cli@1.25.0 update
npx --yes @bubblewrap/cli@1.25.0 build --skipSigning
```

Record every Play upload in `CHANGELOG-android.md`.

Verify both release artifacts before upload:

```powershell
& $java -jar $apksigner verify --verbose --print-certs .\app-release-signed.apk
& (Join-Path $jdkBin "jarsigner.exe") -verify .\app-release-signed.aab
```

## Digital Asset Links

The web origin must serve `/.well-known/assetlinks.json` containing the SHA-256
fingerprint of this upload key. After Play App Signing is enabled, add the Play app
signing certificate fingerprint as a second entry before production release.

# Building and publishing a release

Release version: 1.0.0-beta.1, versionCode 2. Keep the package identity `com.example.sportsstream` and the same signing key when publishing updates.

## Signing

The release build uses `SPORTSWAVE_SIGNING_PROPERTIES` to locate a private Java properties file. Keep this file and its keystore outside the repository. Its required fields are:

```properties
storeFile=C:/private/sportswave/sportswave-release.p12
storePassword=YOUR_PRIVATE_PASSWORD
keyAlias=sportswave
keyPassword=YOUR_PRIVATE_PASSWORD
```

Use forward slashes for Windows paths. The initial signing key was generated locally as RSA 3072 / PKCS12 with a long validity. Back up the key and its password securely: future sideloaded APK updates must retain the signing identity. Neither the key nor its password is uploaded to GitHub. Release tasks fail if signing configuration is absent; debug builds need no private release key.

## Build

```powershell
$env:SPORTSWAVE_SIGNING_PROPERTIES = "C:/private/sportswave/release.properties"
./gradlew.bat testDebugUnitTest assembleRelease bundleRelease
```

Unix equivalent:

```sh
export SPORTSWAVE_SIGNING_PROPERTIES=/private/sportswave/release.properties
./gradlew testDebugUnitTest assembleRelease bundleRelease
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk`: signed, minified, resource-shrunk, directly installable APK.
- `app/build/outputs/bundle/release/app-release.aab`: signed Android App Bundle, intended for store tooling rather than direct installation.
- `app/build/outputs/mapping/release/mapping.txt`: retain privately for diagnosing this minified version.

Use SDK build-tools `apksigner verify --verbose --print-certs` and `zipalign -c -P 16 4` on the APK. Use JDK `jarsigner -verify` on the AAB. Android signing certificates may be self-signed; a trusted public certificate authority is not required for APK distribution.

## Publish

Update versionName/versionCode, README, CHANGELOG and the actual test report. Build from the commit being tagged. Package the APK, AAB, SHA256SUMS.txt and the public signing-certificate SHA-256 fingerprint. Commit and tag the source, then create a GitHub release attaching those artifacts. Label builds prerelease while the documented playback limitations remain unresolved.

The workflow in `.github/workflows/android.yml` runs offline tests and assembles a debug APK without release-signing credentials. Publishing this repository/release does not submit an app to Google Play. Store submission needs a separate developer account, store identity/policy work and Play signing setup.

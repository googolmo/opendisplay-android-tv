# Build a signed release

The project reads upload-key values from these environment variables:

- `OPENDISPLAY_UPLOAD_KEYSTORE`
- `OPENDISPLAY_UPLOAD_KEY_ALIAS`
- `OPENDISPLAY_UPLOAD_STORE_PASSWORD`
- `OPENDISPLAY_UPLOAD_KEY_PASSWORD`

## Generate an upload key

From the repository root, run:

```sh
./scripts/generate-play-upload-key.sh
```

The script creates a 4096-bit RSA upload keystore in `~/projects/android/kp/`, with a private `.env` file beside it. It uses a timestamped name and refuses to overwrite existing files. The `.env` and `.jks` files are created with owner-only permissions. Keep both files private and outside version control.

The root `justfile` provides shortcuts for common tasks:

```sh
just keygen
just debug
just install-debug
just release "$HOME/projects/android/kp/opendisplay-upload-YYYYMMDD-HHMMSS.env"
just sign "$HOME/projects/android/kp/opendisplay-upload-YYYYMMDD-HHMMSS.env"
just release-both "$HOME/projects/android/kp/opendisplay-upload-YYYYMMDD-HHMMSS.env"
just signing-report
```

`just release` creates the Play `.aab`; `just sign` creates a signed `.apk` for install/testing; `just release-both` creates both. Pass the `.env` path printed by `just keygen`.

## Build the Play release

Use the `.env` path printed by the key-generation script:

```sh
./scripts/build-play-release.sh bundle "$HOME/projects/android/kp/opendisplay-upload-YYYYMMDD-HHMMSS.env"
```

The Play Store artifact is `app/build/outputs/bundle/release/app-release.aab`. A signed APK for direct install or testing can be built with:

```sh
./scripts/build-play-release.sh apk "$HOME/projects/android/kp/opendisplay-upload-YYYYMMDD-HHMMSS.env"
```

To build both artifacts, use `both` instead of `bundle` or `apk`. The Gradle signing configuration applies to the release variant only. Debug builds do not need the upload key.

Google Play requires Android App Bundles for Android TV apps, so upload the `.aab` to Play Console; the APK output is for local install/testing. For the first Play release, enroll in Play App Signing and use this keystore as the upload key. Increase `versionCode` in `app/build.gradle.kts` for every submitted update. Never commit the `.jks` or `.env` file.

Current store listing copy and graphics are in [PLAY_STORE.md](PLAY_STORE.md). Capture a real Android TV screenshot from the release build before submission; generated marketing art cannot stand in for the required TV UI screenshot.

References: [Android TV distribution requirements](https://developer.android.com/training/tv/publishing/distribute), [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756).
